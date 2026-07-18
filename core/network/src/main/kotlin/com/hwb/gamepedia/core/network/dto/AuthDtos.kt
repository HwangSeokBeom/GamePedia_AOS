package com.hwb.gamepedia.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for GamePediaCoreServer `/auth` endpoints (see docs/AUTH_CONTRACT.md).
 *
 * Request DTOs:
 *  - Optional fields default to null and are OMITTED when null (the shared Json
 *    has encodeDefaults=false): the backend zod schemas declare them `.optional()`
 *    but not `.nullable()`, so an explicit JSON null would be a validation error.
 *  - Credential-carrying classes are NOT data classes and redact toString() so
 *    passwords/tokens can never leak through logging or exception interpolation.
 */

@Serializable
class SignUpRequestDto(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("nickname") val nickname: String,
    @SerialName("deviceName") val deviceName: String? = null,
) {
    override fun toString(): String = "SignUpRequestDto(<redacted>)"
}

@Serializable
class LoginRequestDto(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("deviceName") val deviceName: String? = null,
) {
    override fun toString(): String = "LoginRequestDto(<redacted>)"
}

@Serializable
class GoogleLoginRequestDto(
    @SerialName("idToken") val idToken: String,
    @SerialName("deviceName") val deviceName: String? = null,
) {
    override fun toString(): String = "GoogleLoginRequestDto(<redacted>)"
}

@Serializable
class RefreshRequestDto(
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("deviceName") val deviceName: String? = null,
) {
    override fun toString(): String = "RefreshRequestDto(<redacted>)"
}

@Serializable
class LogoutRequestDto(
    @SerialName("refreshToken") val refreshToken: String,
) {
    override fun toString(): String = "LogoutRequestDto(<redacted>)"
}

/** `data` of signup (201) / login / google / refresh: `{ user, tokens }`. */
@Serializable
class AuthSessionDataDto(
    @SerialName("user") val user: AuthUserDto,
    @SerialName("tokens") val tokens: TokenPairDto,
) {
    override fun toString(): String = "AuthSessionDataDto(userId=${user.id}, tokens=<redacted>)"
}

@Serializable
class TokenPairDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
) {
    override fun toString(): String = "TokenPairDto(<redacted>)"
}

/** Mirrors backend `mapUserToDto`. Timestamps arrive as ISO-8601 strings. */
@Serializable
data class AuthUserDto(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("nickname") val nickname: String,
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
    @SerialName("status") val status: String,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
)

@Serializable
data class LogoutResultDto(
    @SerialName("loggedOut") val loggedOut: Boolean,
)

@Serializable
data class MeDataDto(
    @SerialName("user") val user: AuthUserDto,
)

@Serializable
data class DeleteAccountResultDto(
    @SerialName("deleted") val deleted: Boolean,
    @SerialName("deletedAt") val deletedAt: String? = null,
)

/** Additional stable backend codes for the auth slice (docs/AUTH_CONTRACT.md). */
object AuthErrorCodes {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val EMAIL_ALREADY_IN_USE = "EMAIL_ALREADY_IN_USE"
    const val NICKNAME_ALREADY_EXISTS = "NICKNAME_ALREADY_EXISTS"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val TOKEN_REVOKED = "TOKEN_REVOKED"
    const val ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"
    const val ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE"
    const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val GOOGLE_ID_TOKEN_REQUIRED = "GOOGLE_ID_TOKEN_REQUIRED"
    const val GOOGLE_EMAIL_REQUIRED = "GOOGLE_EMAIL_REQUIRED"
    const val GOOGLE_EMAIL_NOT_VERIFIED = "GOOGLE_EMAIL_NOT_VERIFIED"
    const val GOOGLE_ACCOUNT_LINK_CONFLICT = "GOOGLE_ACCOUNT_LINK_CONFLICT"
    const val SOCIAL_ACCOUNT_CONFLICT = "SOCIAL_ACCOUNT_CONFLICT"
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"
    const val NOT_FOUND = "NOT_FOUND"
}
