package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.network.GamePediaNetwork
import com.hwb.gamepedia.core.storage.InMemoryTokenStore
import com.hwb.gamepedia.core.storage.InMemoryUserSessionStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * OkHttp-level guarantees through the real Retrofit/OkHttp/authenticator stack
 * against MockWebServer. Determinism comes from explicit barriers (latches gated
 * on coordinator hooks), never from sleeps or polling; latch timeouts fail loudly.
 *
 * Matrix items covered here: 16, 17, plus authenticator retry-once (docs/AUTH_TEST_MATRIX.md).
 */
class SessionAuthenticatorHttpTest {

    /** Any regression that reintroduces a hang fails loudly instead of blocking CI. */
    @get:org.junit.Rule
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    private val meEnvelope =
        """{"success":true,"data":{"user":{"id":"user-me","email":"me@example.com","nickname":"me","profileImageUrl":null,"status":"ACTIVE","createdAt":null,"updatedAt":null}}}"""

    private fun rotationEnvelope(tag: String) =
        """{"success":true,"data":{"user":{"id":"user-$tag","email":"u@example.com","nickname":"n","profileImageUrl":null,"status":"ACTIVE","createdAt":null,"updatedAt":null},"tokens":{"accessToken":"access-$tag","refreshToken":"refresh-$tag"}}}"""

    private val unauthorizedEnvelope =
        """{"success":false,"error":{"code":"UNAUTHORIZED","message":"x"}}"""

    private val tokenExpiredEnvelope =
        """{"success":false,"error":{"code":"TOKEN_EXPIRED","message":"x"}}"""

    @Before
    fun setUp() {
        com.hwb.gamepedia.core.network.AuthDiagnostics.sink = {} // android.util.Log throws on the JVM
        server = MockWebServer()
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun buildRepository(hooks: AuthTestHooks = AuthTestHooks.NONE): Pair<DefaultAuthRepository, com.hwb.gamepedia.core.network.AuthedUserApi> {
        val baseUrl = server.url("/").toString()
        val plainClient = GamePediaNetwork.createOkHttpClient()
        val repository = DefaultAuthRepository(
            authApi = GamePediaNetwork.createAuthApi(baseUrl = baseUrl, client = plainClient),
            authedUserApiProvider = { error("not used in this test") },
            failureMapper = GamePediaNetwork.createAuthFailureMapper(),
            // The pair must be consistent: repository init clears partial records.
            tokenStore = InMemoryTokenStore(AuthFixtures.storedTokens("stale")),
            userSessionStore = InMemoryUserSessionStore(AuthFixtures.storedUser("stale")),
            scope = scope,
            hooks = hooks,
        )
        val authedClient = GamePediaNetwork.createAuthenticatedClient(
            gateway = repository,
            plainClient = plainClient,
        )
        val authedApi = GamePediaNetwork.createAuthedUserApi(authedClient, baseUrl = baseUrl)
        return repository to authedApi
    }

    // 16. A concurrent 401 fan-out triggers exactly ONE refresh
    @Test
    fun `two concurrent 401s share one refresh and both requests succeed after rotation`() {
        val refreshHits = AtomicInteger(0)
        val meHits = AtomicInteger(0)
        val refreshHadAuthHeader = AtomicBoolean(false)
        // Barrier: the refresh response is withheld until BOTH callers have
        // joined the shared flight, forcing full overlap of the authenticators.
        val bothWaitersJoined = CountDownLatch(1)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/auth/me" -> {
                    meHits.incrementAndGet()
                    if (request.getHeader("Authorization") == "Bearer access-good") {
                        MockResponse().setBody(meEnvelope)
                    } else {
                        MockResponse().setResponseCode(401).setBody(unauthorizedEnvelope)
                    }
                }
                "/auth/refresh" -> {
                    refreshHits.incrementAndGet()
                    if (request.getHeader("Authorization") != null) refreshHadAuthHeader.set(true)
                    assertTrue(
                        "refresh response gate must open once both waiters joined",
                        bothWaitersJoined.await(30, TimeUnit.SECONDS),
                    )
                    MockResponse().setBody(rotationEnvelope("good"))
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val (repository, authedApi) = buildRepository(
            hooks = AuthTestHooks(
                onWaiterAttached = { waiters -> if (waiters == 2) bothWaitersJoined.countDown() },
            ),
        )

        val results = runBlocking {
            listOf(
                scope.async { authedApi.me() },
                scope.async { authedApi.me() },
            ).awaitAll()
        }

        assertEquals("exactly one refresh for the whole 401 fan-out", 1, refreshHits.get())
        assertEquals(4, meHits.get()) // 2 rejected + 2 retried
        assertEquals(false, refreshHadAuthHeader.get())
        assertEquals(2, results.size)
        results.forEach { assertEquals("user-me", it.data.user.id) }
        assertEquals("access-good", repository.currentAccessToken())
    }

    // 17. The refresh endpoint itself never triggers a recursive refresh
    @Test
    fun `a 401 from the refresh endpoint fails once and never recurses`() {
        val refreshHits = AtomicInteger(0)
        val meHits = AtomicInteger(0)
        val refreshHadAuthHeader = AtomicBoolean(false)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/auth/me" -> {
                    meHits.incrementAndGet()
                    MockResponse().setResponseCode(401).setBody(unauthorizedEnvelope)
                }
                "/auth/refresh" -> {
                    refreshHits.incrementAndGet()
                    if (request.getHeader("Authorization") != null) refreshHadAuthHeader.set(true)
                    MockResponse().setResponseCode(401).setBody(tokenExpiredEnvelope)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val (repository, authedApi) = buildRepository()

        val thrown = runCatching { runBlocking { authedApi.me() } }.exceptionOrNull()

        assertTrue("caller must observe the original 401", thrown is HttpException && thrown.code() == 401)
        assertEquals("refresh endpoint hit exactly once, no recursion", 1, refreshHits.get())
        assertEquals("original request must not be retried after failed refresh", 1, meHits.get())
        assertEquals("refresh request must never carry credentials", false, refreshHadAuthHeader.get())
        // TOKEN_EXPIRED is a definitive rejection: the session ends.
        assertNull(repository.currentAccessToken())
    }

    // Authenticator retries at most once for a given token state
    @Test
    fun `a request that still 401s after one rotation is not retried again`() {
        val refreshHits = AtomicInteger(0)
        val meHits = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/auth/me" -> {
                    meHits.incrementAndGet()
                    MockResponse().setResponseCode(401).setBody(unauthorizedEnvelope) // always rejects
                }
                "/auth/refresh" -> {
                    refreshHits.incrementAndGet()
                    MockResponse().setBody(rotationEnvelope("good"))
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val (repository, authedApi) = buildRepository()

        val thrown = runCatching { runBlocking { authedApi.me() } }.exceptionOrNull()

        assertTrue(thrown is HttpException && (thrown as HttpException).code() == 401)
        assertEquals("original + exactly one retry", 2, meHits.get())
        assertEquals("one refresh, not one per 401", 1, refreshHits.get())
        // The rotated session itself is intact (the endpoint rejected, not the token).
        assertEquals("access-good", repository.currentAccessToken())
    }
}
