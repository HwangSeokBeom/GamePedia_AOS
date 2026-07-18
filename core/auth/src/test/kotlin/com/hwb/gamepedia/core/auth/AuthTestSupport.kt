package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthTokens
import com.hwb.gamepedia.core.network.AuthApi
import com.hwb.gamepedia.core.network.AuthedUserApi
import com.hwb.gamepedia.core.network.GamePediaNetwork
import com.hwb.gamepedia.core.network.dto.AuthSessionDataDto
import com.hwb.gamepedia.core.network.dto.AuthUserDto
import com.hwb.gamepedia.core.network.dto.DeleteAccountResultDto
import com.hwb.gamepedia.core.network.dto.GoogleLoginRequestDto
import com.hwb.gamepedia.core.network.dto.LoginRequestDto
import com.hwb.gamepedia.core.network.dto.LogoutRequestDto
import com.hwb.gamepedia.core.network.dto.LogoutResultDto
import com.hwb.gamepedia.core.network.dto.MeDataDto
import com.hwb.gamepedia.core.network.dto.RefreshRequestDto
import com.hwb.gamepedia.core.network.dto.SignUpRequestDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import com.hwb.gamepedia.core.network.dto.TokenPairDto
import com.hwb.gamepedia.core.storage.InMemoryTokenStore
import com.hwb.gamepedia.core.storage.InMemoryUserSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Canonical response builders mirroring GamePediaCoreServer auth shapes (dev merge 28a113e; unchanged by atomic-rotation fix 7c9f88f). */
object AuthFixtures {

    fun userDto(tag: String): AuthUserDto =
        AuthUserDto(
            id = "user-$tag",
            email = "user-$tag@example.com",
            nickname = "nick-$tag",
            profileImageUrl = null,
            status = "ACTIVE",
            createdAt = "2026-07-18T00:00:00.000Z",
            updatedAt = "2026-07-18T00:00:00.000Z",
        )

    fun sessionData(tag: String): AuthSessionDataDto =
        AuthSessionDataDto(
            user = userDto(tag),
            tokens = TokenPairDto(accessToken = "access-$tag", refreshToken = "refresh-$tag"),
        )

    fun sessionEnvelope(tag: String): SuccessEnvelopeDto<AuthSessionDataDto> =
        SuccessEnvelopeDto(success = true, data = sessionData(tag))

    fun storedTokens(tag: String): AuthTokens =
        AuthTokens(accessToken = "access-$tag", refreshToken = "refresh-$tag")

    fun storedUser(tag: String): com.hwb.gamepedia.core.model.AuthUser =
        com.hwb.gamepedia.core.model.AuthUser(
            id = "user-$tag",
            email = "user-$tag@example.com",
            nickname = "nick-$tag",
            profileImageUrl = null,
            status = "ACTIVE",
            createdAtIso = null,
            updatedAtIso = null,
        )

    fun httpException(status: Int, code: String): HttpException =
        HttpException(
            Response.error<Any>(
                status,
                """{"success":false,"error":{"code":"$code","message":"x"}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
}

/**
 * Deterministic AuthApi fake. refresh() suspends on a per-call gate the test
 * completes explicitly; cancellation of the provider job is recorded per call.
 */
class FakeAuthApi : AuthApi {

    class PendingRefresh(val request: RefreshRequestDto) {
        val gate = CompletableDeferred<SuccessEnvelopeDto<AuthSessionDataDto>>()

        @Volatile
        var cancelled = false
    }

    val refreshCalls = mutableListOf<PendingRefresh>()
    val logoutCalls = mutableListOf<LogoutRequestDto>()

    /** Deterministic arrival signal for real-dispatcher tests (no polling). */
    val refreshArrivals = kotlinx.coroutines.channels.Channel<PendingRefresh>(
        kotlinx.coroutines.channels.Channel.UNLIMITED,
    )

    var loginHandler: suspend (LoginRequestDto) -> SuccessEnvelopeDto<AuthSessionDataDto> =
        { AuthFixtures.sessionEnvelope("login") }
    var signUpHandler: suspend (SignUpRequestDto) -> SuccessEnvelopeDto<AuthSessionDataDto> =
        { AuthFixtures.sessionEnvelope("signup") }
    var googleHandler: suspend (GoogleLoginRequestDto) -> SuccessEnvelopeDto<AuthSessionDataDto> =
        { AuthFixtures.sessionEnvelope("google") }

    override suspend fun signUp(body: SignUpRequestDto): SuccessEnvelopeDto<AuthSessionDataDto> =
        signUpHandler(body)

    override suspend fun login(body: LoginRequestDto): SuccessEnvelopeDto<AuthSessionDataDto> =
        loginHandler(body)

    override suspend fun googleLogin(body: GoogleLoginRequestDto): SuccessEnvelopeDto<AuthSessionDataDto> =
        googleHandler(body)

    override suspend fun refresh(body: RefreshRequestDto): SuccessEnvelopeDto<AuthSessionDataDto> {
        val pending = PendingRefresh(body)
        synchronized(refreshCalls) { refreshCalls += pending }
        refreshArrivals.trySend(pending)
        try {
            return pending.gate.await()
        } catch (cancellation: CancellationException) {
            pending.cancelled = true
            throw cancellation
        }
    }

    override suspend fun logout(body: LogoutRequestDto): SuccessEnvelopeDto<LogoutResultDto> {
        logoutCalls += body
        return SuccessEnvelopeDto(success = true, data = LogoutResultDto(loggedOut = true))
    }
}

class FakeAuthedUserApi : AuthedUserApi {
    var deleteCalls = 0

    override suspend fun me(): SuccessEnvelopeDto<MeDataDto> =
        SuccessEnvelopeDto(success = true, data = MeDataDto(AuthFixtures.userDto("me")))

    override suspend fun deleteMyAccount(): SuccessEnvelopeDto<DeleteAccountResultDto> {
        deleteCalls++
        return SuccessEnvelopeDto(
            success = true,
            data = DeleteAccountResultDto(deleted = true, deletedAt = "2026-07-18T00:00:00.000Z"),
        )
    }
}

/**
 * Repository scope for virtual-time tests. Deliberately NOT `backgroundScope`:
 * TestScope's background scope tears down cancelled children without dispatching
 * their cancellation through catch blocks and stops accepting post-cancel work,
 * which breaks classic cancellation semantics the coordinator relies on. An
 * independent SupervisorJob scope on the same scheduler keeps virtual-time
 * determinism with real cancellation behavior.
 */
fun kotlinx.coroutines.test.TestScope.repositoryScope(): CoroutineScope =
    CoroutineScope(
        kotlinx.coroutines.SupervisorJob() +
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
    )

class AuthTestHarness(
    scope: CoroutineScope,
    initialTokens: AuthTokens? = null,
    hooks: AuthTestHooks = AuthTestHooks.NONE,
) {
    init {
        // The default sink calls android.util.Log, which throws on the JVM. Tests
        // that assert on diagnostics re-replace this with their own capture.
        com.hwb.gamepedia.core.network.AuthDiagnostics.sink = {}
    }

    val api = FakeAuthApi()
    val authedApi = FakeAuthedUserApi()
    val tokenStore = InMemoryTokenStore(initialTokens)
    val userStore = InMemoryUserSessionStore(
        if (initialTokens != null) {
            com.hwb.gamepedia.core.model.AuthUser(
                id = "user-initial",
                email = "user-initial@example.com",
                nickname = "nick-initial",
                profileImageUrl = null,
                status = "ACTIVE",
                createdAtIso = null,
                updatedAtIso = null,
            )
        } else {
            null
        },
    )

    val repository = DefaultAuthRepository(
        authApi = api,
        authedUserApiProvider = { authedApi },
        failureMapper = GamePediaNetwork.createAuthFailureMapper(),
        tokenStore = tokenStore,
        userSessionStore = userStore,
        scope = scope,
        deviceName = "test-device",
        hooks = hooks,
    )
}
