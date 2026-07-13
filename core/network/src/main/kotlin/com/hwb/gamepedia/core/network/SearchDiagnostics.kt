package com.hwb.gamepedia.core.network

import android.util.Log

/**
 * Privacy-safe diagnostics for search traffic.
 *
 * Backend parity rule (contract 8790a13): never record the raw query, a URL
 * containing `q`, headers, tokens, or provider payloads. Allowed dimensions are the
 * request category, query length, result count, latency, and the stable error code.
 * OkHttp body logging must never be enabled; this is the only logging surface.
 */
object SearchDiagnostics {

    const val CATEGORY_SEARCH = "search"
    const val CATEGORY_SUGGESTIONS = "suggestions"

    private const val TAG = "GamePediaSearch"

    @Volatile
    var sink: (String) -> Unit = { line -> Log.d(TAG, line) }

    fun requestSucceeded(category: String, queryLength: Int, resultCount: Int, latencyMillis: Long, fromCache: Boolean) {
        sink(format(category, queryLength, "ok", "result_count=$resultCount latency_ms=$latencyMillis cache=${if (fromCache) "hit" else "miss"}"))
    }

    fun requestFailed(category: String, queryLength: Int, diagnosticCategory: String, backendCode: String?, latencyMillis: Long) {
        sink(format(category, queryLength, "fail", "error=$diagnosticCategory backend_code=${backendCode ?: "none"} latency_ms=$latencyMillis"))
    }

    private fun format(category: String, queryLength: Int, outcome: String, detail: String): String =
        "category=$category outcome=$outcome q_len=$queryLength $detail"
}
