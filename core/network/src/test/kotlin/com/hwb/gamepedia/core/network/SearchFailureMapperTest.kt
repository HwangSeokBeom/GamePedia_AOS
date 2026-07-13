package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.model.SearchFailure
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SearchFailureMapperTest {

    private val mapper = GamePediaNetwork.createFailureMapper()

    private fun httpException(status: Int, body: String): HttpException =
        HttpException(
            Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())),
        )

    @Test
    fun `INVALID_SEARCH_QUERY maps to InvalidQuery and is not retryable`() {
        val failure = mapper.map(httpException(400, loadFixture("error_invalid_query.json")))

        assertTrue(failure is SearchFailure.InvalidQuery)
        assertEquals("INVALID_SEARCH_QUERY", failure.backendCode)
        assertFalse(failure.retryable)
        assertEquals("invalid_query", failure.diagnosticCategory)
    }

    @Test
    fun `INVALID_GAMES_LIMIT maps to InvalidLimit client bug`() {
        val failure = mapper.map(httpException(400, loadFixture("error_invalid_limit.json")))

        assertTrue(failure is SearchFailure.InvalidLimit)
        assertEquals("INVALID_GAMES_LIMIT", failure.backendCode)
        assertFalse(failure.retryable)
    }

    @Test
    fun `IGDB_UPSTREAM_ERROR maps to retryable ProviderUnavailable`() {
        val failure = mapper.map(httpException(502, loadFixture("error_upstream.json")))

        assertTrue(failure is SearchFailure.ProviderUnavailable)
        assertEquals("IGDB_UPSTREAM_ERROR", failure.backendCode)
        assertTrue(failure.retryable)
    }

    @Test
    fun `TWITCH_AUTH_UNAVAILABLE maps to ProviderUnavailable`() {
        val body = """{"success":false,"error":{"code":"TWITCH_AUTH_UNAVAILABLE","message":"x"}}"""
        val failure = mapper.map(httpException(502, body))

        assertTrue(failure is SearchFailure.ProviderUnavailable)
        assertEquals("TWITCH_AUTH_UNAVAILABLE", failure.backendCode)
    }

    @Test
    fun `IGDB_RATE_LIMITED maps to RateLimited with manual retry`() {
        val failure = mapper.map(httpException(429, loadFixture("error_rate_limited.json")))

        assertTrue(failure is SearchFailure.RateLimited)
        assertTrue(failure.retryable)
    }

    @Test
    fun `500 codes map to non-retryable ServerError`() {
        val configured = """{"success":false,"error":{"code":"IGDB_NOT_CONFIGURED","message":"x"}}"""
        val internal = """{"success":false,"error":{"code":"INTERNAL_SERVER_ERROR","message":"x"}}"""

        assertTrue(mapper.map(httpException(500, configured)) is SearchFailure.ServerError)
        val failure = mapper.map(httpException(500, internal))
        assertTrue(failure is SearchFailure.ServerError)
        assertFalse(failure.retryable)
    }

    @Test
    fun `unparseable error body falls back to HTTP status semantics`() {
        assertTrue(mapper.map(httpException(429, "not json")) is SearchFailure.RateLimited)
        assertTrue(mapper.map(httpException(502, "<html>bad gateway</html>")) is SearchFailure.ProviderUnavailable)
        assertTrue(mapper.map(httpException(500, "")) is SearchFailure.ServerError)
    }

    @Test
    fun `transport failures map to their taxonomy entries`() {
        assertEquals(SearchFailure.Timeout, mapper.map(SocketTimeoutException("timeout")))
        assertEquals(SearchFailure.NetworkUnavailable, mapper.map(IOException("dns")))
        assertEquals(SearchFailure.MalformedResponse, mapper.map(SerializationException("bad json")))
    }

    @Test
    fun `unknown code on unexpected status maps to Unexpected keeping the code`() {
        val body = """{"success":false,"error":{"code":"SOME_NEW_CODE","message":"x"}}"""
        val failure = mapper.map(httpException(418, body))

        assertTrue(failure is SearchFailure.Unexpected)
        assertEquals("SOME_NEW_CODE", failure.backendCode)
    }
}
