package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.model.SearchFailure
import com.hwb.gamepedia.core.network.dto.BackendErrorCodes
import com.hwb.gamepedia.core.network.dto.ErrorEnvelopeDto
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Maps transport/HTTP failures to the [SearchFailure] taxonomy using stable backend
 * codes only. Localized `error.message` values are intentionally discarded.
 */
class SearchFailureMapper(private val json: Json) {

    fun map(throwable: Throwable): SearchFailure = when (throwable) {
        is CancellationException -> throw throwable
        is HttpException -> mapHttp(throwable)
        is SocketTimeoutException -> SearchFailure.Timeout
        is SerializationException -> SearchFailure.MalformedResponse
        is IOException -> SearchFailure.NetworkUnavailable
        else -> SearchFailure.Unexpected(backendCode = null)
    }

    private fun mapHttp(exception: HttpException): SearchFailure {
        val code = parseBackendCode(exception)
        return when (code) {
            BackendErrorCodes.INVALID_SEARCH_QUERY,
            BackendErrorCodes.VALIDATION_ERROR,
            -> SearchFailure.InvalidQuery(code)

            BackendErrorCodes.INVALID_GAMES_LIMIT -> SearchFailure.InvalidLimit(code)

            BackendErrorCodes.IGDB_RATE_LIMITED -> SearchFailure.RateLimited(code)

            BackendErrorCodes.IGDB_UPSTREAM_ERROR,
            BackendErrorCodes.TWITCH_AUTH_UNAVAILABLE,
            -> SearchFailure.ProviderUnavailable(code)

            BackendErrorCodes.IGDB_NOT_CONFIGURED,
            BackendErrorCodes.INTERNAL_SERVER_ERROR,
            -> SearchFailure.ServerError(code)

            else -> when (exception.code()) {
                // Known statuses with an unparseable/unknown code keep contract behavior.
                429 -> SearchFailure.RateLimited(code)
                502 -> SearchFailure.ProviderUnavailable(code)
                500 -> SearchFailure.ServerError(code)
                in 400..499 -> SearchFailure.Unexpected(code)
                else -> if (code == null) {
                    SearchFailure.MalformedResponse
                } else {
                    SearchFailure.Unexpected(code)
                }
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
