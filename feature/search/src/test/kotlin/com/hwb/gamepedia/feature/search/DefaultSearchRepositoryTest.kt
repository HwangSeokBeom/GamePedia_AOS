package com.hwb.gamepedia.feature.search

import com.hwb.gamepedia.core.common.TimeSource
import com.hwb.gamepedia.core.model.SearchOutcome
import com.hwb.gamepedia.core.network.GamePediaNetwork
import com.hwb.gamepedia.core.network.SearchApi
import com.hwb.gamepedia.core.network.SearchDiagnostics
import com.hwb.gamepedia.core.network.dto.GameListItemDto
import com.hwb.gamepedia.core.network.dto.GameSearchDataDto
import com.hwb.gamepedia.core.network.dto.GameSuggestionItemDto
import com.hwb.gamepedia.core.network.dto.GameSuggestionsDataDto
import com.hwb.gamepedia.core.network.dto.SearchMetaDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import com.hwb.gamepedia.feature.search.data.DefaultSearchRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSearchApi : SearchApi {
    var searchCallCount = 0
    var suggestionCallCount = 0
    var failWith: Throwable? = null

    override suspend fun searchGames(query: String, limit: Int?): SuccessEnvelopeDto<GameSearchDataDto> {
        searchCallCount++
        failWith?.let { throw it }
        val game = GameListItemDto(
            id = 1001,
            name = "Contract Quest",
            summary = null,
            coverUrl = null,
            genres = listOf("Adventure"),
            platforms = emptyList(),
            rating = 88.5,
            aggregatedRating = null,
            totalRating = 88.5,
            releaseDate = 1700000000L,
        )
        return SuccessEnvelopeDto(
            success = true,
            data = GameSearchDataDto(
                query = query,
                games = listOf(game),
                results = listOf(game),
                suggestions = emptyList(),
                meta = SearchMetaDto(query, query.lowercase(), query.lowercase(), 1),
            ),
        )
    }

    override suspend fun getSuggestions(query: String, limit: Int?): SuccessEnvelopeDto<GameSuggestionsDataDto> {
        suggestionCallCount++
        failWith?.let { throw it }
        return SuccessEnvelopeDto(
            success = true,
            data = GameSuggestionsDataDto(
                suggestions = listOf(GameSuggestionItemDto(1001, "Contract Quest", null, 88.5)),
                meta = SearchMetaDto(query, query.lowercase(), query.lowercase(), 1),
            ),
        )
    }
}

class DefaultSearchRepositoryTest {

    private val api = FakeSearchApi()
    private var nowMillis = 1_000_000L
    private val capturedDiagnostics = mutableListOf<String>()
    private val originalSink = SearchDiagnostics.sink

    private val repository = DefaultSearchRepository(
        api = api,
        failureMapper = GamePediaNetwork.createFailureMapper(),
        timeSource = TimeSource { nowMillis },
    )

    @Before
    fun captureDiagnostics() {
        capturedDiagnostics.clear()
        SearchDiagnostics.sink = { line -> capturedDiagnostics += line }
    }

    @After
    fun restoreDiagnostics() {
        SearchDiagnostics.sink = originalSink
    }

    @Test
    fun `identical query within TTL is served from cache deterministically`() = runTest {
        val first = repository.search("Zelda") as SearchOutcome.Success
        // Key is trimmed+lowercased: same candidate set, same entry.
        val second = repository.search("  zelda ") as SearchOutcome.Success

        assertEquals(1, api.searchCallCount)
        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(first.value.games, second.value.games)
    }

    @Test
    fun `cache entry expires after ten minutes`() = runTest {
        repository.search("zelda")
        nowMillis += DefaultSearchRepository.CACHE_TTL_MILLIS + 1
        val refetched = repository.search("zelda") as SearchOutcome.Success

        assertEquals(2, api.searchCallCount)
        assertFalse(refetched.fromCache)
    }

    @Test
    fun `forceRefresh bypasses the cache so retry can refresh the result`() = runTest {
        repository.search("zelda")
        val refreshed = repository.search("zelda", forceRefresh = true) as SearchOutcome.Success

        assertEquals(2, api.searchCallCount)
        assertFalse(refreshed.fromCache)
    }

    @Test
    fun `suggestions are cached independently from search`() = runTest {
        repository.suggestions("zelda")
        repository.suggestions("zelda")
        repository.search("zelda")

        assertEquals(1, api.suggestionCallCount)
        assertEquals(1, api.searchCallCount)
    }

    // 13. Raw query absent from diagnostics
    @Test
    fun `diagnostics never contain the raw query on success or failure`() = runTest {
        val sensitiveQuery = "SuperSecretUnannouncedGame"

        repository.search(sensitiveQuery)
        repository.suggestions(sensitiveQuery)
        api.failWith = IOException("socket closed")
        repository.search(sensitiveQuery, forceRefresh = true)

        assertTrue(capturedDiagnostics.isNotEmpty())
        capturedDiagnostics.forEach { line ->
            assertFalse(
                "Diagnostic line leaked the raw query: $line",
                line.contains(sensitiveQuery, ignoreCase = true),
            )
            assertFalse("Diagnostic line leaked a URL with q=: $line", line.contains("q="))
        }
        // Allowed dimensions are present instead.
        assertTrue(capturedDiagnostics.any { it.contains("q_len=${sensitiveQuery.length}") })
        assertTrue(capturedDiagnostics.any { it.contains("error=network_unavailable") })
    }

    @Test
    fun `failure outcome carries the mapped taxonomy`() = runTest {
        api.failWith = IOException("offline")
        val outcome = repository.search("zelda", forceRefresh = true)

        assertTrue(outcome is SearchOutcome.Failure)
        assertEquals(
            "network_unavailable",
            (outcome as SearchOutcome.Failure).failure.diagnosticCategory,
        )
    }
}
