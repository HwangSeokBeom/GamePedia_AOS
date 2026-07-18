package com.hwb.gamepedia.core.model

/**
 * Failure taxonomy for authentication and session management.
 *
 * Mapping is keyed by GamePediaCoreServer's stable `error.code` values (see
 * docs/AUTH_CONTRACT.md), never by localized messages. [isAuthRejection]
 * distinguishes a definitive credential/account rejection (which invalidates the
 * stored session on refresh failure) from transient transport failures (which must
 * NOT clear a session).
 */
sealed class AuthFailure(
    val retryable: Boolean,
    val diagnosticCategory: String,
    open val backendCode: String? = null,
) {
    /** 401 INVALID_CREDENTIALS on login. */
    data class InvalidCredentials(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "invalid_credentials", backendCode = backendCode)

    /** 409 EMAIL_ALREADY_IN_USE on signup. */
    data class EmailAlreadyInUse(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "email_in_use", backendCode = backendCode)

    /** 409 NICKNAME_ALREADY_EXISTS on signup / social first-login. */
    data class NicknameAlreadyExists(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "nickname_exists", backendCode = backendCode)

    /** 400 VALIDATION_ERROR / GOOGLE_ID_TOKEN_REQUIRED and other request-shape rejections. */
    data class ValidationFailed(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "validation_failed", backendCode = backendCode)

    /**
     * 401 UNAUTHORIZED / TOKEN_EXPIRED / TOKEN_REVOKED — the presented token is
     * definitively unusable. On refresh this ends the stored session.
     */
    data class SessionExpired(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "session_expired", backendCode = backendCode)

    /** 403 ACCOUNT_INACTIVE / ACCOUNT_SUSPENDED, 404 ACCOUNT_NOT_FOUND. */
    data class AccountUnavailable(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "account_unavailable", backendCode = backendCode)

    /** 409 SOCIAL_ACCOUNT_CONFLICT / GOOGLE_ACCOUNT_LINK_CONFLICT. */
    data class SocialConflict(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "social_conflict", backendCode = backendCode)

    /** 400 GOOGLE_EMAIL_REQUIRED / 401 GOOGLE_EMAIL_NOT_VERIFIED. */
    data class GoogleAccountRejected(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "google_rejected", backendCode = backendCode)

    /** 500 INTERNAL_SERVER_ERROR and other server-side failures. */
    data class ServerError(override val backendCode: String?) :
        AuthFailure(retryable = true, diagnosticCategory = "server_error", backendCode = backendCode)

    data object Timeout :
        AuthFailure(retryable = true, diagnosticCategory = "timeout")

    data object NetworkUnavailable :
        AuthFailure(retryable = true, diagnosticCategory = "network_unavailable")

    data object MalformedResponse :
        AuthFailure(retryable = true, diagnosticCategory = "malformed_response")

    /** Client-side: no stored refresh token exists; refresh cannot start. */
    data object MissingRefreshToken :
        AuthFailure(retryable = false, diagnosticCategory = "missing_refresh_token")

    /**
     * Client-side: the refresh flight was superseded by a newer login/signup/
     * social login or invalidated by logout/account deletion. Waiters must treat
     * the current session state as authoritative and never retry the old flight.
     */
    data object Superseded :
        AuthFailure(retryable = false, diagnosticCategory = "superseded")

    /** Client-side: the shared flight was cancelled by its last waiter. */
    data object FlightCancelled :
        AuthFailure(retryable = true, diagnosticCategory = "flight_cancelled")

    data class Unexpected(override val backendCode: String?) :
        AuthFailure(retryable = false, diagnosticCategory = "unexpected", backendCode = backendCode)

    /**
     * True when the failure proves the credential/account is rejected (as opposed
     * to a transport problem). Only these clear a stored session on refresh
     * failure; a timeout or offline error must never log the user out.
     */
    val isAuthRejection: Boolean
        get() = this is SessionExpired || this is AccountUnavailable
}
