package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthFailure
import com.hwb.gamepedia.core.model.AuthOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Single-flight refresh coordinator behavior under virtual time. Every test is
 * fully deterministic: fake provider calls suspend on explicit gates, ordering is
 * driven by runCurrent()/advanceUntilIdle(), and there are no delays or polling.
 *
 * Matrix items covered here: 1, 2, 3, 4, 5, 13, 14 (docs/AUTH_TEST_MATRIX.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRefreshConcurrencyTest {

    // 1. Two concurrent callers share one refresh request
    @Test
    fun `two concurrent callers share one refresh request and one result`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val first = async { h.repository.refresh() }
        val second = async { h.repository.refresh() }
        runCurrent()

        assertEquals(1, h.api.refreshCalls.size)
        assertEquals("refresh-r0", h.api.refreshCalls[0].request.refreshToken)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()

        val firstOutcome = first.await()
        val secondOutcome = second.await()
        assertTrue(firstOutcome is AuthOutcome.Success)
        // All subscribers receive the same shared result (guarantee 3).
        assertEquals(firstOutcome, secondOutcome)
        assertEquals("access-s1", h.tokenStore.tokens()?.accessToken)
        assertEquals("refresh-s1", h.tokenStore.tokens()?.refreshToken)
    }

    // 2. Sequential refresh uses the newly rotated token
    @Test
    fun `sequential refresh submits the rotated refresh token`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val first = async { h.repository.refresh() }
        runCurrent()
        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()
        assertTrue(first.await() is AuthOutcome.Success)

        val second = async { h.repository.refresh() }
        runCurrent()

        assertEquals(2, h.api.refreshCalls.size)
        assertEquals("refresh-s1", h.api.refreshCalls[1].request.refreshToken)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s2"))
        advanceUntilIdle()
        assertTrue(second.await() is AuthOutcome.Success)
        assertEquals("refresh-s2", h.tokenStore.tokens()?.refreshToken)
    }

    // 3. One waiter cancellation does not cancel a surviving waiter
    @Test
    fun `cancelling one waiter keeps the shared refresh alive for the survivor`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val doomed = async { h.repository.refresh() }
        val survivor = async { h.repository.refresh() }
        runCurrent()
        assertEquals(1, h.api.refreshCalls.size)

        doomed.cancel()
        runCurrent()

        assertFalse("provider work must survive for the remaining waiter", h.api.refreshCalls[0].cancelled)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()

        assertTrue(survivor.await() is AuthOutcome.Success)
        assertEquals("access-s1", h.tokenStore.tokens()?.accessToken)
    }

    // 4. Last waiter cancellation cancels provider work
    @Test
    fun `cancelling the last waiter cancels the provider request and leaves the session untouched`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val first = async { h.repository.refresh() }
        val second = async { h.repository.refresh() }
        runCurrent()
        assertEquals(1, h.api.refreshCalls.size)

        first.cancel()
        second.cancel()
        advanceUntilIdle()

        assertTrue("last waiter must cancel provider work", h.api.refreshCalls[0].cancelled)
        assertEquals("refresh-r0", h.tokenStore.tokens()?.refreshToken)

        // Coordinator is reusable: a new refresh starts a fresh flight.
        val next = async { h.repository.refresh() }
        runCurrent()
        assertEquals(2, h.api.refreshCalls.size)
        assertEquals("refresh-r0", h.api.refreshCalls[1].request.refreshToken)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()
        assertTrue(next.await() is AuthOutcome.Success)
    }

    // 5. Delayed subscriber receives the shared result
    @Test
    fun `a waiter joining mid-flight receives the same shared result without a second request`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val early = async { h.repository.refresh() }
        runCurrent()
        assertEquals(1, h.api.refreshCalls.size)

        val late = async { h.repository.refresh() }
        runCurrent()
        assertEquals("late joiner must not start a second request", 1, h.api.refreshCalls.size)

        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()

        assertEquals(early.await(), late.await())
        assertTrue(early.await() is AuthOutcome.Success)
    }

    // 13. Cancellation / completion race leaves the coordinator reusable
    @Test
    fun `response arriving as the last waiter cancels still commits the rotation and stays reusable`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val waiter = async { h.repository.refresh() }
        runCurrent()
        assertEquals(1, h.api.refreshCalls.size)

        // The provider response has arrived but its continuation has not run yet;
        // the last waiter cancels in that window.
        h.api.refreshCalls[0].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        waiter.cancel()
        advanceUntilIdle()

        // A completed rotation must persist: the server already revoked
        // refresh-r0, so discarding the response would strand the client.
        assertEquals("refresh-s1", h.tokenStore.tokens()?.refreshToken)

        // Coordinator remains reusable with exactly one new flight.
        val next = async { h.repository.refresh() }
        runCurrent()
        assertEquals(2, h.api.refreshCalls.size)
        assertEquals("refresh-s1", h.api.refreshCalls[1].request.refreshToken)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s2"))
        advanceUntilIdle()
        assertTrue(next.await() is AuthOutcome.Success)
    }

    // 13 (other interleaving): cancellation lands before the response
    @Test
    fun `cancellation before the response leaves tokens untouched and the coordinator reusable`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val waiter = async { h.repository.refresh() }
        runCurrent()
        waiter.cancel()
        advanceUntilIdle()

        assertTrue(h.api.refreshCalls[0].cancelled)
        assertEquals("refresh-r0", h.tokenStore.tokens()?.refreshToken)

        val next = async { h.repository.refresh() }
        runCurrent()
        assertEquals(2, h.api.refreshCalls.size)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()
        assertTrue(next.await() is AuthOutcome.Success)
    }

    // 14. Refresh failure detaches the flight
    @Test
    fun `network failure detaches the flight and preserves the stored session`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val first = async { h.repository.refresh() }
        runCurrent()
        h.api.refreshCalls[0].gate.completeExceptionally(java.io.IOException("socket closed"))
        advanceUntilIdle()

        val outcome = first.await()
        assertTrue(outcome is AuthOutcome.Failure)
        assertEquals(AuthFailure.NetworkUnavailable, (outcome as AuthOutcome.Failure).failure)
        // Transport failure must NOT clear the session (guarantee: distinguish
        // auth rejection from network failure).
        assertEquals("refresh-r0", h.tokenStore.tokens()?.refreshToken)

        // Detached: the next refresh starts a brand-new flight.
        val second = async { h.repository.refresh() }
        runCurrent()
        assertEquals(2, h.api.refreshCalls.size)
        h.api.refreshCalls[1].gate.complete(AuthFixtures.sessionEnvelope("s1"))
        advanceUntilIdle()
        assertTrue(second.await() is AuthOutcome.Success)
    }

    // 14b. Auth-rejection failure ends the session and detaches
    @Test
    fun `token-revoked failure clears the session and the next refresh reports missing token`() = runTest {
        val h = AuthTestHarness(repositoryScope(), initialTokens = AuthFixtures.storedTokens("r0"))

        val first = async { h.repository.refresh() }
        runCurrent()
        h.api.refreshCalls[0].gate.completeExceptionally(AuthFixtures.httpException(401, "TOKEN_REVOKED"))
        advanceUntilIdle()

        val outcome = first.await()
        assertTrue((outcome as AuthOutcome.Failure).failure is AuthFailure.SessionExpired)
        assertNull(h.tokenStore.tokens())
        assertNull(h.userStore.user())

        val second = h.repository.refresh()
        assertEquals(
            AuthOutcome.Failure(AuthFailure.MissingRefreshToken),
            second,
        )
        assertEquals("no new provider call without a stored token", 1, h.api.refreshCalls.size)
    }
}
