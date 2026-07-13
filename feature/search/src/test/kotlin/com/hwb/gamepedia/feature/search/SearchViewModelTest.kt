package com.hwb.gamepedia.feature.search

import androidx.lifecycle.SavedStateHandle
import com.hwb.gamepedia.core.model.SearchFailure
import com.hwb.gamepedia.core.model.SearchOutcome
import com.hwb.gamepedia.feature.search.presentation.QueryValidationError
import com.hwb.gamepedia.feature.search.presentation.ResultsUiState
import com.hwb.gamepedia.feature.search.presentation.SearchViewModel
import com.hwb.gamepedia.feature.search.presentation.SuggestionsUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DEBOUNCE = 300L

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSearchRepository()

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        SearchViewModel(repository, savedStateHandle, debounceMillis = DEBOUNCE)

    // 1. Query debounce
    @Test
    fun `suggestions are debounced and only the settled query is requested`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()

            vm.onQueryChange("z")
            advanceTimeBy(100)
            vm.onQueryChange("ze")
            advanceTimeBy(100)
            vm.onQueryChange("zelda")
            // Mid-debounce: no request yet, and no fake empty suggestion state.
            assertEquals(emptyList<String>(), repository.suggestionCalls)
            assertEquals(SuggestionsUiState.Hidden, vm.uiState.value.suggestions)

            advanceUntilIdle()

            assertEquals(listOf("zelda"), repository.suggestionCalls)
            assertTrue(vm.uiState.value.suggestions is SuggestionsUiState.Shown)
        }

    // 2. Latest query wins
    @Test
    fun `an older search response never replaces a newer query`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = { query ->
                delay(if (query == "alpha") 500 else 50) // older request resolves later
                SearchOutcome.Success(FakeSearchRepository.successResults(query))
            }
            val vm = viewModel()

            vm.onQueryChange("alpha")
            vm.onSubmit()
            advanceTimeBy(10)
            vm.onQueryChange("beta")
            vm.onSubmit()
            advanceUntilIdle()

            val results = vm.uiState.value.results as ResultsUiState.Success
            assertEquals("beta", results.query)
        }

    // 3. Superseded request cancellation
    @Test
    fun `submitting a new query cancels the in-flight search request`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = { query ->
                delay(if (query == "alpha") 10_000 else 10)
                SearchOutcome.Success(FakeSearchRepository.successResults(query))
            }
            val vm = viewModel()

            vm.onQueryChange("alpha")
            vm.onSubmit()
            advanceTimeBy(50)
            vm.onQueryChange("beta")
            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(1, repository.cancelledSearchCount)
            assertEquals("beta", (vm.uiState.value.results as ResultsUiState.Success).query)
        }

    // 6. Empty-result state
    @Test
    fun `zero games renders Empty state not Error`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = { query ->
                SearchOutcome.Success(FakeSearchRepository.emptyResults(query))
            }
            val vm = viewModel()

            vm.onQueryChange("nothing here")
            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(ResultsUiState.Empty("nothing here"), vm.uiState.value.results)
        }

    // 14. Loading state (and: loading must not render as an empty result)
    @Test
    fun `in-flight search renders Loading not Empty`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = { query ->
                delay(5_000)
                SearchOutcome.Success(FakeSearchRepository.successResults(query))
            }
            val vm = viewModel()

            vm.onQueryChange("slow")
            vm.onSubmit()
            advanceTimeBy(1_000)

            assertEquals(ResultsUiState.Loading("slow"), vm.uiState.value.results)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.results is ResultsUiState.Success)
        }

    // 7. Invalid query mapping (client-side prevention)
    @Test
    fun `over-long query sets validation error and sends nothing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()

            vm.onQueryChange("a".repeat(101))
            advanceUntilIdle()
            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(QueryValidationError.TOO_LONG, vm.uiState.value.queryError)
            assertEquals(emptyList<String>(), repository.suggestionCalls)
            assertEquals(emptyList<FakeSearchRepository.SearchCall>(), repository.searchCalls)
        }

    // 7b. Backend INVALID_SEARCH_QUERY still maps to a non-retryable error state
    @Test
    fun `backend invalid-query failure renders non-retryable Error`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = {
                FakeSearchRepository.failure(SearchFailure.InvalidQuery("INVALID_SEARCH_QUERY"))
            }
            val vm = viewModel()

            vm.onQueryChange("weird")
            vm.onSubmit()
            advanceUntilIdle()

            val error = vm.uiState.value.results as ResultsUiState.Error
            assertEquals(false, error.failure.retryable)
        }

    // 9. Provider error mapping renders retryable Error
    @Test
    fun `provider failure renders retryable Error state`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = {
                FakeSearchRepository.failure(SearchFailure.ProviderUnavailable("IGDB_UPSTREAM_ERROR"))
            }
            val vm = viewModel()

            vm.onQueryChange("zelda")
            vm.onSubmit()
            advanceUntilIdle()

            val error = vm.uiState.value.results as ResultsUiState.Error
            assertTrue(error.failure.retryable)
            assertEquals("IGDB_UPSTREAM_ERROR", error.failure.backendCode)
        }

    // 10. Retry behavior: reruns the accepted query, not stale field text
    @Test
    fun `retry reruns the accepted query with forceRefresh even after editing the field`() =
        runTest(mainDispatcherRule.dispatcher) {
            var fail = true
            repository.searchBehavior = { query ->
                if (fail) {
                    FakeSearchRepository.failure(SearchFailure.Timeout)
                } else {
                    SearchOutcome.Success(FakeSearchRepository.successResults(query))
                }
            }
            val vm = viewModel()

            vm.onQueryChange("zelda")
            vm.onSubmit()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.results is ResultsUiState.Error)

            // User edits the field but does not submit — retry must ignore this text.
            vm.onQueryChange("zeldax")
            fail = false
            vm.onRetry()
            advanceUntilIdle()

            val lastCall = repository.searchCalls.last()
            assertEquals("zelda", lastCall.query)
            assertTrue(lastCall.forceRefresh)
            assertEquals("zelda", (vm.uiState.value.results as ResultsUiState.Success).query)
        }

    // 11. Clear cancels both pipelines and resets state
    @Test
    fun `clear cancels in-flight search and suggestions and resets to idle`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = { query ->
                delay(10_000)
                SearchOutcome.Success(FakeSearchRepository.successResults(query))
            }
            repository.suggestionsBehavior = { query ->
                delay(10_000)
                SearchOutcome.Success(FakeSearchRepository.suggestionResults(query))
            }
            val vm = viewModel()

            vm.onQueryChange("zelda")
            vm.onSubmit()          // search in flight
            vm.onQueryChange("zeldam") // restart suggestions debounce
            advanceTimeBy(DEBOUNCE + 50) // suggestions now in flight
            vm.onClear()
            advanceUntilIdle()

            assertEquals(1, repository.cancelledSearchCount)
            assertEquals(1, repository.cancelledSuggestionCount)
            assertEquals("", vm.uiState.value.query)
            assertEquals(ResultsUiState.Idle, vm.uiState.value.results)
            assertEquals(SuggestionsUiState.Hidden, vm.uiState.value.suggestions)
        }

    // 12. Local filter change without redundant request
    @Test
    fun `genre filter changes never trigger a backend request`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.searchBehavior = { query ->
                SearchOutcome.Success(
                    FakeSearchRepository.successResults(
                        query,
                        games = listOf(
                            FakeSearchRepository.game(1, "Adventure Game", listOf("Adventure")),
                            FakeSearchRepository.game(2, "Racing Game", listOf("Racing")),
                        ),
                    ),
                )
            }
            val vm = viewModel()

            vm.onQueryChange("games")
            vm.onSubmit()
            advanceUntilIdle()
            val callsAfterSearch = repository.searchCalls.size

            vm.onGenreFilterSelected("racing")
            advanceUntilIdle()

            assertEquals(callsAfterSearch, repository.searchCalls.size)
            assertEquals(listOf(2), vm.uiState.value.visibleGames.map { it.id })

            // Selecting the same filter again toggles it off — still no request.
            vm.onGenreFilterSelected("racing")
            assertEquals(callsAfterSearch, repository.searchCalls.size)
            assertEquals(listOf(1, 2), vm.uiState.value.visibleGames.map { it.id })
        }

    // State restoration: accepted query re-runs after process death
    @Test
    fun `restored accepted query is searched again on init`() =
        runTest(mainDispatcherRule.dispatcher) {
            val handle = SavedStateHandle(
                mapOf("search_query" to "zelda", "search_accepted_query" to "zelda"),
            )
            val vm = viewModel(handle)
            advanceUntilIdle()

            assertEquals(listOf(FakeSearchRepository.SearchCall("zelda", false)), repository.searchCalls)
            assertEquals("zelda", vm.uiState.value.query)
            assertTrue(vm.uiState.value.results is ResultsUiState.Success)
        }

    // Suggestion selection triggers a full search for the suggestion
    @Test
    fun `selecting a suggestion submits it as the accepted query`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()

            vm.onSuggestionSelected("Breath of the Wild")
            advanceUntilIdle()

            assertEquals("Breath of the Wild", repository.searchCalls.single().query)
            assertEquals("Breath of the Wild", vm.uiState.value.query)
            assertEquals(SuggestionsUiState.Hidden, vm.uiState.value.suggestions)
        }
}
