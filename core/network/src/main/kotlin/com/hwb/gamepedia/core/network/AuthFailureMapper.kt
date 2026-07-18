package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.model.AuthFailure
import com.hwb.gamepedia.core.network.dto.AuthErrorCodes
import com.hwb.gamepedia.core.network.dto.ErrorEnvelopeDto
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Maps transport/HTTP failures of auth endpoints to [AuthFailure] using stable
 * backend `error.code` values only. Never inspects localized messages; never
 * embeds request/response bodies (which carry credentials) in the result.
 */
class AuthFailureMapper(private val json: Json) {

    fun map(throwable: Throwable): AuthFailure = when (throwable) {
        is CancellationException -> throw throwable
        is HttpException -> mapHttp(throwable)
        is SocketTimeoutException -> AuthFailure.Timeout
        is SerializationException -> AuthFailure.MalformedResponse
        is IOException -> AuthFailure.NetworkUnavailable
        else -> AuthFailure.Unexpected(backendCode = null)
    }

    private fun mapHttp(exception: HttpException): AuthFailure {
        val code = parseBackendCode(exception)
        return when (code) {
            AuthErrorCodes.INVALID_CREDENTIALS -> AuthFailure.InvalidCredentials(code)
            AuthErrorCodes.EMAIL_ALREADY_IN_USE -> AuthFailure.EmailAlreadyInUse(code)
            AuthErrorCodes.NICKNAME_ALREADY_EXISTS -> AuthFailure.NicknameAlreadyExists(code)

            AuthErrorCodes.UNAUTHORIZED,
            AuthErrorCodes.TOKEN_EXPIRED,
            AuthErrorCodes.TOKEN_REVOKED,
            -> AuthFailure.SessionExpired(code)

            AuthErrorCodes.ACCOUNT_NOT_FOUND,
            AuthErrorCodes.ACCOUNT_INACTIVE,
            AuthErrorCodes.ACCOUNT_SUSPENDED,
            -> AuthFailure.AccountUnavailable(code)

            AuthErrorCodes.VALIDATION_ERROR,
            AuthErrorCodes.GOOGLE_ID_TOKEN_REQUIRED,
            -> AuthFailure.ValidationFailed(code)

            AuthErrorCodes.GOOGLE_EMAIL_REQUIRED,
            AuthErrorCodes.GOOGLE_EMAIL_NOT_VERIFIED,
            -> AuthFailure.GoogleAccountRejected(code)

            AuthErrorCodes.SOCIAL_ACCOUNT_CONFLICT,
            AuthErrorCodes.GOOGLE_ACCOUNT_LINK_CONFLICT,
            -> AuthFailure.SocialConflict(code)

            AuthErrorCodes.INTERNAL_SERVER_ERROR -> AuthFailure.ServerError(code)

            else -> when (exception.code()) {
                // Unknown/unparseable code: fall back to status semantics.
                401 -> AuthFailure.SessionExpired(code)
                403, 404 -> AuthFailure.AccountUnavailable(code)
                400, 409 -> AuthFailure.ValidationFailed(code)
                in 500..599 -> AuthFailure.ServerError(code)
                else -> AuthFailure.Unexpected(code)
            }
        }
    }

    private fun parseBackendCode(exception: HttpException): String? {
        val body = try {
            exception.response()?.errorBody()?.string()
        } catch (_: IOException) {
            null
        }
        if (body.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ErrorEnvelopeDto>(body).error.code
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
