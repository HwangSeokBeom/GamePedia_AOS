package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthOutcome
import com.hwb.gamepedia.core.model.AuthSession
import com.hwb.gamepedia.core.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication and session-management boundary.
 *
 * Concurrency guarantees (see docs/AUTH_ARCHITECTURE.md):
 *  - [refresh] is single-flight per session generation: concurrent callers share
 *    one provider request and receive the same result; the rotating refresh token
 *    is read once per flight and never submitted by two overlapping requests.
 *  - A newer [login]/[signUp]/[loginWithGoogle] supersedes an in-flight refresh;
 *    late refresh results neither overwrite nor clear the newer session.
 *  - [logout]/[deleteAccount] advance the session generation; a refresh completing
 *    afterwards cannot restore credentials.
 *  - Cancelling one refresh waiter never cancels the shared work while other
 *    waiters remain; cancelling the last waiter cancels the provider request.
 */
interface AuthRepository {

    /** App-level session state. Never exposes tokens. */
    val sessionState: StateFlow<SessionState>

    suspend fun login(email: String, password: String): AuthOutcome<AuthSession>

    suspend fun signUp(email: String, password: String, nickname: String): AuthOutcome<AuthSession>

    suspend fun loginWithGoogle(idToken: String): AuthOutcome<AuthSession>

    /** Joins or starts the shared single-flight refresh. */
    suspend fun refresh(): AuthOutcome<AuthSession>

    /**
     * Invalidates the local session immediately (generation advance + store
     * clear) and revokes the refresh token server-side best-effort.
     */
    fun logout()

    /**
     * Deletes the account server-side, then invalidates the local session.
     * Requires an authenticated session.
     */
    suspend fun deleteAccount(): AuthOutcome<Unit>
}
