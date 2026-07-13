package com.hwb.gamepedia.feature.search

import com.hwb.gamepedia.core.model.Game
import com.hwb.gamepedia.core.model.GameSuggestion
import com.hwb.gamepedia.core.model.SearchFailure
import com.hwb.gamepedia.core.model.SearchMeta
import com.hwb.gamepedia.core.model.SearchOutcome
import com.hwb.gamepedia.core.model.SearchResults
import com.hwb.gamepedia.core.model.SuggestionResults
import com.hwb.gamepedia.feature.search.data.SearchRepository
import kotlinx.coroutines.CancellationException

class FakeSearchRepository : SearchRepository {

    data class SearchCall(val query: String, val forceRefresh: Boolean)

    val searchCalls = mutableListOf<SearchCall>()
    val suggestionCalls = mutableListOf<String>()
    var cancelledSearchCount = 0
    var cancelledSuggestionCount = 0

    var searchBehavior: suspend (String) -> SearchOutcome<SearchResults> =
        { query -> SearchOutcome.Success(successResults(query)) }

    var suggestionsBehavior: suspend (String) -> SearchOutcome<SuggestionResults> =
        { query -> SearchOutcome.Success(suggestionResults(query)) }

    override suspend fun search(query: String, forceRefresh: Boolean): SearchOutcome<SearchResults> {
        searchCalls += SearchCall(query, forceRefresh)
        return try {
            searchBehavior(query)
        } catch (exception: CancellationException) {
            cancelledSearchCount++
            throw exception
        }
    }

    override suspend fun suggestions(query: String): SearchOutcome<SuggestionResults> {
        suggestionCalls += query
        return try {
            suggestionsBehavior(query)
        } catch (exception: CancellationException) {
            cancelledSuggestionCount++
            throw exception
        }
    }

    companion object {
        fun game(id: Int, name: String, genres: List<String> = listOf("Adventure")): Game =
            Game(
                id = id,
                name = name,
                summary = null,
                coverUrl = null,
                genres = genres,
                platforms = listOf("PC (Microsoft Windows)"),
                rating = 80.0,
                aggregatedRating = null,
                totalRating = 80.0,
                releaseDateEpochSeconds = 1700000000L,
            )

        fun meta(query: String, count: Int): SearchMeta =
            SearchMeta(query, query.lowercase(), query.lowercase(), count)

        fun successResults(query: String, games: List<Game> = listOf(game(1, "Game for $query"))): SearchResults =
            SearchResults(query = query, games = games, suggestions = emptyList(), meta = meta(query, games.size))

        fun emptyResults(query: String): SearchResults =
            SearchResults(query = query, games = emptyList(), suggestions = emptyList(), meta = meta(query, 0))

        fun suggestionResults(query: String): SuggestionResults =
            SuggestionResults(
                suggestions = listOf(GameSuggestion(9, "Suggested for $query", null, 75.0)),
                meta = meta(query, 1),
            )

        fun failure(searchFailure: SearchFailure): SearchOutcome.Failure = SearchOutcome.Failure(searchFailure)
    }
}
