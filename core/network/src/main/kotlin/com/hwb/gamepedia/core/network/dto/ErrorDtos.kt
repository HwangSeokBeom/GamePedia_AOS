package com.hwb.gamepedia.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Shared error envelope: `{ "success": false, "error": { code, message, details? } }`.
 * Clients branch on [ErrorBodyDto.code] only; `message` is sanitized/localized and
 * not stable. `details` shape varies (array/object/null) so it stays a JsonElement;
 * it is used for QA logging only, never for UI copy.
 */
@Serializable
data class ErrorEnvelopeDto(
    @SerialName("success") val success: Boolean,
    @SerialName("error") val error: ErrorBodyDto,
)

@Serializable
data class ErrorBodyDto(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("details") val details: JsonElement? = null,
)

/** Stable backend error codes for the search slice (contract 8790a13). */
object BackendErrorCodes {
    const val INVALID_SEARCH_QUERY = "INVALID_SEARCH_QUERY"
    const val INVALID_GAMES_LIMIT = "INVALID_GAMES_LIMIT"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val IGDB_RATE_LIMITED = "IGDB_RATE_LIMITED"
    const val IGDB_UPSTREAM_ERROR = "IGDB_UPSTREAM_ERROR"
    const val TWITCH_AUTH_UNAVAILABLE = "TWITCH_AUTH_UNAVAILABLE"
    const val IGDB_NOT_CONFIGURED = "IGDB_NOT_CONFIGURED"
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"
    const val NOT_FOUND = "NOT_FOUND"
}
