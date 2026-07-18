package com.hwb.gamepedia.core.network

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Boundary between the OkHttp layer and the session/refresh coordinator (which
 * lives in :core:auth). Implementations must be thread-safe: OkHttp invokes these
 * from arbitrary worker threads.
 */
interface SessionTokenGateway {
    /** Current access token, or null when unauthenticated. */
    fun currentAccessToken(): String?

    /**
     * Blocks until a usable access token exists or the refresh definitively
     * fails; returns null on failure. [staleAccessToken] is the token the caller
     * already tried; when the stored token already differs, that rotation is
     * reused and NO new refresh is started (this is what keeps a 401 fan-out at
     * one refresh). Implementations single-flight internally.
     */
    fun awaitValidAccessToken(staleAccessToken: String?): String?
}

/** Attaches `Authorization: Bearer` to authenticated-client requests. */
class AuthorizationInterceptor(private val gateway: SessionTokenGateway) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = gateway.currentAccessToken() ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder()
                .header(AUTHORIZATION_HEADER, "Bearer $token")
                .build(),
        )
    }
}

/**
 * 401-driven refresh for the authenticated client.
 *
 * Recursive-refresh safety comes from client separation: the credential `/auth`
 * endpoints (including refresh itself) use the plain client, which has no
 * authenticator, so a 401 from the refresh endpoint can never re-enter here.
 *
 * Per failed request:
 *  - at most ONE retry for a given token state ([MAX_ATTEMPTS] counts the prior
 *    response chain);
 *  - the stale token is handed to the gateway, which either reuses an already
 *    rotated token or joins/starts the single shared refresh — never one refresh
 *    per 401;
 *  - a request is never re-sent with the same already-rejected token;
 *  - if the calling thread is interrupted (call cancelled), the waiter cancels
 *    without failing the shared refresh for other waiters.
 */
class SessionAuthenticator(private val gateway: SessionTokenGateway) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null

        val staleToken = response.request.header(AUTHORIZATION_HEADER)
            ?.removePrefix("Bearer ")
            ?.takeIf { it.isNotBlank() }
            ?: return null // request carried no credentials; a retry cannot help

        val freshToken = gateway.awaitValidAccessToken(staleAccessToken = staleToken) ?: return null
        if (freshToken == staleToken) return null // no rotation happened; do not loop

        return response.request.newBuilder()
            .header(AUTHORIZATION_HEADER, "Bearer $freshToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2 // original + one refresh-driven retry
    }
}

private const val AUTHORIZATION_HEADER = "Authorization"
