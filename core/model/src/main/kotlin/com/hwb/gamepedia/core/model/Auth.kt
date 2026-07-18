package com.hwb.gamepedia.core.model

/**
 * Authenticated user profile as returned by GamePediaCoreServer's `mapUserToDto`
 * (`/auth` responses). Timestamps stay ISO-8601 strings as received.
 */
data class AuthUser(
    val id: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val status: String,
    val createdAtIso: String?,
    val updatedAtIso: String?,
)

/**
 * Access/refresh token pair. Deliberately NOT a data class: toString(), and
 * therefore log interpolation, exception messages, and debugger dumps, must never
 * expose raw token material.
 */
class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "AuthTokens(accessToken=<redacted>, refreshToken=<redacted>)"

    override fun equals(other: Any?): Boolean =
        other is AuthTokens && other.accessToken == accessToken && other.refreshToken == refreshToken

    override fun hashCode(): Int = 31 * accessToken.hashCode() + refreshToken.hashCode()
}

/** A fully authenticated session: profile + token pair. toString stays redacted. */
class AuthSession(
    val user: AuthUser,
    val tokens: AuthTokens,
) {
    override fun toString(): String = "AuthSession(userId=${user.id}, tokens=<redacted>)"

    override fun equals(other: Any?): Boolean =
        other is AuthSession && other.user == user && other.tokens == tokens

    override fun hashCode(): Int = 31 * user.hashCode() + tokens.hashCode()
}

/** App-level session state exposed to navigation/UI. Never carries tokens. */
sealed interface SessionState {
    data object Unauthenticated : SessionState
    data class Authenticated(val user: AuthUser) : SessionState
}

/** Result wrapper for auth operations (no exceptions except coroutine cancellation). */
sealed interface AuthOutcome<out T> {
    data class Success<T>(val value: T) : AuthOutcome<T>
    data class Failure(val failure: AuthFailure) : AuthOutcome<Nothing>
}
