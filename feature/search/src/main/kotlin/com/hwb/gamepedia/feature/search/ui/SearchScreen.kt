package com.hwb.gamepedia.feature.search.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hwb.gamepedia.core.designsystem.component.EmptyStateView
import com.hwb.gamepedia.core.designsystem.component.ErrorStateView
import com.hwb.gamepedia.core.designsystem.component.LoadingStateView
import com.hwb.gamepedia.core.model.Game
import com.hwb.gamepedia.core.model.GameSuggestion
import com.hwb.gamepedia.feature.search.R
import com.hwb.gamepedia.feature.search.presentation.QueryValidationError
import com.hwb.gamepedia.feature.search.presentation.ResultsUiState
import com.hwb.gamepedia.feature.search.presentation.SearchUiState
import com.hwb.gamepedia.feature.search.presentation.SearchViewModel
import com.hwb.gamepedia.feature.search.presentation.SuggestionsUiState
import java.time.Instant
import java.time.ZoneOffset

object SearchScreenTestTags {
    const val SEARCH_FIELD = "search_field"
    const val CLEAR_BUTTON = "search_clear_button"
    const val SUGGESTION_LIST = "search_suggestion_list"
    const val RESULT_LIST = "search_result_list"
    const val LOADING = "search_loading"
    const val EMPTY = "search_empty"
    const val ERROR = "search_error"
    const val RETRY_BUTTON = "search_retry_button"
    const val IDLE = "search_idle"
}

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.suggestions != SuggestionsUiState.Hidden) {
        viewModel.onBackPressed()
    }

    SearchScreenContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onClear = viewModel::onClear,
        onRetry = viewModel::onRetry,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onGenreFilterSelected = viewModel::onGenreFilterSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onGenreFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Text(
                    text = stringResource(R.string.search_title),
                    modifier = Modifier.semantics { heading() },
                )
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SearchField(
                query = state.query,
                queryError = state.queryError,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClear = onClear,
            )

            when (val suggestions = state.suggestions) {
                SuggestionsUiState.Hidden -> Unit
                SuggestionsUiState.Loading -> Text(
                    text = stringResource(R.string.search_suggestions_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                is SuggestionsUiState.Shown -> SuggestionList(
                    suggestions = suggestions.suggestions,
                    onSuggestionSelected = onSuggestionSelected,
                )
            }

            ResultsPane(
                results = state.results,
                selectedGenreId = state.selectedGenreId,
                visibleGames = state.visibleGames,
                onRetry = onRetry,
                onGenreFilterSelected = onGenreFilterSelected,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    queryError: QueryValidationError?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val fieldDescription = stringResource(R.string.search_field_description)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(SearchScreenTestTags.SEARCH_FIELD)
            .semantics { contentDescription = fieldDescription },
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(SearchScreenTestTags.CLEAR_BUTTON),
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.search_action_clear),
                    )
                }
            }
        },
        isError = queryError != null,
        supportingText = queryError?.let {
            { Text(stringResource(R.string.search_query_too_long)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

@Composable
private fun SuggestionList(
    suggestions: List<GameSuggestion>,
    onSuggestionSelected: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Column(modifier = Modifier.testTag(SearchScreenTestTags.SUGGESTION_LIST)) {
        Text(
            text = stringResource(R.string.search_suggestions_heading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { heading() },
        )
        suggestions.forEach { suggestion ->
            val name = suggestion.name ?: stringResource(R.string.search_unnamed_game)
            ListItem(
                headlineContent = { Text(name) },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { onSuggestionSelected(name) },
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun ResultsPane(
    results: ResultsUiState,
    selectedGenreId: String?,
    visibleGames: List<Game>,
    onRetry: () -> Unit,
    onGenreFilterSelected: (String?) -> Unit,
) {
    when (results) {
        ResultsUiState.Idle -> EmptyStateView(
            title = stringResource(R.string.search_idle_title),
            body = stringResource(R.string.search_idle_body),
            modifier = Modifier.testTag(SearchScreenTestTags.IDLE),
        )

        is ResultsUiState.Loading -> LoadingStateView(
            label = stringResource(R.string.search_loading),
            modifier = Modifier.testTag(SearchScreenTestTags.LOADING),
        )

        is ResultsUiState.Empty -> EmptyStateView(
            title = stringResource(R.string.search_empty_title),
            body = stringResource(R.string.search_empty_body, results.query),
            modifier = Modifier.testTag(SearchScreenTestTags.EMPTY),
        )

        is ResultsUiState.Error -> ErrorStateView(
            message = stringResource(results.failure.userMessageRes()),
            retryLabel = if (results.failure.retryable) stringResource(R.string.search_action_retry) else null,
            onRetry = onRetry,
            modifier = Modifier.testTag(SearchScreenTestTags.ERROR),
        )

        is ResultsUiState.Success -> Column {
            if (results.genreFilters.isNotEmpty()) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "filter-all") {
                        FilterChip(
                            selected = selectedGenreId == null,
                            onClick = { onGenreFilterSelected(null) },
                            label = { Text(stringResource(R.string.search_filter_all)) },
                        )
                    }
                    items(results.genreFilters, key = { it.id }) { filter ->
                        FilterChip(
                            selected = selectedGenreId == filter.id,
                            onClick = { onGenreFilterSelected(filter.id) },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.search_result_count, visibleGames.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SearchScreenTestTags.RESULT_LIST),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(visibleGames, key = { it.id }) { game ->
                    GameResultItem(game)
                }
            }
        }
    }
}

@Composable
private fun GameResultItem(game: Game) {
    val name = game.name ?: stringResource(R.string.search_unnamed_game)
    ListItem(
        headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (game.genres.isNotEmpty()) {
                    Text(
                        text = game.genres.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    game.rating?.let { rating ->
                        val display = String.format(java.util.Locale.ROOT, "%.0f", rating)
                        val ratingDescription = stringResource(R.string.search_rating_description, display)
                        Text(
                            text = "★ $display",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { contentDescription = ratingDescription },
                        )
                    }
                    game.releaseDateEpochSeconds?.let { epochSeconds ->
                        Text(
                            text = Instant.ofEpochSecond(epochSeconds)
                                .atZone(ZoneOffset.UTC).year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        leadingContent = {
            AsyncImage(
                model = game.coverUrl,
                contentDescription = stringResource(R.string.search_cover_description, name),
                modifier = Modifier.size(56.dp),
            )
        },
        modifier = Modifier.defaultMinSize(minHeight = 72.dp),
    )
}
