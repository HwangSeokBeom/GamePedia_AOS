package com.hwb.gamepedia.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hwb.gamepedia.core.designsystem.theme.GamePediaTheme
import com.hwb.gamepedia.core.model.Game
import com.hwb.gamepedia.core.model.SearchFailure
import com.hwb.gamepedia.feature.search.presentation.GenreFilter
import com.hwb.gamepedia.feature.search.presentation.ResultsUiState
import com.hwb.gamepedia.feature.search.presentation.SearchUiState
import com.hwb.gamepedia.feature.search.ui.SearchScreenContent
import com.hwb.gamepedia.feature.search.ui.SearchScreenTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * State-rendering coverage: each ResultsUiState renders its dedicated surface
 * (loading / results / empty / error) and never a misleading substitute.
 */
@RunWith(AndroidJUnit4::class)
class SearchStateRenderingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun game(id: Int, name: String): Game =
        Game(
            id = id,
            name = name,
            summary = null,
            coverUrl = null,
            genres = listOf("Adventure"),
            platforms = emptyList(),
            rating = 88.0,
            aggregatedRating = null,
            totalRating = 88.0,
            releaseDateEpochSeconds = 1700000000L,
        )

    private fun setContent(state: SearchUiState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            GamePediaTheme {
                SearchScreenContent(
                    state = state,
                    onQueryChange = {},
                    onSubmit = {},
                    onClear = {},
                    onRetry = onRetry,
                    onSuggestionSelected = {},
                    onGenreFilterSelected = {},
                )
            }
        }
    }

    // 14. Loading state rendering
    @Test
    fun loadingState_showsSpinner_notEmptyState() {
        setContent(SearchUiState(query = "zelda", results = ResultsUiState.Loading("zelda")))

        composeRule.onNodeWithTag(SearchScreenTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchScreenTestTags.EMPTY).assertDoesNotExist()
        composeRule.onNodeWithTag(SearchScreenTestTags.ERROR).assertDoesNotExist()
    }

    // 15. Result state rendering
    @Test
    fun successState_rendersResultList() {
        setContent(
            SearchUiState(
                query = "zelda",
                results = ResultsUiState.Success(
                    query = "zelda",
                    games = listOf(game(1, "Breath of the Wild"), game(2, "Tears of the Kingdom")),
                    genreFilters = listOf(GenreFilter("adventure", "Adventure")),
                ),
            ),
        )

        composeRule.onNodeWithTag(SearchScreenTestTags.RESULT_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Breath of the Wild").assertIsDisplayed()
        composeRule.onNodeWithText("Tears of the Kingdom").assertIsDisplayed()
    }

    // 16. Empty state rendering
    @Test
    fun emptyState_rendersNoResultsMessage() {
        setContent(SearchUiState(query = "qqq", results = ResultsUiState.Empty("qqq")))

        composeRule.onNodeWithTag(SearchScreenTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchScreenTestTags.RESULT_LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(SearchScreenTestTags.ERROR).assertDoesNotExist()
    }

    // 17. Error state rendering (+ retry wiring)
    @Test
    fun errorState_rendersRetryableErrorAndInvokesRetry() {
        var retried = false
        setContent(
            SearchUiState(
                query = "zelda",
                results = ResultsUiState.Error("zelda", SearchFailure.ProviderUnavailable("IGDB_UPSTREAM_ERROR")),
            ),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithTag(SearchScreenTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun nonRetryableError_hidesRetryButton() {
        setContent(
            SearchUiState(
                query = "zelda",
                results = ResultsUiState.Error("zelda", SearchFailure.ServerError("INTERNAL_SERVER_ERROR")),
            ),
        )

        composeRule.onNodeWithTag(SearchScreenTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun idleState_rendersSearchPrompt() {
        setContent(SearchUiState())

        composeRule.onNodeWithTag(SearchScreenTestTags.IDLE).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchScreenTestTags.SEARCH_FIELD).assertIsDisplayed()
    }
}
