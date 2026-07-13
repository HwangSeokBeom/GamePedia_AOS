package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.model.SearchFailure
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * End-to-end HTTP-layer tests through the real Retrofit/OkHttp/kotlinx stack against
 * canonical backend fixtures (contract 8790a13).
 */
class SearchApiHttpContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SearchApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = GamePediaNetwork.createSearchApi(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search request uses contract path and params and sends no Authorization header`() = runTest {
        server.enqueue(MockResponse().setBody(loadFixture("search_success.json")))

        val envelope = api.searchGames(query = "contract quest", limit = 20)

        val recorded = server.takeRequest()
        assertEquals("/games/search?q=contract%20quest&limit=20", recorded.path)
        // Public endpoint: the client must never attach credentials.
        assertNull(recorded.getHeader("Authorization"))
        assertTrue(envelope.success)
        assertEquals(2, envelope.data.games.size)
    }

    @Test
    fun `suggestions request uses contract path and params and sends no Authorization header`() = runTest {
        server.enqueue(MockResponse().setBody(loadFixture("suggestions_success.json")))

        val envelope = api.getSuggestions(query = "suggestion case", limit = 6)

        val recorded = server.takeRequest()
        assertEquals("/games/suggestions?q=suggestion%20case&limit=6", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        assertEquals(2, envelope.data.suggestions.size)
    }

    @Test
    fun `400 error envelope surfaces as HttpException mapping to InvalidQuery`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(loadFixture("error_invalid_query.json")),
        )

        val failure = try {
            api.searchGames(query = " ", limit = 20)
            null
        } catch (exception: HttpException) {
            GamePediaNetwork.createFailureMapper().map(exception)
        }

        assertTrue(failure is SearchFailure.InvalidQuery)
    }

    @Test
    fun `malformed 200 body maps to MalformedResponse`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"unexpected":"shape"}}"""))

        val failure = try {
            api.searchGames(query = "contract quest", limit = 20)
            null
        } catch (throwable: Throwable) {
            GamePediaNetwork.createFailureMapper().map(throwable)
        }

        assertEquals(SearchFailure.MalformedResponse, failure)
    }
}
