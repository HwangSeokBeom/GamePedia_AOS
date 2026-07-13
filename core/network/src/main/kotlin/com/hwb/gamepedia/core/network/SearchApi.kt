package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.network.dto.GameSearchDataDto
import com.hwb.gamepedia.core.network.dto.GameSuggestionsDataDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Trustworthy Search endpoints (GamePediaCoreServer 8790a13).
 *
 * Both endpoints are public: no Authorization header is ever attached (the server
 * ignores it entirely and never emits 401 here). Limits are validated client-side
 * because out-of-range values are a 400, not a clamp.
 */
interface SearchApi {

    @GET("games/search")
    suspend fun searchGames(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): SuccessEnvelopeDto<GameSearchDataDto>

    @GET("games/suggestions")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): SuccessEnvelopeDto<GameSuggestionsDataDto>

    companion object {
        /** `q` must be 1..100 chars after trimming; validate before sending. */
        const val QUERY_MIN_LENGTH = 1
        const val QUERY_MAX_LENGTH = 100

        /** Search: default 20, max 30. No pagination — the list is complete. */
        const val SEARCH_DEFAULT_LIMIT = 20
        const val SEARCH_MAX_LIMIT = 30

        /** Suggestions: default 6, max 8. */
        const val SUGGESTIONS_DEFAULT_LIMIT = 6
        const val SUGGESTIONS_MAX_LIMIT = 8
    }
}
