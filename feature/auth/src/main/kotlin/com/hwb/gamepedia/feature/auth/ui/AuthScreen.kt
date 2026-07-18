package com.hwb.gamepedia.feature.auth.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hwb.gamepedia.core.model.AuthFailure
import com.hwb.gamepedia.core.model.SessionState
import com.hwb.gamepedia.feature.auth.R
import com.hwb.gamepedia.feature.auth.presentation.AuthMode
import com.hwb.gamepedia.feature.auth.presentation.AuthUiState
import com.hwb.gamepedia.feature.auth.presentation.AuthViewModel

object AuthScreenTestTags {
    const val EMAIL_FIELD = "auth_email_field"
    const val PASSWORD_FIELD = "auth_password_field"
    const val NICKNAME_FIELD = "auth_nickname_field"
    const val SUBMIT_BUTTON = "auth_submit_button"
    const val GOOGLE_BUTTON = "auth_google_button"
    const val LOGOUT_BUTTON = "auth_logout_button"
    const val ERROR_TEXT = "auth_error_text"
}

@Composable
fun AuthScreen(viewModel: AuthViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    AuthScreenContent(
        uiState = uiState,
        sessionState = sessionState,
        onModeChange = viewModel::onModeChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onNicknameChange = viewModel::onNicknameChange,
        onSubmit = viewModel::onSubmit,
        onGoogleSignIn = viewModel::onGoogleSignIn,
        onLogout = viewModel::onLogout,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    sessionState: SessionState,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.auth_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (sessionState) {
                is SessionState.Authenticated -> AuthenticatedPane(
                    nickname = sessionState.user.nickname,
                    onLogout = onLogout,
                )
                SessionState.Unauthenticated -> CredentialsPane(
                    uiState = uiState,
                    onModeChange = onModeChange,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onNicknameChange = onNicknameChange,
                    onSubmit = onSubmit,
                    onGoogleSignIn = onGoogleSignIn,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedPane(nickname: String, onLogout: () -> Unit) {
    Text(
        text = stringResource(R.string.auth_signed_in_as, nickname),
        style = MaterialTheme.typography.titleMedium,
    )
    Button(
        onClick = onLogout,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(AuthScreenTestTags.LOGOUT_BUTTON),
    ) {
        Text(stringResource(R.string.auth_logout))
    }
}

@Composable
private fun CredentialsPane(
    uiState: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = uiState.mode == AuthMode.LOGIN,
            onClick = { onModeChange(AuthMode.LOGIN) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringResource(R.string.auth_mode_login))
        }
        SegmentedButton(
            selected = uiState.mode == AuthMode.SIGN_UP,
            onClick = { onModeChange(AuthMode.SIGN_UP) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(R.string.auth_mode_signup))
        }
    }

    OutlinedTextField(
        value = uiState.email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.auth_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AuthScreenTestTags.EMAIL_FIELD),
    )

    OutlinedTextField(
        value = uiState.password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.auth_password_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AuthScreenTestTags.PASSWORD_FIELD),
    )

    if (uiState.mode == AuthMode.SIGN_UP) {
        OutlinedTextField(
            value = uiState.nickname,
            onValueChange = onNicknameChange,
            label = { Text(stringResource(R.string.auth_nickname_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthScreenTestTags.NICKNAME_FIELD),
        )
    }

    uiState.failure?.let { failure ->
        Text(
            text = stringResource(failure.userMessageRes()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .testTag(AuthScreenTestTags.ERROR_TEXT),
        )
    }
    if (uiState.googleNotConfigured) {
        Text(
            text = stringResource(R.string.auth_google_not_configured),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    Button(
        onClick = onSubmit,
        enabled = uiState.canSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag(AuthScreenTestTags.SUBMIT_BUTTON),
    ) {
        if (uiState.submitting) {
            CircularProgressIndicator(modifier = Modifier.sizeIn(maxWidth = 20.dp, maxHeight = 20.dp))
        } else {
            Text(
                stringResource(
                    if (uiState.mode == AuthMode.LOGIN) R.string.auth_submit_login else R.string.auth_submit_signup,
                ),
            )
        }
    }

    OutlinedButton(
        onClick = onGoogleSignIn,
        enabled = !uiState.submitting,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag(AuthScreenTestTags.GOOGLE_BUTTON),
    ) {
        Text(stringResource(R.string.auth_google_button))
    }
}

@StringRes
private fun AuthFailure.userMessageRes(): Int = when (this) {
    is AuthFailure.InvalidCredentials -> R.string.auth_error_invalid_credentials
    is AuthFailure.EmailAlreadyInUse -> R.string.auth_error_email_in_use
    is AuthFailure.NicknameAlreadyExists -> R.string.auth_error_nickname_exists
    is AuthFailure.ValidationFailed -> R.string.auth_error_validation
    is AuthFailure.SessionExpired -> R.string.auth_error_session_expired
    is AuthFailure.AccountUnavailable -> R.string.auth_error_account_unavailable
    is AuthFailure.SocialConflict -> R.string.auth_error_social_conflict
    is AuthFailure.GoogleAccountRejected -> R.string.auth_error_google_rejected
    is AuthFailure.ServerError -> R.string.auth_error_server
    AuthFailure.Timeout -> R.string.auth_error_timeout
    AuthFailure.NetworkUnavailable -> R.string.auth_error_offline
    AuthFailure.MalformedResponse -> R.string.auth_error_malformed
    AuthFailure.MissingRefreshToken,
    AuthFailure.Superseded,
    AuthFailure.FlightCancelled,
    -> R.string.auth_error_session_expired
    is AuthFailure.Unexpected -> R.string.auth_error_unexpected
}
