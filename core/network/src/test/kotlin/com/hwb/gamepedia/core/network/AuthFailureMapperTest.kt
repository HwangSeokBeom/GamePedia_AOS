package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.model.AuthFailure
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

class AuthFailureMapperTest {

    private val mapper = GamePediaNetwork.createAuthFailureMapper()

    private fun httpException(status: Int, code: String?): HttpException {
        val body = if (code == null) {
            "not json"
        } else {
            """{"success":false,"error":{"code":"$code","message":"x"}}"""
        }
        return HttpException(
            Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())),
        )
    }

    @Test
    fun `login and signup codes map to their taxonomy entries`() {
        assertTrue(mapper.map(httpException(401, "INVALID_CREDENTIALS")) is AuthFailure.InvalidCredentials)
        assertTrue(mapper.map(httpException(409, "EMAIL_ALREADY_IN_USE")) is AuthFailure.EmailAlreadyInUse)
        assertTrue(mapper.map(httpException(409, "NICKNAME_ALREADY_EXISTS")) is AuthFailure.NicknameAlreadyExists)
        assertTrue(mapper.map(httpException(400, "VALIDATION_ERROR")) is AuthFailure.ValidationFailed)
    }

    @Test
    fun `token rejection codes are auth rejections`() {
        listOf("UNAUTHORIZED", "TOKEN_EXPIRED", "TOKEN_REVOKED").forEach { code ->
            val failure = mapper.map(httpException(401, code))
            assertTrue("$code should be SessionExpired", failure is AuthFailure.SessionExpired)
            assertTrue("$code must count as auth rejection", failure.isAuthRejection)
            assertEquals(code, failure.backendCode)
        }
    }

    @Test
    fun `account status codes are auth rejections`() {
        assertTrue(mapper.map(httpException(404, "ACCOUNT_NOT_FOUND")).isAuthRejection)
        assertTrue(mapper.map(httpException(403, "ACCOUNT_INACTIVE")).isAuthRejection)
        assertTrue(mapper.map(httpException(403, "ACCOUNT_SUSPENDED")).isAuthRejection)
    }

    @Test
    fun `google and social codes map without becoming session rejections`() {
        assertTrue(mapper.map(httpException(400, "GOOGLE_EMAIL_REQUIRED")) is AuthFailure.GoogleAccountRejected)
        assertTrue(mapper.map(httpException(401, "GOOGLE_EMAIL_NOT_VERIFIED")) is AuthFailure.GoogleAccountRejected)
        assertTrue(mapper.map(httpException(409, "SOCIAL_ACCOUNT_CONFLICT")) is AuthFailure.SocialConflict)
        assertTrue(mapper.map(httpException(409, "GOOGLE_ACCOUNT_LINK_CONFLICT")) is AuthFailure.SocialConflict)
        assertFalse(mapper.map(httpException(401, "GOOGLE_EMAIL_NOT_VERIFIED")).isAuthRejection)
    }

    @Test
    fun `transport failures never count as auth rejections`() {
        assertEquals(AuthFailure.Timeout, mapper.map(SocketTimeoutException("t")))
        assertEquals(AuthFailure.NetworkUnavailable, mapper.map(IOException("io")))
        assertEquals(AuthFailure.MalformedResponse, mapper.map(SerializationException("bad")))
        assertFalse(mapper.map(SocketTimeoutException("t")).isAuthRejection)
        assertFalse(mapper.map(IOException("io")).isAuthRejection)
    }

    @Test
    fun `unparseable error bodies fall back to status semantics`() {
        assertTrue(mapper.map(httpException(401, null)) is AuthFailure.SessionExpired)
        assertTrue(mapper.map(httpException(500, null)) is AuthFailure.ServerError)
        assertTrue(mapper.map(httpException(400, null)) is AuthFailure.ValidationFailed)
    }
}
