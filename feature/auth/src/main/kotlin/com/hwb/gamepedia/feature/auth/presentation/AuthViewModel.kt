package com.hwb.gamepedia.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hwb.gamepedia.core.auth.AuthRepository
import com.hwb.gamepedia.core.model.AuthFailure
import com.hwb.gamepedia.core.model.AuthOutcome
import com.hwb.gamepedia.core.model.SessionState
import com.hwb.gamepedia.feature.auth.GoogleIdTokenProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SIGN_UP }

/**
 * Minimal auth-foundation UI state. Field values live here; credentials are never
 * logged and never leave the ViewModel except toward the repository.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val nickname: String = "",
    val submitting: Boolean = false,
    val failure: AuthFailure? = null,
    val googleNotConfigured: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !submitting &&
            email.isNotBlank() &&
            password.length >= PASSWORD_MIN_LENGTH &&
            (mode == AuthMode.LOGIN || nickname.trim().length >= NICKNAME_MIN_LENGTH)

    companion object {
        // Client-side mirrors of backend zod constraints (docs/AUTH_CONTRACT.md).
        const val PASSWORD_MIN_LENGTH = 8
        const val NICKNAME_MIN_LENGTH = 2
    }
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val googleIdTokenProvider: GoogleIdTokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<SessionState> = repository.sessionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.sessionState.value)

    private var submitJob: Job? = null

    fun onModeChange(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(mode = mode, failure = null, googleNotConfigured = false)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, failure = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, failure = null)
    }

    fun onNicknameChange(value: String) {
        _uiState.value = _uiState.value.copy(nickname = value, failure = null)
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        submitJob?.cancel()
        _uiState.value = state.copy(submitting = true, failure = null, googleNotConfigured = false)
        submitJob = viewModelScope.launch {
            val outcome = when (state.mode) {
                AuthMode.LOGIN -> repository.login(state.email.trim(), state.password)
                AuthMode.SIGN_UP -> repository.signUp(state.email.trim(), state.password, state.nickname.trim())
            }
            applyOutcome(outcome)
        }
    }

    fun onGoogleSignIn() {
        submitJob?.cancel()
        _uiState.value = _uiState.value.copy(submitting = true, failure = null, googleNotConfigured = false)
        submitJob = viewModelScope.launch {
            when (val credential = googleIdTokenProvider.acquireIdToken()) {
                is GoogleIdTokenProvider.Result.Success ->
                    applyOutcome(repository.loginWithGoogle(credential.idToken))
                GoogleIdTokenProvider.Result.NotConfigured ->
                    _uiState.value = _uiState.value.copy(submitting = false, googleNotConfigured = true)
                GoogleIdTokenProvider.Result.Cancelled ->
                    _uiState.value = _uiState.value.copy(submitting = false)
                is GoogleIdTokenProvider.Result.Failed ->
                    _uiState.value = _uiState.value.copy(
                        submitting = false,
                        failure = AuthFailure.Unexpected(backendCode = null),
                    )
            }
        }
    }

    fun onLogout() {
        repository.logout()
    }

    private fun applyOutcome(outcome: AuthOutcome<*>) {
        _uiState.value = when (outcome) {
            is AuthOutcome.Success ->
                // Session state flips via repository.sessionState; scrub the
                // credential fields immediately.
                AuthUiState(mode = _uiState.value.mode)
            is AuthOutcome.Failure ->
                _uiState.value.copy(submitting = false, failure = outcome.failure)
        }
    }
}
