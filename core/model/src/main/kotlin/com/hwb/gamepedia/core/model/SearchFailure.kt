package com.hwb.gamepedia.core.model

/**
 * Client-side failure taxonomy for the Trustworthy Search slice.
 *
 * Mapping is driven by the stable backend `error.code` values (never by localized
 * `error.message`) plus transport-level failures. Each failure carries:
 *  - [retryable]: whether the UI may offer/perform a retry of the same query
 *  - [diagnosticCategory]: privacy-safe stable label for logs (no raw query, no URLs)
 *  - [backendCode]: original backend code when the failure came from an error envelope
 */
sealed class SearchFailure(
    val retryable: Boolean,
    val diagnosticCategory: String,
    open val backendCode: String? = null,
) {
    /** 400 INVALID_SEARCH_QUERY / VALIDATION_ERROR on `q` — should be prevented client-side. */
    data class InvalidQuery(override val backendCode: String?) :
        SearchFailure(retryable = false, diagnosticCategory = "invalid_query", backendCode = backendCode)

    /** 400 INVALID_GAMES_LIMIT — a client bug, never user-visible as such. */
    data class InvalidLimit(override val backendCode: String?) :
        SearchFailure(retryable = false, diagnosticCategory = "invalid_limit", backendCode = backendCode)

    /** 429 IGDB_RATE_LIMITED — back off; manual retry only, no immediate auto-retry. */
    data class RateLimited(override val backendCode: String?) :
        SearchFailure(retryable = true, diagnosticCategory = "rate_limited", backendCode = backendCode)

    /** 502 IGDB_UPSTREAM_ERROR / TWITCH_AUTH_UNAVAILABLE — transient provider failure. */
    data class ProviderUnavailable(override val backendCode: String?) :
        SearchFailure(retryable = true, diagnosticCategory = "provider_unavailable", backendCode = backendCode)

    /** 500 IGDB_NOT_CONFIGURED / INTERNAL_SERVER_ERROR — non-retryable in-session. */
    data class ServerError(override val backendCode: String?) :
        SearchFailure(retryable = false, diagnosticCategory = "server_error", backendCode = backendCode)

    /** Connect/read timeout. */
    data object Timeout :
        SearchFailure(retryable = true, diagnosticCategory = "timeout")

    /** No connectivity / DNS / socket failure. */
    data object NetworkUnavailable :
        SearchFailure(retryable = true, diagnosticCategory = "network_unavailable")

    /** 2xx body (or error body) that does not decode against the contract. */
    data object MalformedResponse :
        SearchFailure(retryable = true, diagnosticCategory = "malformed_response")

    /** Anything else, including unknown backend codes on unexpected statuses. */
    data class Unexpected(override val backendCode: String?) :
        SearchFailure(retryable = false, diagnosticCategory = "unexpected", backendCode = backendCode)
}
