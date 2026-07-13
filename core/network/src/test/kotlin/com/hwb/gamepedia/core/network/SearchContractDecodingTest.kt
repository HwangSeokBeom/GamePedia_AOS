package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.network.dto.ErrorEnvelopeDto
import com.hwb.gamepedia.core.network.dto.GameSearchDataDto
import com.hwb.gamepedia.core.network.dto.GameSuggestionsDataDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract decoding tests. Fixtures mirror GamePediaCoreServer 8790a13
 * (`test/games-search-contract.test.js` shapes and values) — they are the Android
 * side of the cross-platform contract, not invented DTO samples.
 */
class SearchContractDecodingTest {

    private val json = GamePediaNetwork.json

    @Test
    fun `search success fixture decodes with exact field values`() {
        val envelope = json.decodeFromString<SuccessEnvelopeDto<GameSearchDataDto>>(
            loadFixture("search_success.json"),
        )

        assertTrue(envelope.success)
        val data = envelope.data
        assertEquals("contract quest", data.query)
        assertEquals(2, data.games.size)

        val top = data.games.first { it.id == 1001 }
        assertEquals("Contract Quest", top.name)
        assertEquals("A game used only by contract tests.", top.summary)
        assertTrue(top.coverUrl!!.startsWith("https://"))
        assertEquals(listOf("Adventure"), top.genres)
        assertEquals(listOf("PC (Microsoft Windows)"), top.platforms)
        assertEquals(88.5, top.rating!!, 0.0)
        assertNull(top.aggregatedRating)
        assertEquals(88.5, top.totalRating!!, 0.0)
        assertEquals(1700000000L, top.releaseDate)

        // Server nulls must decode as nulls, not defaults.
        val nullable = data.games.first { it.id == 1002 }
        assertNull(nullable.name)
        assertNull(nullable.summary)
        assertNull(nullable.coverUrl)
        assertTrue(nullable.genres.isEmpty())
        assertTrue(nullable.platforms.isEmpty())
        assertNull(nullable.rating)
        assertNull(nullable.releaseDate)

        assertEquals("contract quest", data.meta.originalQuery)
        assertEquals(2, data.meta.resultCount)
        // `results` is decoded (strict shape) but must stay a byte-identical alias.
        assertEquals(data.games, data.results)
    }

    @Test
    fun `suggestions success fixture decodes with exact field values`() {
        val envelope = json.decodeFromString<SuccessEnvelopeDto<GameSuggestionsDataDto>>(
            loadFixture("suggestions_success.json"),
        )

        assertTrue(envelope.success)
        val data = envelope.data
        assertEquals(2, data.suggestions.size)
        assertEquals("Suggestion Case", data.suggestions[0].name)
        assertEquals(88.5, data.suggestions[0].rating!!, 0.0)
        assertNull(data.suggestions[1].coverUrl)
        assertEquals(70.0, data.suggestions[1].rating!!, 0.0)
        assertEquals(2, data.meta.resultCount)
    }

    @Test
    fun `empty search result decodes as success with zero games`() {
        val envelope = json.decodeFromString<SuccessEnvelopeDto<GameSearchDataDto>>(
            loadFixture("search_empty.json"),
        )

        assertTrue(envelope.success)
        assertTrue(envelope.data.games.isEmpty())
        assertTrue(envelope.data.suggestions.isEmpty())
        assertEquals(0, envelope.data.meta.resultCount)
    }

    @Test
    fun `error envelope decodes stable code and validation details`() {
        val envelope = json.decodeFromString<ErrorEnvelopeDto>(loadFixture("error_invalid_query.json"))

        assertEquals(false, envelope.success)
        assertEquals("INVALID_SEARCH_QUERY", envelope.error.code)
        val details = envelope.error.details!!.jsonArray
        assertEquals("q", details[0].jsonObject["field"]!!.jsonPrimitive.content)
    }

    @Test
    fun `missing contract field fails decoding instead of defaulting`() {
        // `meta` removed: strict decoding must throw, surfacing MalformedResponse upstream.
        val truncated = """{"success":true,"data":{"query":"x","games":[],"results":[],"suggestions":[]}}"""
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<SuccessEnvelopeDto<GameSearchDataDto>>(truncated)
        }
    }
}
