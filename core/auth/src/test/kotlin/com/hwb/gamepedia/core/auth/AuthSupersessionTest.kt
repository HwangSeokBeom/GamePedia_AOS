package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthFailure
import com.hwb.gamepedia.core.model.AuthOutcome
import com.hwb.gamepedia.core.model.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session generation / supersession behavior: a newer authenticated session (or an
 * invalidation) always wins over an in-flight refresh, in every ordering.
 *
 * Matrix items covered here: 6, 7, 8, 9, 10, 11, 12 (docs/AUTH_TEST_MATRIX.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSupersessionTest {

    private fun TestScope.startPendingRefresh(h: AuthTestHarness): Deferred<AuthOutcome<*>> {
        val waiter = async { h.repository.refresh() }
        runCurrent()
        assertEquals(1, h.api.refreshCalls.size)
        return waiter
    }

    private suspend fun assertSupersededBy(tag: String, h: AuthTestHarness, waiter: Deferred<AuthOutcome<*>>) {
        val outcome = waiter.await()
        assertEquals(AuthOutcome.Failure(AuthFailure.Superseded), outcome)
        assertEquals("access-$tag", h.tokenStore.tokens()?.accessToken)
        assertEquals("refresh-$tag", h.tokenStore.tokens()?.refreshToken)
        assertEquals("user-$tag", (h.repository.sessionState.value as SessionState.Authenticated).user.id)
    }

    // 6. Login supersedes an older refresh success (late success stays inert)
    @Test
    fun `late refresh success cannot overwrite a newer login session`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        // The old refresh's response is already queued (arrived on the wire)…
        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("stale"))
        // …but a login supersedes before the continuation runs.
        val login = h.repository.login("user-login@example.com", "password123")
        assertTrue(login is AuthOutcome.Success)
        advanceUntilIdle()

        assertSupersededBy("login", h, waiter)
    }

    // 7. Login supersedes an older refresh failure (failure arrives first in queue)
    @Test
    fun `late refresh failure cannot clear a newer login session`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        h.api.refreshCalls[0].gate.completeExceptionally(AuthFixtures.httpException(401, "TOKEN_EXPIRED"))
        val login = h.repository.login("user-login@example.com", "password123")
        assertTrue(login is AuthOutcome.Success)
        advanceUntilIdle()

        assertSupersededBy("login", h, waiter)
    }

    // 8. Signup supersedes an older refresh
    @Test
    fun `signup supersedes an in-flight refresh`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("stale"))
        val signup = h.repository.signUp("user-signup@example.com", "password123", "nick-signup")
        assertTrue(signup is AuthOutcome.Success)
        advanceUntilIdle()

        assertSupersededBy("signup", h, waiter)
    }

    // 9. Google login supersedes an older refresh
    @Test
    fun `google login supersedes an in-flight refresh`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("stale"))
        val google = h.repository.loginWithGoogle("google-id-token")
        assertTrue(google is AuthOutcome.Success)
        advanceUntilIdle()

        assertSupersededBy("google", h, waiter)
    }

    // 10. Logout prevents late refresh persistence
    @Test
    fun `refresh completing after logout cannot restore credentials`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("stale"))
        h.repository.logout()
        advanceUntilIdle()

        assertEquals(AuthOutcome.Failure(AuthFailure.Superseded), waiter.await())
        assertNull("late refresh must not restore tokens after logout", h.tokenStore.tokens())
        assertNull(h.userStore.user())
        assertEquals(SessionState.Unauthenticated, h.repository.sessionState.value)
        // Logout still revoked the ORIGINAL refresh token server-side.
        assertEquals(1, h.api.logoutCalls.size)
    }

    // 11. Account deletion prevents late refresh persistence
    @Test
    fun `refresh completing after account deletion cannot restore credentials`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("stale"))
        val deletion = h.repository.deleteAccount()
        assertTrue(deletion is AuthOutcome.Success)
        assertEquals(1, h.authedApi.deleteCalls)
        advanceUntilIdle()

        assertEquals(AuthOutcome.Failure(AuthFailure.Superseded), waiter.await())
        assertNull(h.tokenStore.tokens())
        assertNull(h.userStore.user())
        assertEquals(SessionState.Unauthenticated, h.repository.sessionState.value)
    }

    // 12. A late failure cannot clear a newer session (failure lands after adoption)
    @Test
    fun `refresh failure resolving after a newer login leaves that session authenticated`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        val waiter = startPendingRefresh(h)

        // Login first (supersedes + cancels the flight), THEN the old request fails.
        val login = h.repository.login("user-login@example.com", "password123")
        assertTrue(login is AuthOutcome.Success)
        h.api.refreshCalls[0].gate.completeExceptionally(AuthFixtures.httpException(401, "TOKEN_REVOKED"))
        advanceUntilIdle()

        assertSupersededBy("login", h, waiter)

        // The newer session stays fully usable: its refresh token drives the next flight.
        val next = async { h.repository.refresh() }
        runCurrent()
        assertEquals(2, h.api.refreshCalls.size)
        assertEquals("refresh-login", h.api.refreshCalls[1].request.refreshToken)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s2"))
        advanceUntilIdle()
        assertTrue(next.await() is AuthOutcome.Success)
    }

    // Supersession also blocks an overlapping rotating-token submission: a new
    // refresh entering while the superseded flight is dying waits for slot release.
    @Test
    fun `replacement refresh never overlaps the superseded flight's request`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))
        startPendingRefresh(h)

        val login = h.repository.login("user-login@example.com", "password123")
        assertTrue(login is AuthOutcome.Success)

        // New refresh while the old flight is still occupying the slot.
        val replacement = async { h.repository.refresh() }
        runCurrent()
        advanceUntilIdle()

        // Exactly one replacement call, and it must carry the LOGIN session's
        // rotated token — never refresh-r0 (which the dying flight submitted).
        assertEquals(2, h.api.refreshCalls.size)
        assertEquals("refresh-login", h.api.refreshCalls[1].request.refreshToken)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s2"))
        advanceUntilIdle()
        assertTrue(replacement.await() is AuthOutcome.Success)
    }
}
