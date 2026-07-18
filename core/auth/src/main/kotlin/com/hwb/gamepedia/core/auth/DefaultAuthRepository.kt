package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthFailure
import com.hwb.gamepedia.core.model.AuthOutcome
import com.hwb.gamepedia.core.model.AuthSession
import com.hwb.gamepedia.core.model.AuthTokens
import com.hwb.gamepedia.core.model.AuthUser
import com.hwb.gamepedia.core.model.SessionState
import com.hwb.gamepedia.core.network.AuthApi
import com.hwb.gamepedia.core.network.AuthDiagnostics
import com.hwb.gamepedia.core.network.AuthFailureMapper
import com.hwb.gamepedia.core.network.AuthedUserApi
import com.hwb.gamepedia.core.network.SessionTokenGateway
import com.hwb.gamepedia.core.network.dto.AuthSessionDataDto
import com.hwb.gamepedia.core.network.dto.GoogleLoginRequestDto
import com.hwb.gamepedia.core.network.dto.LoginRequestDto
import com.hwb.gamepedia.core.network.dto.LogoutRequestDto
import com.hwb.gamepedia.core.network.dto.RefreshRequestDto
import com.hwb.gamepedia.core.network.dto.SignUpRequestDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import com.hwb.gamepedia.core.storage.TokenStore
import com.hwb.gamepedia.core.storage.UserSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Session repository with a concurrency-safe single-flight refresh coordinator.
 * Kotlin translation of the guarantees in the merged iOS implementation
 * (GamePedia PR #14); see docs/AUTH_ARCHITECTURE.md for the full state model.
 *
 * Locking discipline: [lock] guards {generation, inFlight, flight flags, waiter
 * counts} and every store mutation, so persistence is always atomic with the
 * generation/identity checks. The lock is NEVER held across network I/O or
 * `await`; provider work runs in [scope] and is awaited outside the lock.
 *
 * Flight lifecycle (all transitions under [lock]):
 *  - A flight is created with its creator pre-registered as waiter #1 and the
 *    refresh token captured exactly once.
 *  - Joiners increment the waiter count while the flight is joinable (not
 *    completed, not abandoned).
 *  - A waiter cancellation decrements the count; the LAST waiter's cancellation
 *    marks the flight abandoned and cancels the provider job. A flight whose
 *    response already arrived commits normally even if the last waiter just
 *    cancelled — discarding a completed rotation would strand a revoked refresh
 *    token client-side.
 *  - Supersession (login/signup/google adoption, logout/deletion invalidation)
 *    advances [generation], claims the flight's completion so late provider
 *    results are inert, resolves waiters with [AuthFailure.Superseded], and
 *    cancels the provider job — but the flight keeps occupying the slot until its
 *    job has actually completed ([finalizeFlight]), so a replacement refresh can
 *    never overlap a still-live request that is submitting the rotating token.
 *  - New callers that find a dying flight suspend on [RefreshFlight.slotCleared]
 *    and then re-enter; they never share a dying flight's fate.
 *  - Detachment ([detachLocked]) is idempotent and happens exactly once per
 *    flight, from commit, failure, supersession finalization, or abandonment.
 */
class DefaultAuthRepository(
    private val authApi: AuthApi,
    private val authedUserApiProvider: () -> AuthedUserApi,
    private val failureMapper: AuthFailureMapper,
    private val tokenStore: TokenStore,
    private val userSessionStore: UserSessionStore,
    private val scope: CoroutineScope,
    private val deviceName: String? = null,
    private val hooks: AuthTestHooks = AuthTestHooks.NONE,
) : AuthRepository, SessionTokenGateway {

    private val lock = Any()
    private var generation: Long = 0
    private var inFlight: RefreshFlight? = null

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        // Session restoration: only a fully consistent pair (tokens AND user)
        // restores; a partial record is cleared rather than half-trusted.
        val tokens = tokenStore.tokens()
        val user = userSessionStore.user()
        if (tokens != null && user != null) {
            _sessionState.value = SessionState.Authenticated(user)
        } else if (tokens != null || user != null) {
            tokenStore.clear()
            userSessionStore.clear()
        }
    }

    private class RefreshFlight(
        val generation: Long,
        val refreshToken: String,
    ) {
        /** Completed exactly once with the flight's outcome; never cancelled, so
         * a waiter's CancellationException always means the WAITER was cancelled. */
        val result = CompletableDeferred<AuthOutcome<AuthSession>>()

        /** Completed when the flight has fully released the coordinator slot. */
        val slotCleared = CompletableDeferred<Unit>()

        // All fields below are guarded by the repository lock.
        var job: Job? = null
        var waiters: Int = 1 // creator pre-registered before the flight is visible
        var completionClaimed = false
        var abandoned = false
        var detached = false
        var cancelRequestedBeforeJob = false

        val joinable: Boolean get() = !completionClaimed && !abandoned
    }

    private sealed interface FlightDecision {
        class Created(val flight: RefreshFlight) : FlightDecision
        class Joined(val flight: RefreshFlight) : FlightDecision
        class AwaitSlot(val flight: RefreshFlight) : FlightDecision
        class Immediate(val outcome: AuthOutcome<AuthSession>) : FlightDecision
    }

    // region AuthRepository

    override suspend fun login(email: String, password: String): AuthOutcome<AuthSession> =
        establishSession("login") {
            authApi.login(LoginRequestDto(email = email, password = password, deviceName = deviceName))
        }

    override suspend fun signUp(email: String, password: String, nickname: String): AuthOutcome<AuthSession> =
        establishSession("signup") {
            authApi.signUp(
                SignUpRequestDto(email = email, password = password, nickname = nickname, deviceName = deviceName),
            )
        }

    override suspend fun loginWithGoogle(idToken: String): AuthOutcome<AuthSession> =
        establishSession("google_login") {
            authApi.googleLogin(GoogleLoginRequestDto(idToken = idToken, deviceName = deviceName))
        }

    override suspend fun refresh(): AuthOutcome<AuthSession> {
        while (true) {
            val decision = synchronized(lock) {
                val existing = inFlight
                when {
                    existing == null -> {
                        val refreshToken = tokenStore.tokens()?.refreshToken
                        if (refreshToken == null) {
                            FlightDecision.Immediate(AuthOutcome.Failure(AuthFailure.MissingRefreshToken))
                        } else {
                            val flight = RefreshFlight(generation, refreshToken)
                            inFlight = flight
                            hooks.onWaiterAttached(1)
                            FlightDecision.Created(flight)
                        }
                    }
                    existing.joinable -> {
                        existing.waiters++
                        hooks.onWaiterAttached(existing.waiters)
                        FlightDecision.Joined(existing)
                    }
                    else -> FlightDecision.AwaitSlot(existing)
                }
            }

            when (decision) {
                is FlightDecision.Immediate -> return decision.outcome
                is FlightDecision.Created -> {
                    launchFlight(decision.flight)
                    return awaitFlight(decision.flight)
                }
                is FlightDecision.Joined -> return awaitFlight(decision.flight)
                is FlightDecision.AwaitSlot ->
                    // The dying flight still owns the slot (its request may still
                    // be live). Wait for full release, then re-enter.
                    decision.flight.slotCleared.await()
            }
        }
    }

    override fun logout() {
        val refreshToken = tokenStore.tokens()?.refreshToken
        invalidateSession()
        AuthDiagnostics.operationSucceeded("logout")
        if (refreshToken != null) {
            // Best-effort server-side revocation; failures are irrelevant to the
            // already-cleared local session and carry nothing loggable.
            scope.launch {
                runCatching { authApi.logout(LogoutRequestDto(refreshToken)) }
            }
        }
    }

    override suspend fun deleteAccount(): AuthOutcome<Unit> =
        try {
            authedUserApiProvider().deleteMyAccount()
            invalidateSession()
            AuthDiagnostics.operationSucceeded("delete_account")
            AuthOutcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val failure = failureMapper.map(throwable)
            AuthDiagnostics.operationFailed("delete_account", failure.diagnosticCategory, failure.backendCode)
            AuthOutcome.Failure(failure)
        }

    // endregion

    // region SessionTokenGateway (OkHttp integration)

    override fun currentAccessToken(): String? = tokenStore.tokens()?.accessToken

    override fun awaitValidAccessToken(staleAccessToken: String?): String? {
        // Another caller may have already rotated: reuse without a new refresh.
        tokenStore.tokens()?.accessToken?.let { current ->
            if (current != staleAccessToken) return current
        }
        return try {
            runBlocking {
                when (val outcome = refresh()) {
                    is AuthOutcome.Success -> outcome.value.tokens.accessToken
                    is AuthOutcome.Failure -> null
                }
            }
        } catch (_: InterruptedException) {
            // The OkHttp call hosting this waiter was cancelled; its registration
            // was already released via the waiter-cancellation path.
            Thread.currentThread().interrupt()
            null
        } catch (_: CancellationException) {
            null
        }
    }

    // endregion

    // region Flight mechanics

    private fun launchFlight(flight: RefreshFlight) {
        AuthDiagnostics.refreshFlightStarted(flight.generation)
        hooks.onFlightLaunched()

        val job = scope.launch {
            val outcome = try {
                val envelope = authApi.refresh(
                    RefreshRequestDto(refreshToken = flight.refreshToken, deviceName = deviceName),
                )
                AuthOutcome.Success(envelope.data.toDomain())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                AuthOutcome.Failure(failureMapper.map(throwable))
            }
            completeFlight(flight, outcome)
        }
        job.invokeOnCompletion { finalizeFlight(flight) }

        val cancelNow = synchronized(lock) {
            flight.job = job
            flight.cancelRequestedBeforeJob
        }
        if (cancelNow) {
            job.cancel()
        }
    }

    private suspend fun awaitFlight(flight: RefreshFlight): AuthOutcome<AuthSession> =
        try {
            flight.result.await()
        } catch (cancellation: CancellationException) {
            // result is never cancelled, so this is OUR waiter being cancelled.
            onWaiterCancelled(flight)
            throw cancellation
        }

    private fun onWaiterCancelled(flight: RefreshFlight) {
        val jobToCancel: Job? = synchronized(lock) {
            if (flight.completionClaimed || flight.abandoned) return
            flight.waiters--
            if (flight.waiters > 0) return // shared work survives for the others
            flight.abandoned = true
            flight.job ?: run {
                flight.cancelRequestedBeforeJob = true
                null
            }
        }
        AuthDiagnostics.refreshFlightAbandoned(flight.generation)
        // Cancel outside the lock. The flight keeps the slot until the job's
        // completion handler runs, so no replacement overlaps the live request.
        jobToCancel?.cancel()
    }

    /** Runs inside the provider job when a response (or mapped failure) arrived. */
    private fun completeFlight(flight: RefreshFlight, outcome: AuthOutcome<AuthSession>) {
        val resolved: AuthOutcome<AuthSession> = synchronized(lock) {
            if (flight.completionClaimed) {
                // Superseded/invalidated while the response was in transit: the
                // result was already resolved; the late outcome stays inert.
                return
            }
            if (inFlight !== flight || flight.generation != generation) {
                // Defensive: identity or generation moved without claiming us.
                flight.completionClaimed = true
                detachLocked(flight)
                AuthOutcome.Failure(AuthFailure.Superseded)
            } else {
                flight.completionClaimed = true
                when (outcome) {
                    is AuthOutcome.Success -> persistLocked(outcome.value)
                    is AuthOutcome.Failure ->
                        if (outcome.failure.isAuthRejection) {
                            // Definitive rejection ends the session; transport
                            // failures leave the stored session untouched.
                            generation++
                            clearLocked()
                        }
                }
                detachLocked(flight)
                outcome
            }
        }
        flight.result.complete(resolved)
        AuthDiagnostics.refreshFlightResolved(
            flight.generation,
            when (resolved) {
                is AuthOutcome.Success -> "ok"
                is AuthOutcome.Failure -> resolved.failure.diagnosticCategory
            },
        )
    }

    /** invokeOnCompletion: runs after the provider job finished OR was cancelled. */
    private fun finalizeFlight(flight: RefreshFlight) {
        synchronized(lock) {
            detachLocked(flight)
        }
        // No-ops when the flight already resolved; resolves abandoned flights.
        flight.result.complete(AuthOutcome.Failure(AuthFailure.FlightCancelled))
        flight.slotCleared.complete(Unit)
    }

    private fun detachLocked(flight: RefreshFlight) {
        if (flight.detached) return
        flight.detached = true
        if (inFlight === flight) {
            inFlight = null
        }
    }

    // endregion

    // region Session adoption / invalidation

    private suspend fun establishSession(
        operation: String,
        call: suspend () -> SuccessEnvelopeDto<AuthSessionDataDto>,
    ): AuthOutcome<AuthSession> =
        try {
            val session = call().data.toDomain()
            adoptAuthenticatedSession(session)
            AuthDiagnostics.operationSucceeded(operation)
            AuthOutcome.Success(session)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val failure = failureMapper.map(throwable)
            AuthDiagnostics.operationFailed(operation, failure.diagnosticCategory, failure.backendCode)
            AuthOutcome.Failure(failure)
        }

    /**
     * A fresh login/signup/social session owns the credential store from now on.
     * Any in-flight refresh belongs to the previous session: its completion is
     * claimed (late results inert), its waiters resolve with Superseded, its
     * provider job is cancelled — but it holds the slot until the job ends.
     */
    private fun adoptAuthenticatedSession(session: AuthSession) {
        supersedeAndReplaceSession(session)
    }

    /** Logout / account deletion: same supersession, but the store is cleared. */
    private fun invalidateSession() {
        supersedeAndReplaceSession(null)
    }

    private fun supersedeAndReplaceSession(session: AuthSession?) {
        val superseded: RefreshFlight?
        val jobToCancel: Job?
        synchronized(lock) {
            generation++
            val flight = inFlight
            if (flight != null && !flight.completionClaimed) {
                flight.completionClaimed = true
                superseded = flight
                jobToCancel = flight.job ?: run {
                    flight.cancelRequestedBeforeJob = true
                    null
                }
            } else {
                superseded = null
                jobToCancel = null
            }
            if (session != null) persistLocked(session) else clearLocked()
        }
        superseded?.let {
            AuthDiagnostics.refreshFlightSuperseded(it.generation)
            it.result.complete(AuthOutcome.Failure(AuthFailure.Superseded))
        }
        jobToCancel?.cancel()
    }

    /** Guarded by [lock]: token pair + user + state advance together, atomically. */
    private fun persistLocked(session: AuthSession) {
        tokenStore.save(session.tokens)
        userSessionStore.save(session.user)
        _sessionState.value = SessionState.Authenticated(session.user)
    }

    private fun clearLocked() {
        tokenStore.clear()
        userSessionStore.clear()
        _sessionState.value = SessionState.Unauthenticated
    }

    // endregion
}

/** Deterministic observation points for concurrency tests; no-ops in production. */
class AuthTestHooks(
    val onWaiterAttached: (activeWaiters: Int) -> Unit = {},
    val onFlightLaunched: () -> Unit = {},
) {
    companion object {
        val NONE = AuthTestHooks()
    }
}

private fun AuthSessionDataDto.toDomain(): AuthSession =
    AuthSession(
        user = AuthUser(
            id = user.id,
            email = user.email,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            status = user.status,
            createdAtIso = user.createdAt,
            updatedAtIso = user.updatedAt,
        ),
        tokens = AuthTokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        ),
    )
