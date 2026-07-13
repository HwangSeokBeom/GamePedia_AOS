package com.hwb.gamepedia.feature.search.presentation

import com.hwb.gamepedia.core.model.Game
import com.hwb.gamepedia.core.model.GameSuggestion
import com.hwb.gamepedia.core.model.SearchFailure

/**
 * Single source of UI truth for the search screen.
 *
 * Suggestions and full results deliberately have distinct state ownership
 * ([suggestions] vs [results]): a typeahead response must never disturb the results
 * pane and vice versa.
 */
data class SearchUiState(
    val query: String = "",
    val queryError: QueryValidationError? = null,
    val suggestions: SuggestionsUiState = SuggestionsUiState.Hidden,
    val results: ResultsUiState = ResultsUiState.Idle,
    /** Stable local-only filter id (normalized genre name); null = no filter. */
    val selectedGenreId: String? = null,
) {
    /** Genre filtering is purely presentational — it never triggers a request. */
    val visibleGames: List<Game>
        get() {
            val success = results as? ResultsUiState.Success ?: return emptyList()
            val genreId = selectedGenreId ?: return success.games
            return success.games.filter { game -> game.genres.any { genreFilterId(it) == genreId } }
        }
}

enum class QueryValidationError {
    /** Query exceeds the 100-character contract limit; request must not be sent. */
    TOO_LONG,
}

sealed interface SuggestionsUiState {
    data object Hidden : SuggestionsUiState
    data object Loading : SuggestionsUiState
    data class Shown(val suggestions: List<GameSuggestion>) : SuggestionsUiState
}

sealed interface ResultsUiState {
    /** Nothing searched yet (or cleared). Distinct from Empty by contract. */
    data object Idle : ResultsUiState

    /** A search is in flight. Must never render as an empty result. */
    data class Loading(val query: String) : ResultsUiState

    data class Success(
        val query: String,
        val games: List<Game>,
        /** Stable filter ids paired with display labels, derived from results. */
        val genreFilters: List<GenreFilter>,
    ) : ResultsUiState

    /** HTTP 200 with zero games — a normal outcome, not an error. */
    data class Empty(val query: String) : ResultsUiState

    data class Error(val query: String, val failure: SearchFailure) : ResultsUiState
}

data class GenreFilter(val id: String, val label: String)

/** Stable, locale-independent identifier for a genre chip. */
fun genreFilterId(genreName: String): String = genreName.trim().lowercase().replace(' ', '-')
