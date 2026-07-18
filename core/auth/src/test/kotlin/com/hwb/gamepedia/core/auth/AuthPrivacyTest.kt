package com.hwb.gamepedia.core.auth

import com.hwb.gamepedia.core.model.AuthSession
import com.hwb.gamepedia.core.model.AuthTokens
import com.hwb.gamepedia.core.network.AuthDiagnostics
import com.hwb.gamepedia.core.network.dto.AuthSessionDataDto
import com.hwb.gamepedia.core.network.dto.AuthUserDto
import com.hwb.gamepedia.core.network.dto.GoogleLoginRequestDto
import com.hwb.gamepedia.core.network.dto.LoginRequestDto
import com.hwb.gamepedia.core.network.dto.LogoutRequestDto
import com.hwb.gamepedia.core.network.dto.RefreshRequestDto
import com.hwb.gamepedia.core.network.dto.SignUpRequestDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import com.hwb.gamepedia.core.network.dto.TokenPairDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matrix item 15: credential material never reaches logs.
 *
 * Sentinel values flow through login, signup, google login, refresh (success and
 * failure), logout, and supersession while every diagnostic line is captured; no
 * sentinel may appear. toString() of every credential-carrying type is redacted so
 * accidental interpolation cannot leak either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthPrivacyTest {

    private companion object {
        const val EMAIL = "privacy-sentinel-email@example.com"
        const val PASSWORD = "privacy-sentinel-password"
        const val NICKNAME_SENSITIVE = "privacy-sentinel-nick"
        const val GOOGLE_ID_TOKEN = "privacy-sentinel-google-id-token"
        const val ACCESS_TOKEN = "privacy-sentinel-access-token"
        const val REFRESH_TOKEN = "privacy-sentinel-refresh-token"

        val SENTINELS = listOf(
            EMAIL, PASSWORD, NICKNAME_SENSITIVE, GOOGLE_ID_TOKEN, ACCESS_TOKEN, REFRESH_TOKEN,
            "Bearer ", "Authorization",
        )
    }

    private val captured = mutableListOf<String>()
    private val originalSink = AuthDiagnostics.sink

    @After
    fun restoreSink() {
        AuthDiagnostics.sink = originalSink
    }

    @Test
    fun `diagnostics never contain credentials across the full auth lifecycle`() = runTest {
        val h = AuthTestHarness(
            repositoryScope(),
            initialTokens = AuthTokens(accessToken = ACCESS_TOKEN, refreshToken = REFRESH_TOKEN),
        )
        // Install the capture AFTER harness construction (the harness resets the
        // sink to a no-op for tests that don't assert on diagnostics).
        captured.clear()
        AuthDiagnostics.sink = { line -> synchronized(captured) { captured += line } }
        h.api.loginHandler = { request ->
            // Echo the sentinel credentials into the response the way the real
            // backend echoes user data, to prove response paths do not log either.
            SuccessEnvelopeDto(
                success = true,
                data = AuthSessionDataDto(
                    user = AuthUserDto(
                        id = "user-privacy",
                        email = request.email,
                        nickname = NICKNAME_SENSITIVE,
                        profileImageUrl = null,
                        status = "ACTIVE",
                        createdAt = null,
                        updatedAt = null,
                    ),
                    tokens = TokenPairDto(accessToken = ACCESS_TOKEN, refreshToken = REFRESH_TOKEN),
                ),
            )
        }

        // Refresh failure path (auth rejection logs a category, never the token).
        val refresh = async { h.repository.refresh() }
        runCurrent()
        h.api.refreshCalls[0].gate.completeExceptionally(AuthFixtures.httpException(401, "TOKEN_REVOKED"))
        advanceUntilIdle()
        refresh.await()

        // Login with sentinel credentials.
        h.repository.login(EMAIL, PASSWORD)

        // Google login + signup + supersession while a refresh is in flight.
        val refresh2 = async { h.repository.refresh() }
        runCurrent()
        h.repository.loginWithGoogle(GOOGLE_ID_TOKEN)
        advanceUntilIdle()
        refresh2.await()
        h.repository.signUp(EMAIL, PASSWORD, NICKNAME_SENSITIVE)

        // Logout revokes the refresh token — the token must not be logged.
        h.repository.logout()
        advanceUntilIdle()

        assertTrue("expected diagnostics to have been emitted", captured.isNotEmpty())
        captured.forEach { line ->
            SENTINELS.forEach { sentinel ->
                assertFalse(
                    "diagnostic line leaked credential material: $line",
                    line.contains(sentinel, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `credential-carrying types redact toString`() {
        val tokens = AuthTokens(accessToken = ACCESS_TOKEN, refreshToken = REFRESH_TOKEN)
        val session = AuthSession(user = AuthFixtures.userDto("x").let {
            com.hwb.gamepedia.core.model.AuthUser(
                it.id, it.email, it.nickname, it.profileImageUrl, it.status, it.createdAt, it.updatedAt,
            )
        }, tokens = tokens)

        val renderings = listOf(
            tokens.toString(),
            session.toString(),
            LoginRequestDto(EMAIL, PASSWORD).toString(),
            SignUpRequestDto(EMAIL, PASSWORD, "nick").toString(),
            GoogleLoginRequestDto(GOOGLE_ID_TOKEN).toString(),
            RefreshRequestDto(REFRESH_TOKEN).toString(),
            LogoutRequestDto(REFRESH_TOKEN).toString(),
            TokenPairDto(ACCESS_TOKEN, REFRESH_TOKEN).toString(),
            AuthSessionDataDto(
                user = AuthFixtures.userDto("x"),
                tokens = TokenPairDto(ACCESS_TOKEN, REFRESH_TOKEN),
            ).toString(),
        )

        renderings.forEach { rendered ->
            listOf(EMAIL, PASSWORD, GOOGLE_ID_TOKEN, ACCESS_TOKEN, REFRESH_TOKEN).forEach { sentinel ->
                assertFalse(
                    "toString leaked credential material: $rendered",
                    rendered.contains(sentinel),
                )
            }
        }
    }
}
