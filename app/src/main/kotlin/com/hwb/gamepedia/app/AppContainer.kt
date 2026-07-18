package com.hwb.gamepedia.app

import android.content.Context
import android.os.Build
import com.hwb.gamepedia.core.auth.AuthRepository
import com.hwb.gamepedia.core.auth.DefaultAuthRepository
import com.hwb.gamepedia.core.network.AuthedUserApi
import com.hwb.gamepedia.core.network.GamePediaNetwork
import com.hwb.gamepedia.core.storage.SecureSessionStorage
import com.hwb.gamepedia.feature.auth.GoogleIdTokenProvider
import com.hwb.gamepedia.feature.search.data.DefaultSearchRepository
import com.hwb.gamepedia.feature.search.data.SearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency wiring for the current slices. A DI framework is deliberately
 * deferred until more features exist (docs/DECISIONS.md D-0002).
 *
 * Client topology (docs/AUTH_ARCHITECTURE.md):
 *  - plain client: public search + credential `/auth` endpoints (incl. refresh) —
 *    never carries an Authorization header, never triggers the authenticator.
 *  - authenticated client: plain client + Bearer interceptor + single-flight
 *    401 authenticator, for authenticated endpoints only.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val plainClient = GamePediaNetwork.createOkHttpClient()
    private val searchApi = GamePediaNetwork.createSearchApi(client = plainClient)
    private val authApi = GamePediaNetwork.createAuthApi(client = plainClient)

    private val sessionStorage = SecureSessionStorage(context)

    val authRepository: AuthRepository by lazy { defaultAuthRepository }

    private val defaultAuthRepository: DefaultAuthRepository by lazy {
        DefaultAuthRepository(
            authApi = authApi,
            authedUserApiProvider = { authedUserApi },
            failureMapper = GamePediaNetwork.createAuthFailureMapper(),
            tokenStore = sessionStorage.tokenStore,
            userSessionStore = sessionStorage.userSessionStore,
            scope = appScope,
            deviceName = Build.MODEL,
        )
    }

    private val authenticatedClient by lazy {
        GamePediaNetwork.createAuthenticatedClient(
            gateway = defaultAuthRepository,
            plainClient = plainClient,
        )
    }

    private val authedUserApi: AuthedUserApi by lazy {
        GamePediaNetwork.createAuthedUserApi(authenticatedClient)
    }

    /** Swap for a real Credential Manager implementation once OAuth client IDs exist. */
    val googleIdTokenProvider: GoogleIdTokenProvider = GoogleIdTokenProvider.Unavailable

    val searchRepository: SearchRepository =
        DefaultSearchRepository(
            api = searchApi,
            failureMapper = GamePediaNetwork.createFailureMapper(),
        )
}
