package com.hwb.gamepedia.core.network

import com.hwb.gamepedia.core.network.dto.AuthSessionDataDto
import com.hwb.gamepedia.core.network.dto.DeleteAccountResultDto
import com.hwb.gamepedia.core.network.dto.GoogleLoginRequestDto
import com.hwb.gamepedia.core.network.dto.LoginRequestDto
import com.hwb.gamepedia.core.network.dto.LogoutRequestDto
import com.hwb.gamepedia.core.network.dto.LogoutResultDto
import com.hwb.gamepedia.core.network.dto.MeDataDto
import com.hwb.gamepedia.core.network.dto.RefreshRequestDto
import com.hwb.gamepedia.core.network.dto.SignUpRequestDto
import com.hwb.gamepedia.core.network.dto.SuccessEnvelopeDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Credential-establishing `/auth` endpoints (GamePediaCoreServer `auth.routes.js`).
 *
 * MUST be bound to the plain (non-authenticated) OkHttp client: none of these
 * take an Authorization header, and the refresh endpoint especially must never
 * pass through the session authenticator — that is what structurally prevents
 * recursive refresh.
 */
interface AuthApi {

    /** 201 on success. */
    @POST("auth/signup")
    suspend fun signUp(@Body body: SignUpRequestDto): SuccessEnvelopeDto<AuthSessionDataDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): SuccessEnvelopeDto<AuthSessionDataDto>

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequestDto): SuccessEnvelopeDto<AuthSessionDataDto>

    /** Rotates the pair: the submitted refresh token is revoked server-side. */
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): SuccessEnvelopeDto<AuthSessionDataDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): SuccessEnvelopeDto<LogoutResultDto>
}

/**
 * Authenticated `/auth` endpoints. Bound to the authenticated client (Bearer
 * header + 401-driven refresh via [SessionAuthenticator]).
 */
interface AuthedUserApi {

    @GET("auth/me")
    suspend fun me(): SuccessEnvelopeDto<MeDataDto>

    @DELETE("auth/me")
    suspend fun deleteMyAccount(): SuccessEnvelopeDto<DeleteAccountResultDto>
}
