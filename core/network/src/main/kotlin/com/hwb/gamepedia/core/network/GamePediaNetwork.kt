package com.hwb.gamepedia.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Network stack for the GamePedia Android client.
 *
 * - Base URL comes from BuildConfig (debug=staging, release=production) and can be
 *   overridden with the `gamepedia.apiBaseUrl` Gradle property (DECISIONS D-0005).
 * - No logging interceptors of any kind: request/response bodies, URLs (which carry
 *   `q`), and headers must never reach logs. Diagnostics go through
 *   [SearchDiagnostics] with privacy-safe fields only.
 * - Retry policy: OkHttp connection-failure retry stays on; there is no automatic
 *   retry of HTTP errors (429 requires backoff per contract). Retries are
 *   user-initiated at the feature layer.
 */
object GamePediaNetwork {

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val REQUEST_TIMEOUT_SECONDS = 15L

    /**
     * Strict contract decoding: not lenient, no value coercion, explicit nulls
     * required by the DTOs. Unknown keys are ignored so additive backend changes do
     * not brick released clients; shape/type violations still fail decoding and
     * surface as MalformedResponse.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }

    fun defaultBaseUrl(): String = BuildConfig.API_BASE_URL

    fun createOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun createSearchApi(
        baseUrl: String = defaultBaseUrl(),
        client: OkHttpClient = createOkHttpClient(),
    ): SearchApi {
        val normalizedBaseUrl = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SearchApi::class.java)
    }

    fun createFailureMapper(): SearchFailureMapper = SearchFailureMapper(json)
}
