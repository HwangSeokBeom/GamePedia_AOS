package com.hwb.gamepedia.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.hwb.gamepedia.core.model.AuthTokens
import com.hwb.gamepedia.core.model.AuthUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Production credential storage: Keystore-encrypted values in a private
 * SharedPreferences file.
 *
 * Atomicity: the whole token pair is serialized as ONE encrypted record under ONE
 * preference key, and commits are synchronous (`commit()`), so a reader can never
 * observe a half-updated pair and a process death cannot split an update.
 *
 * Corrupt or undecryptable records (e.g. after a Keystore key invalidation) are
 * treated as absent — the session simply requires a fresh login. Nothing here
 * logs, throws, or toString()s raw credential material.
 */
class SecureSessionStorage(
    context: Context,
    prefsName: String = "gamepedia_session",
    keyAlias: String = "gamepedia_session_key",
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(keyAlias)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class PersistedTokens(
        @SerialName("accessToken") val accessToken: String,
        @SerialName("refreshToken") val refreshToken: String,
    ) {
        override fun toString(): String = "PersistedTokens(<redacted>)"
    }

    @Serializable
    private data class PersistedUser(
        @SerialName("id") val id: String,
        @SerialName("email") val email: String,
        @SerialName("nickname") val nickname: String,
        @SerialName("profileImageUrl") val profileImageUrl: String?,
        @SerialName("status") val status: String,
        @SerialName("createdAtIso") val createdAtIso: String?,
        @SerialName("updatedAtIso") val updatedAtIso: String?,
    )

    val tokenStore: TokenStore = object : TokenStore {
        override fun tokens(): AuthTokens? =
            readRecord(KEY_TOKENS)?.let { raw ->
                runCatching {
                    val decoded = json.decodeFromString<PersistedTokens>(raw)
                    AuthTokens(decoded.accessToken, decoded.refreshToken)
                }.getOrNull()
            }

        override fun save(tokens: AuthTokens) {
            writeRecord(
                KEY_TOKENS,
                json.encodeToString(PersistedTokens(tokens.accessToken, tokens.refreshToken)),
            )
        }

        override fun clear() {
            @Suppress("ApplySharedPref")
            prefs.edit().remove(KEY_TOKENS).commit()
        }
    }

    val userSessionStore: UserSessionStore = object : UserSessionStore {
        override fun user(): AuthUser? =
            readRecord(KEY_USER)?.let { raw ->
                runCatching {
                    val decoded = json.decodeFromString<PersistedUser>(raw)
                    AuthUser(
                        id = decoded.id,
                        email = decoded.email,
                        nickname = decoded.nickname,
                        profileImageUrl = decoded.profileImageUrl,
                        status = decoded.status,
                        createdAtIso = decoded.createdAtIso,
                        updatedAtIso = decoded.updatedAtIso,
                    )
                }.getOrNull()
            }

        override fun save(user: AuthUser) {
            writeRecord(
                KEY_USER,
                json.encodeToString(
                    PersistedUser(
                        id = user.id,
                        email = user.email,
                        nickname = user.nickname,
                        profileImageUrl = user.profileImageUrl,
                        status = user.status,
                        createdAtIso = user.createdAtIso,
                        updatedAtIso = user.updatedAtIso,
                    ),
                ),
            )
        }

        override fun clear() {
            @Suppress("ApplySharedPref")
            prefs.edit().remove(KEY_USER).commit()
        }
    }

    private fun readRecord(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            String(cipher.decrypt(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun writeRecord(key: String, value: String) {
        val encrypted = Base64.encodeToString(
            cipher.encrypt(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        @Suppress("ApplySharedPref")
        prefs.edit().putString(key, encrypted).commit()
    }

    private companion object {
        const val KEY_TOKENS = "auth_tokens_v1"
        const val KEY_USER = "auth_user_v1"
    }
}
