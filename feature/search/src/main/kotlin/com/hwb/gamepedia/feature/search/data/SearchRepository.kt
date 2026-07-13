package com.hwb.gamepedia.feature.search.data

import com.hwb.gamepedia.core.common.TimeSource
import com.hwb.gamepedia.core.model.Game
import com.hwb.gamepedia.core.model.GameSuggestion
import com.hwb.gamepedia.core.model.SearchMeta
import com.hwb.gamepedia.core.model.SearchOutcome
import com.hwb.gamepedia.core.model.SearchResults
import com.hwb.gamepedia.core.model.SuggestionResults
import com.hwb.gamepedia.core.network.SearchApi
import com.hwb.gamepedia.core.network.SearchDiagnostics
import com.hwb.gamepedia.core.network.SearchFailureMapper
import com.hwb.gamepedia.core.network.dto.GameListItemDto
import com.hwb.gamepedia.core.network.dto.GameSuggestionItemDto
import com.hwb.gamepedia.core.network.dto.SearchMetaDto

interface SearchRepository {
    /** Full search. [forceRefresh] bypasses the cache read (used by retry). */
    suspend fun search(query: String, forceRefresh: Boolean = false): SearchOutcome<SearchResults>

    suspend fun suggestions(query: String): SearchOutcome<SuggestionResults>
}

/**
 * Repository over the public search endpoints with a short-lived in-memory cache.
 *
 * Cache policy (contract 8790a13 "Caching expectations"): in-memory only, LRU keyed
 * by trimmed-lowercased query + limit, TTL 10 minutes, never persisted. Responses
 * carry no cache headers, so nothing is cached at the HTTP layer. Retry passes
 * forceRefresh=true so a stale entry can always be replaced. Raw queries live only
 * inside the cache map — they are never logged (see [SearchDiagnostics]).
 */
class DefaultSearchRepository(
    private val api: SearchApi,
    private val failureMapper: SearchFailureMapper,
    private val timeSource: TimeSource = TimeSource.SYSTEM,
) : SearchRepository {

    private data class CacheEntry<T>(val value: T, val storedAtMillis: Long)

    private val lock = Any()
    private val searchCache = LruMap<String, CacheEntry<SearchResults>>(MAX_CACHE_ENTRIES)
    private val suggestionCache = LruMap<String, CacheEntry<SuggestionResults>>(MAX_CACHE_ENTRIES)

    override suspend fun search(query: String, forceRefresh: Boolean): SearchOutcome<SearchResults> {
        val key = cacheKey(query, SearchApi.SEARCH_DEFAULT_LIMIT)
        if (!forceRefresh) {
            readCache(searchCache, key)?.let { cached ->
                SearchDiagnostics.requestSucceeded(
                    category = SearchDiagnostics.CATEGORY_SEARCH,
                    queryLength = query.length,
                    resultCount = cached.games.size,
                    latencyMillis = 0,
                    fromCache = true,
                )
                return SearchOutcome.Success(cached, fromCache = true)
            }
        }

        val startedAt = timeSource.nowMillis()
        return try {
            val envelope = api.searchGames(query = query, limit = SearchApi.SEARCH_DEFAULT_LIMIT)
            val results = envelope.data.toDomain()
            synchronized(lock) { searchCache[key] = CacheEntry(results, timeSource.nowMillis()) }
            SearchDiagnostics.requestSucceeded(
                category = SearchDiagnostics.CATEGORY_SEARCH,
                queryLength = query.length,
                resultCount = results.games.size,
                latencyMillis = timeSource.nowMillis() - startedAt,
                fromCache = false,
            )
            SearchOutcome.Success(results)
        } catch (throwable: Throwable) {
            val failure = failureMapper.map(throwable)
            SearchDiagnostics.requestFailed(
                category = SearchDiagnostics.CATEGORY_SEARCH,
                queryLength = query.length,
                diagnosticCategory = failure.diagnosticCategory,
                backendCode = failure.backendCode,
                latencyMillis = timeSource.nowMillis() - startedAt,
            )
            SearchOutcome.Failure(failure)
        }
    }

    override suspend fun suggestions(query: String): SearchOutcome<SuggestionResults> {
        val key = cacheKey(query, SearchApi.SUGGESTIONS_DEFAULT_LIMIT)
        readCache(suggestionCache, key)?.let { cached ->
            return SearchOutcome.Success(cached, fromCache = true)
        }

        val startedAt = timeSource.nowMillis()
        return try {
            val envelope = api.getSuggestions(query = query, limit = SearchApi.SUGGESTIONS_DEFAULT_LIMIT)
            val results = envelope.data.let { data ->
                SuggestionResults(
                    suggestions = data.suggestions.map { it.toDomain() },
                    meta = data.meta.toDomain(),
                )
            }
            synchronized(lock) { suggestionCache[key] = CacheEntry(results, timeSource.nowMillis()) }
            SearchDiagnostics.requestSucceeded(
                category = SearchDiagnostics.CATEGORY_SUGGESTIONS,
                queryLength = query.length,
                resultCount = results.suggestions.size,
                latencyMillis = timeSource.nowMillis() - startedAt,
                fromCache = false,
            )
            SearchOutcome.Success(results)
        } catch (throwable: Throwable) {
            val failure = failureMapper.map(throwable)
            SearchDiagnostics.requestFailed(
                category = SearchDiagnostics.CATEGORY_SUGGESTIONS,
                queryLength = query.length,
                diagnosticCategory = failure.diagnosticCategory,
                backendCode = failure.backendCode,
                latencyMillis = timeSource.nowMillis() - startedAt,
            )
            SearchOutcome.Failure(failure)
        }
    }

    private fun <T> readCache(cache: LruMap<String, CacheEntry<T>>, key: String): T? =
        synchronized(lock) {
            val entry = cache[key] ?: return null
            val fresh = timeSource.nowMillis() - entry.storedAtMillis <= CACHE_TTL_MILLIS
            if (fresh) entry.value else { cache.remove(key); null }
        }

    private fun cacheKey(query: String, limit: Int): String = "${query.trim().lowercase()}|$limit"

    private class LruMap<K, V>(private val maxEntries: Int) :
        LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxEntries
    }

    companion object {
        const val CACHE_TTL_MILLIS = 10L * 60L * 1000L
        const val MAX_CACHE_ENTRIES = 32
    }
}

private fun com.hwb.gamepedia.core.network.dto.GameSearchDataDto.toDomain(): SearchResults =
    SearchResults(
        query = query,
        // Canonical field is `games`; `results` is an iOS 2.0 alias and is ignored.
        games = games.map { it.toDomain() },
        suggestions = suggestions.map { it.toDomain() },
        meta = meta.toDomain(),
    )

private fun GameListItemDto.toDomain(): Game =
    Game(
        id = id,
        name = name,
        summary = summary,
        coverUrl = coverUrl,
        genres = genres,
        platforms = platforms,
        rating = rating,
        aggregatedRating = aggregatedRating,
        totalRating = totalRating,
        releaseDateEpochSeconds = releaseDate,
    )

private fun GameSuggestionItemDto.toDomain(): GameSuggestion =
    GameSuggestion(id = id, name = name, coverUrl = coverUrl, rating = rating)

private fun SearchMetaDto.toDomain(): SearchMeta =
    SearchMeta(
        originalQuery = originalQuery,
        normalizedQuery = normalizedQuery,
        effectiveQuery = effectiveQuery,
        resultCount = resultCount,
    )
