package com.hwb.gamepedia.feature.auth

/**
 * Boundary for obtaining a Google ID token from the platform sign-in UI
 * (Credential Manager / Google Identity Services).
 *
 * The real implementation requires OAuth client configuration that lives outside
 * source control (a Web client ID for the backend audience plus an Android client
 * registered with this app's package name and signing SHA-1) — see
 * docs/AUTH_CONTRACT.md "Google configuration". Until that exists, the app wires
 * [Unavailable], which surfaces a clear not-configured failure instead of a
 * half-working flow.
 */
interface GoogleIdTokenProvider {

    sealed interface Result {
        data class Success(val idToken: String) : Result {
            override fun toString(): String = "Success(idToken=<redacted>)"
        }

        data object NotConfigured : Result
        data object Cancelled : Result
        data class Failed(val diagnosticCategory: String) : Result
    }

    suspend fun acquireIdToken(): Result

    object Unavailable : GoogleIdTokenProvider {
        override suspend fun acquireIdToken(): Result = Result.NotConfigured
    }
}
