package com.hwb.gamepedia.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hwb.gamepedia.feature.auth.presentation.AuthViewModel
import com.hwb.gamepedia.feature.auth.ui.AuthScreen
import com.hwb.gamepedia.feature.search.presentation.SearchViewModel
import com.hwb.gamepedia.feature.search.ui.SearchScreen

object Destinations {
    const val SEARCH = "search"
    const val AUTH = "auth"
}

@Composable
fun GamePediaNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destinations.SEARCH) {
        composable(Destinations.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel(
                factory = searchViewModelFactory(appContainer),
            )
            SearchScreen(
                viewModel = searchViewModel,
                onAccountClick = { navController.navigate(Destinations.AUTH) },
            )
        }
        composable(Destinations.AUTH) {
            val authViewModel: AuthViewModel = viewModel(
                factory = authViewModelFactory(appContainer),
            )
            AuthScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

private fun searchViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle: SavedStateHandle = extras.createSavedStateHandle()
            return SearchViewModel(
                repository = appContainer.searchRepository,
                savedStateHandle = savedStateHandle,
            ) as T
        }
    }

private fun authViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            AuthViewModel(
                repository = appContainer.authRepository,
                googleIdTokenProvider = appContainer.googleIdTokenProvider,
            ) as T
    }
