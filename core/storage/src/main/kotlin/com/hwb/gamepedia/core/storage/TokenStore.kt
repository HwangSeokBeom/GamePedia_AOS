package com.hwb.gamepedia.core.storage

import com.hwb.gamepedia.core.model.AuthTokens

/**
 * Persistent credential storage boundary.
 *
 * Contract:
 *  - [save] replaces BOTH tokens atomically: readers can never observe a new
 *    access token paired with an old refresh token or vice versa.
 *  - [clear] removes both tokens atomically.
 *  - Implementations must never log, toString(), or otherwise expose raw token
 *    material ([AuthTokens] itself redacts toString()).
 */
interface TokenStore {
    fun tokens(): AuthTokens?
    fun save(tokens: AuthTokens)
    fun clear()
}

/**
 * Deterministic in-memory store for tests and previews. Thread-safe: the token
 * pair is swapped as a single reference, preserving the atomic-pair contract.
 */
class InMemoryTokenStore(initial: AuthTokens? = null) : TokenStore {
    @Volatile
    private var stored: AuthTokens? = initial

    override fun tokens(): AuthTokens? = stored

    override fun save(tokens: AuthTokens) {
        stored = tokens
    }

    override fun clear() {
        stored = null
    }
}
