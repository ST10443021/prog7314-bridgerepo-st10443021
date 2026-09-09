package com.devledger.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.components.BottomNavTab
import com.devledger.app.ui.components.DevLedgerBottomNav
import com.devledger.app.ui.screens.auth.AuthScreen
import com.devledger.app.ui.screens.biometric.BiometricScreen
import com.devledger.app.ui.screens.dashboard.DashboardScreen
import com.devledger.app.ui.screens.finance.AddTransactionScreen
import com.devledger.app.ui.screens.finance.FinancialTrackerScreen
import com.devledger.app.ui.screens.notifications.NotificationsScreen
import com.devledger.app.ui.screens.settings.SettingsScreen
import com.devledger.app.ui.screens.snippets.SnippetEditorScreen
import com.devledger.app.ui.screens.snippets.SnippetVaultScreen
import com.devledger.app.ui.screens.splash.SplashScreen
import javax.inject.Inject

/**
 * Root navigation graph. Route wiring mirrors the navigation wireflow diagram
 * (Figure 11) from the Part 1 Planning and Design document: Splash → Auth →
 * Biometric → Dashboard, with Dashboard/Snippets/Finance/Notifications reachable via
 * the persistent bottom navigation bar, and Settings/Editor/AddTransaction as
 * secondary screens layered on top.
 */
@Composable
fun DevLedgerNavHost(navController: NavHostController) {
    val authViewModelHolder: AuthEntryPoint = hiltViewModel()
    val isLoggedIn = authViewModelHolder.isLoggedIn()

    NavHost(navController = navController, startDestination = DevLedgerDestinations.SPLASH) {

        composable(DevLedgerDestinations.SPLASH) {
            SplashScreen(
                isLoggedIn = isLoggedIn,
                onNavigateNext = { loggedIn ->
                    val destination = if (loggedIn) DevLedgerDestinations.BIOMETRIC else DevLedgerDestinations.AUTH
                    navController.navigate(destination) {
                        popUpTo(DevLedgerDestinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(DevLedgerDestinations.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(DevLedgerDestinations.BIOMETRIC) {
                        popUpTo(DevLedgerDestinations.AUTH) { inclusive = true }
                    }
                },
                onNavigateToRegister = { /* Registration re-uses AuthScreen's email fields for the prototype */ },
            )
        }

        composable(DevLedgerDestinations.BIOMETRIC) {
            BiometricScreen(
                onVerified = {
                    navController.navigate(DevLedgerDestinations.DASHBOARD) {
                        popUpTo(DevLedgerDestinations.SPLASH) { inclusive = true }
                    }
                },
                onUsePinInstead = {
                    navController.navigate(DevLedgerDestinations.DASHBOARD) {
                        popUpTo(DevLedgerDestinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        // Main, bottom-nav-hosted screens
        composable(DevLedgerDestinations.DASHBOARD) {
            MainScaffold(navController, BottomNavTab.DASHBOARD) {
                DashboardScreen(
                    onOpenSnippets = { navController.navigate(DevLedgerDestinations.SNIPPET_VAULT) },
                    onOpenFinance = { navController.navigate(DevLedgerDestinations.FINANCE) },
                    onOpenSettings = { navController.navigate(DevLedgerDestinations.SETTINGS) },
                )
            }
        }
        composable(DevLedgerDestinations.SNIPPET_VAULT) {
            MainScaffold(navController, BottomNavTab.SNIPPETS, onFabClick = { navController.navigate(DevLedgerDestinations.snippetEditorRoute()) }) {
                SnippetVaultScreen(
                    onOpenSnippet = { id -> navController.navigate(DevLedgerDestinations.snippetEditorRoute(id)) },
                )
            }
        }
        composable(DevLedgerDestinations.FINANCE) {
            MainScaffold(navController, BottomNavTab.FINANCE, onFabClick = { navController.navigate(DevLedgerDestinations.ADD_TRANSACTION) }) {
                FinancialTrackerScreen(onAddTransaction = { navController.navigate(DevLedgerDestinations.ADD_TRANSACTION) })
            }
        }
        composable(DevLedgerDestinations.NOTIFICATIONS) {
            MainScaffold(navController, BottomNavTab.NOTIFICATIONS) {
                NotificationsScreen()
            }
        }

        // Secondary screens
        composable(
            route = DevLedgerDestinations.SNIPPET_EDITOR,
            arguments = listOf(navArgument("snippetId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            SnippetEditorScreen(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(DevLedgerDestinations.ADD_TRANSACTION) {
            AddTransactionScreen(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(DevLedgerDestinations.SETTINGS) {
            SettingsScreen(
                onSignedOut = {
                    navController.navigate(DevLedgerDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Wraps a bottom-nav-hosted screen with the shared [DevLedgerBottomNav]. */
@Composable
private fun MainScaffold(
    navController: NavHostController,
    active: BottomNavTab,
    onFabClick: () -> Unit = { navController.navigate(DevLedgerDestinations.snippetEditorRoute()) },
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            DevLedgerBottomNav(
                active = active,
                onNavigate = { tab -> navigateToTab(navController, tab) },
                onFabClick = onFabClick,
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            content()
        }
    }
}

private fun navigateToTab(navController: NavHostController, tab: BottomNavTab) {
    val route = when (tab) {
        BottomNavTab.DASHBOARD -> DevLedgerDestinations.DASHBOARD
        BottomNavTab.SNIPPETS -> DevLedgerDestinations.SNIPPET_VAULT
        BottomNavTab.FINANCE -> DevLedgerDestinations.FINANCE
        BottomNavTab.NOTIFICATIONS -> DevLedgerDestinations.NOTIFICATIONS
    }
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Small Hilt entry-point ViewModel purely to read [AuthRepository.isLoggedIn] once at
 *  the top of the nav graph, deciding whether Splash routes to Auth or Biometric. */
@dagger.hilt.android.lifecycle.HiltViewModel
class AuthEntryPoint @Inject constructor(
    private val authRepository: AuthRepository,
) : androidx.lifecycle.ViewModel() {
    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
}
