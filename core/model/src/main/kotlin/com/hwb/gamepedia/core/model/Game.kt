package com.hwb.gamepedia.core.model

/**
 * Canonical search result item. Mirrors the backend `GameListItem` schema
 * (GamePediaCoreServer 8790a13, openapi/cross-platform.openapi.json): every key is
 * always present; nullable fields stay nullable instead of hiding server nulls
 * behind defaults.
 */
data class Game(
    val id: Int,
    val name: String?,
    val summary: String?,
    val coverUrl: String?,
    val genres: List<String>,
    val platforms: List<String>,
    val rating: Double?,
    val aggregatedRating: Double?,
    val totalRating: Double?,
    val releaseDateEpochSeconds: Long?,
)

/** Mirrors the backend `GameSuggestionItem` schema. */
data class GameSuggestion(
    val id: Int,
    val name: String?,
    val coverUrl: String?,
    val rating: Double?,
)

/** Mirrors the backend `SearchMeta` schema. */
data class SearchMeta(
    val originalQuery: String,
    val normalizedQuery: String,
    val effectiveQuery: String,
    val resultCount: Int,
)

/** Full-search payload bound to the canonical `games` field (never `results`). */
data class SearchResults(
    val query: String,
    val games: List<Game>,
    val suggestions: List<GameSuggestion>,
    val meta: SearchMeta,
)

data class SuggestionResults(
    val suggestions: List<GameSuggestion>,
    val meta: SearchMeta,
)
