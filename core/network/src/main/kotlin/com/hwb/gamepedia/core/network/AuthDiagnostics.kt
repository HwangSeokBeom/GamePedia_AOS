package com.hwb.gamepedia.core.network

import android.util.Log

/**
 * Privacy-safe diagnostics for authentication.
 *
 * NEVER passes through: emails, passwords, access/refresh tokens, Google ID
 * tokens, Authorization headers, request/response bodies. Allowed dimensions:
 * operation name, outcome, stable failure category/backend code, waiter counts.
 * This is the only logging surface for the auth stack; OkHttp body logging stays
 * banned repository-wide.
 */
object AuthDiagnostics {

    private const val TAG = "GamePediaAuth"

    @Volatile
    var sink: (String) -> Unit = { line -> Log.d(TAG, line) }

    fun operationSucceeded(operation: String) {
        sink("op=$operation outcome=ok")
    }

    fun operationFailed(operation: String, diagnosticCategory: String, backendCode: String?) {
        sink("op=$operation outcome=fail error=$diagnosticCategory backend_code=${backendCode ?: "none"}")
    }

    fun refreshFlightStarted(generation: Long) {
        sink("op=refresh_flight event=started generation=$generation")
    }

    fun refreshFlightResolved(generation: Long, outcome: String) {
        sink("op=refresh_flight event=resolved generation=$generation outcome=$outcome")
    }

    fun refreshFlightSuperseded(generation: Long) {
        sink("op=refresh_flight event=superseded generation=$generation")
    }

    fun refreshFlightAbandoned(generation: Long) {
        sink("op=refresh_flight event=abandoned generation=$generation")
    }
}
