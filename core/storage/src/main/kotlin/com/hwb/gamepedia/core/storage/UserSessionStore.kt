package com.hwb.gamepedia.core.storage

import com.hwb.gamepedia.core.model.AuthUser

/** Persisted authenticated-user profile (no tokens). */
interface UserSessionStore {
    fun user(): AuthUser?
    fun save(user: AuthUser)
    fun clear()
}

/** Deterministic in-memory implementation for tests. */
class InMemoryUserSessionStore(initial: AuthUser? = null) : UserSessionStore {
    @Volatile
    private var stored: AuthUser? = initial

    override fun user(): AuthUser? = stored

    override fun save(user: AuthUser) {
        stored = user
    }

    override fun clear() {
        stored = null
    }
}
