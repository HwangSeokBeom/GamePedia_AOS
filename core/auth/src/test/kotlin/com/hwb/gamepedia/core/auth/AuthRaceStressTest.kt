package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthOutcome
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-dispatcher race coverage, repeated [ITERATIONS] times per scenario.
 *
 * These deliberately race genuine threads at explicit barriers and then assert
 * INVARIANTS that must hold for every interleaving — no sleeps, no polling, no
 * "eventually" helpers; every wait is an explicit join/await on a completion
 * signal (matrix item 13's race half plus fan-out invariants).
 *
 * Invariant under the completion/cancellation race with a ROTATING token:
 *  - the stored pair is always consistent (either the old session or the fully
 *    rotated one — never mixed, never cleared);
 *  - the coordinator is always reusable afterwards and the next flight submits
 *    exactly the currently stored refresh token.
 */
class AuthRaceStressTest {

    /** Any regression that reintroduces a hang fails loudly instead of blocking CI. */
    @get:org.junit.Rule
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(180)

    private companion object {
        const val ITERATIONS = 50
    }

    @Test
    fun `completion racing last-waiter cancellation preserves invariants and reusability`() {
        repeat(ITERATIONS) { iteration ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val h = AuthTestHarness(scope, initialTokens = AuthFixtures.storedTokens("r0"))
                runBlocking {
                    val waiter = scope.async { h.repository.refresh() }
                    val pending = h.api.refreshArrivals.receive() // deterministic arrival signal

                    val barrier = CyclicBarrier(3)
                    val completer = thread {
                        barrier.await()
                        pending.gate.complete(AuthFixtures.sessionEnvelope("s1"))
                    }
                    val canceller = thread {
                        barrier.await()
                        waiter.cancel()
                    }
                    barrier.await()
                    completer.join()
                    canceller.join()
                    runCatching { waiter.await() } // quiesce: waiter resolved or cancelled

                    // Reusability probe: a new refresh must always be able to start.
                    val next = scope.async { h.repository.refresh() }
                    val pending2 = h.api.refreshArrivals.receive()

                    val storedBefore = pending2.request.refreshToken
                    assertTrue(
                        "iteration $iteration: rotating token must be old or fully rotated, was $storedBefore",
                        storedBefore == "refresh-r0" || storedBefore == "refresh-s1",
                    )
                    assertEquals(
                        "iteration $iteration: flight must submit exactly the stored refresh token",
                        h.tokenStore.tokens()?.refreshToken,
                        storedBefore,
                    )
                    // The rotating token is never submitted concurrently: the
                    // replacement flight only exists because the first fully
                    // released the slot (2 total provider calls at this point).
                    assertEquals(2, synchronized(h.api.refreshCalls) { h.api.refreshCalls.size })

                    pending2.gate.complete(AuthFixtures.sessionEnvelope("s2"))
                    assertTrue(next.await() is AuthOutcome.Success)
                    assertEquals("refresh-s2", h.tokenStore.tokens()?.refreshToken)
                }
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `concurrent waiter fan-out always shares exactly one flight`() {
        repeat(ITERATIONS) { iteration ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                // Barrier: the provider response is withheld until ALL waiters
                // have registered, so they provably overlap one flight.
                val allJoined = java.util.concurrent.CountDownLatch(8)
                val h = AuthTestHarness(
                    scope,
                    initialTokens = AuthFixtures.storedTokens("r0"),
                    hooks = AuthTestHooks(onWaiterAttached = { allJoined.countDown() }),
                )
                runBlocking {
                    val waiters = (1..8).map { scope.async { h.repository.refresh() } }
                    val pending = h.api.refreshArrivals.receive()
                    assertTrue(
                        "iteration $iteration: all 8 waiters must join the flight",
                        runInterruptible(Dispatchers.IO) {
                            allJoined.await(30, java.util.concurrent.TimeUnit.SECONDS)
                        },
                    )
                    pending.gate.complete(AuthFixtures.sessionEnvelope("s1"))
                    val outcomes = waiters.awaitAll()

                    assertEquals(
                        "iteration $iteration: 8 concurrent waiters must produce exactly 1 provider call",
                        1,
                        synchronized(h.api.refreshCalls) { h.api.refreshCalls.size },
                    )
                    outcomes.forEach { outcome ->
                        assertTrue(outcome is AuthOutcome.Success)
                        assertEquals(
                            "refresh-s1",
                            (outcome as AuthOutcome.Success).value.tokens.refreshToken,
                        )
                    }
                    assertEquals("refresh-s1", h.tokenStore.tokens()?.refreshToken)
                }
            } finally {
                scope.cancel()
            }
        }
    }
}
