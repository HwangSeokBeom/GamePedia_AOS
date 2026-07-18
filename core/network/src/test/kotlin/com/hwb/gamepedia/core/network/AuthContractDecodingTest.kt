package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.network.dto.AuthSessionDataDto
import com.hwb.gamepedia.core.network.dto.ErrorEnvelopeDto
import com.hwb.gamepedia.core.network.dto.LoginRequestDto
import com.hwb.gamepedia.core.network.dto.RefreshRequestDto
import com.hwb.gamepedia.core.network.dto.SignUpRequestDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auth wire-contract tests against GamePediaCoreServer response/request shapes
 * (auth.service.js `{ user, tokens }`, user.mapper.js `mapUserToDto`, zod
 * validators in auth.validator.js).
 */
class AuthContractDecodingTest {

    private val json = GamePediaNetwork.json

    @Test
    fun `auth session envelope decodes user and token pair`() {
        val envelope = json.decodeFromString<SuccessEnvelopeDto<AuthSessionDataDto>>(
            loadFixture("auth_session_success.json"),
        )

        assertTrue(envelope.success)
        assertEquals("contract-user@example.com", envelope.data.user.email)
        assertEquals("contract-nick", envelope.data.user.nickname)
        assertNull(envelope.data.user.profileImageUrl)
        assertEquals("ACTIVE", envelope.data.user.status)
        assertEquals("2026-07-18T00:00:00.000Z", envelope.data.user.createdAt)
        assertEquals("fixture.access.jwt", envelope.data.tokens.accessToken)
        assertEquals("fixture.refresh.jwt", envelope.data.tokens.refreshToken)
    }

    @Test
    fun `auth error envelope decodes the stable code`() {
        val envelope = json.decodeFromString<ErrorEnvelopeDto>(
            loadFixture("auth_error_invalid_credentials.json"),
        )

        assertEquals(false, envelope.success)
        assertEquals("INVALID_CREDENTIALS", envelope.error.code)
    }

    @Test
    fun `missing tokens field fails decoding instead of defaulting`() {
        val truncated = """{"success":true,"data":{"user":{"id":"x","email":"e","nickname":"n","status":"ACTIVE"}}}"""
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<SuccessEnvelopeDto<AuthSessionDataDto>>(truncated)
        }
    }

    /**
     * Backend zod schemas mark deviceName as `.optional()` but NOT `.nullable()`:
     * an explicit JSON null is a validation error, so absent optional fields must
     * be omitted from the wire entirely.
     */
    @Test
    fun `request DTOs omit absent optional fields instead of sending null`() {
        assertEquals(
            """{"refreshToken":"r1"}""",
            json.encodeToString(RefreshRequestDto.serializer(), RefreshRequestDto("r1")),
        )
        assertEquals(
            """{"email":"e@x.com","password":"password123"}""",
            json.encodeToString(LoginRequestDto.serializer(), LoginRequestDto("e@x.com", "password123")),
        )
        assertFalse(
            json.encodeToString(
                SignUpRequestDto.serializer(),
                SignUpRequestDto("e@x.com", "password123", "nick"),
            ).contains("deviceName"),
        )
    }

    @Test
    fun `request DTOs include deviceName when provided`() {
        val encoded = json.encodeToString(
            RefreshRequestDto.serializer(),
            RefreshRequestDto("r1", deviceName = "Pixel 8"),
        )
        assertEquals("""{"refreshToken":"r1","deviceName":"Pixel 8"}""", encoded)
    }
}
