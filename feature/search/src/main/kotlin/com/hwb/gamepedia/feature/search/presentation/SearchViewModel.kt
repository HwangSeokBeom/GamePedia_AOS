package com.hwb.gamepedia.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hwb.gamepedia.core.model.SearchOutcome
import com.hwb.gamepedia.core.network.SearchApi
import com.hwb.gamepedia.feature.search.data.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State machine for Trustworthy Search.
 *
 * Concurrency rules implemented here (see docs/TRUSTWORTHY_SEARCH_IMPLEMENTATION.md):
 *  - Typing debounces suggestions by [debounceMillis]; the debounce window and the
 *    in-flight request live in one job, so each keystroke (and clear) cancels both.
 *  - Full search: a new submit cancels the previous request; a request-id guard
 *    additionally drops any older response that races past cancellation, so an older
 *    response can never replace a newer query's state.
 *  - Retry re-runs [acceptedQuery] (the last successfully submitted query), never
 *    the live text-field content, and bypasses the repository cache.
 *  - Local genre filters only touch UI state — no backend traffic.
 */
class SearchViewModel(
    private val repository: SearchRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var suggestionsJob: Job? = null
    private var searchJob: Job? = null
    private var searchRequestId = 0L

    /** Last accepted (validated + submitted) query; the retry target. */
    private var acceptedQuery: String? = savedStateHandle[KEY_ACCEPTED_QUERY]

    init {
        val restoredQuery: String? = savedStateHandle[KEY_QUERY]
        if (!restoredQuery.isNullOrEmpty()) {
            _uiState.update { it.copy(query = restoredQuery) }
        }
        // State restoration: re-run the accepted query after process death.
        acceptedQuery?.let { submitSearch(it, forceRefresh = false) }
    }

    fun onQueryChange(newQuery: String) {
        savedStateHandle[KEY_QUERY] = newQuery
        val trimmed = newQuery.trim()
        val tooLong = trimmed.length > SearchApi.QUERY_MAX_LENGTH

        _uiState.update {
            it.copy(
                query = newQuery,
                queryError = if (tooLong) QueryValidationError.TOO_LONG else null,
            )
        }

        // Debounce window and request share one job: cancelling supersedes both.
        suggestionsJob?.cancel()
        if (trimmed.isEmpty() || tooLong) {
            _uiState.update { it.copy(suggestions = SuggestionsUiState.Hidden) }
            return
        }

        suggestionsJob = viewModelScope.launch {
            // While debouncing, keep the previous suggestions state — never fake
            // an empty result during the wait.
            delay(debounceMillis)
            _uiState.update { it.copy(suggestions = SuggestionsUiState.Loading) }
            when (val outcome = repository.suggestions(trimmed)) {
                is SearchOutcome.Success ->
                    _uiState.update { it.copy(suggestions = SuggestionsUiState.Shown(outcome.value.suggestions)) }
                is SearchOutcome.Failure ->
                    // Typeahead failures are non-blocking: hide instead of erroring.
                    _uiState.update { it.copy(suggestions = SuggestionsUiState.Hidden) }
            }
        }
    }

    /** Keyboard search action / explicit submit. */
    fun onSubmit() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.isEmpty() || trimmed.length > SearchApi.QUERY_MAX_LENGTH) return
        dismissSuggestions()
        submitSearch(trimmed, forceRefresh = false)
    }

    fun onSuggestionSelected(suggestionName: String) {
        val trimmed = suggestionName.trim()
        if (trimmed.isEmpty()) return
        savedStateHandle[KEY_QUERY] = trimmed
        _uiState.update { it.copy(query = trimmed, queryError = null) }
        dismissSuggestions()
        submitSearch(trimmed, forceRefresh = false)
    }

    /** Retry re-runs the accepted query (not the live field text), bypassing cache. */
    fun onRetry() {
        val target = acceptedQuery ?: return
        submitSearch(target, forceRefresh = true)
    }

    /** Clear cancels both in-flight pipelines and resets to Idle. */
    fun onClear() {
        suggestionsJob?.cancel()
        searchJob?.cancel()
        searchRequestId++ // invalidate any response that already left the network
        acceptedQuery = null
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle[KEY_ACCEPTED_QUERY] = null
        _uiState.update {
            SearchUiState() // full reset: query, suggestions, results, filter
        }
    }

    /** Local-only presentation filter: must never trigger a backend request. */
    fun onGenreFilterSelected(genreId: String?) {
        _uiState.update { it.copy(selectedGenreId = if (it.selectedGenreId == genreId) null else genreId) }
    }

    /** Back press: dismiss suggestions first; screen pops only when this returns false. */
    fun onBackPressed(): Boolean {
        if (_uiState.value.suggestions != SuggestionsUiState.Hidden) {
            dismissSuggestions()
            return true
        }
        return false
    }

    private fun dismissSuggestions() {
        suggestionsJob?.cancel()
        _uiState.update { it.copy(suggestions = SuggestionsUiState.Hidden) }
    }

    private fun submitSearch(query: String, forceRefresh: Boolean) {
        acceptedQuery = query
        savedStateHandle[KEY_ACCEPTED_QUERY] = query

        val requestId = ++searchRequestId
        searchJob?.cancel()
        _uiState.update { it.copy(results = ResultsUiState.Loading(query), selectedGenreId = null) }

        searchJob = viewModelScope.launch {
            val outcome = repository.search(query, forceRefresh = forceRefresh)
            if (requestId != searchRequestId) return@launch // superseded: never overwrite newer state
            when (outcome) {
                is SearchOutcome.Success -> {
                    val games = outcome.value.games
                    _uiState.update { state ->
                        state.copy(
                            results = if (games.isEmpty()) {
                                ResultsUiState.Empty(query)
                            } else {
                                ResultsUiState.Success(
                                    query = query,
                                    games = games,
                                    genreFilters = games
                                        .flatMap { it.genres }
                                        .distinct()
                                        .sorted()
                                        .map { GenreFilter(id = genreFilterId(it), label = it) },
                                )
                            },
                        )
                    }
                }
                is SearchOutcome.Failure ->
                    _uiState.update { it.copy(results = ResultsUiState.Error(query, outcome.failure)) }
            }
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
        private const val KEY_QUERY = "search_query"
        private const val KEY_ACCEPTED_QUERY = "search_accepted_query"
    }
}
