package com.hwb.gamepedia.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Trustworthy Search contract.
 *
 * Source of truth: GamePediaCoreServer commit 8790a13,
 * openapi/cross-platform.openapi.json. Field names, presence, and nullability are
 * copied verbatim — do not add client-only fields or defaults that mask server nulls.
 */

/** Shared success envelope: `{ "success": true, "data": ... }`. */
@Serializable
data class SuccessEnvelopeDto<T>(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: T,
)

/** `GET /games/search` → `data`. */
@Serializable
data class GameSearchDataDto(
    @SerialName("query") val query: String,
    /** Canonical list — Android binds to this field. */
    @SerialName("games") val games: List<GameListItemDto>,
    /** iOS 2.0 compatibility duplicate of `games`. Decoded for strictness, never read. */
    @SerialName("results") val results: List<GameListItemDto>,
    @SerialName("suggestions") val suggestions: List<GameSuggestionItemDto>,
    @SerialName("meta") val meta: SearchMetaDto,
)

/** `GET /games/suggestions` → `data`. */
@Serializable
data class GameSuggestionsDataDto(
    @SerialName("suggestions") val suggestions: List<GameSuggestionItemDto>,
    @SerialName("meta") val meta: SearchMetaDto,
)

@Serializable
data class GameListItemDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String?,
    @SerialName("summary") val summary: String?,
    @SerialName("coverUrl") val coverUrl: String?,
    @SerialName("genres") val genres: List<String>,
    @SerialName("platforms") val platforms: List<String>,
    @SerialName("rating") val rating: Double?,
    @SerialName("aggregatedRating") val aggregatedRating: Double?,
    @SerialName("totalRating") val totalRating: Double?,
    @SerialName("releaseDate") val releaseDate: Long?,
)

@Serializable
data class GameSuggestionItemDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String?,
    @SerialName("coverUrl") val coverUrl: String?,
    @SerialName("rating") val rating: Double?,
)

@Serializable
data class SearchMetaDto(
    @SerialName("originalQuery") val originalQuery: String,
    @SerialName("normalizedQuery") val normalizedQuery: String,
    @SerialName("effectiveQuery") val effectiveQuery: String,
    @SerialName("resultCount") val resultCount: Int,
)
