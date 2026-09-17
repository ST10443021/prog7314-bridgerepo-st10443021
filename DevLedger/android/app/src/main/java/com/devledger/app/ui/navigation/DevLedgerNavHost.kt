package com.devledger.app.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.components.BottomNavTab
import com.devledger.app.ui.components.DevLedgerBottomNav
import com.devledger.app.ui.screens.auth.AuthScreen
import com.devledger.app.ui.screens.auth.RegisterScreen
import com.devledger.app.ui.screens.biometric.BiometricScreen
import com.devledger.app.ui.screens.biometric.PinVerificationScreen
import com.devledger.app.ui.screens.biometric.SecuritySetupScreen
import com.devledger.app.ui.screens.dashboard.DashboardScreen
import com.devledger.app.ui.screens.finance.AddTransactionScreen
import com.devledger.app.ui.screens.finance.FinancialTrackerScreen
import com.devledger.app.ui.screens.finance.TransactionDetailScreen
import com.devledger.app.ui.screens.finance.TransactionListScreen
import com.devledger.app.ui.screens.folders.FoldersTagsScreen
import com.devledger.app.ui.screens.legal.PrivacyPolicyScreen
import com.devledger.app.ui.screens.legal.TermsScreen
import com.devledger.app.ui.screens.notifications.NotificationsScreen
import com.devledger.app.ui.screens.profile.ProfileScreen
import com.devledger.app.ui.screens.recycle.RecycleScreen
import com.devledger.app.ui.screens.settings.SettingsScreen
import com.devledger.app.ui.screens.snippets.SnippetDetailScreen
import com.devledger.app.ui.screens.snippets.SnippetEditorScreen
import com.devledger.app.ui.screens.snippets.SnippetVaultScreen
import com.devledger.app.ui.screens.splash.SplashScreen
import com.devledger.app.ui.screens.sync.SyncScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@Composable
fun DevLedgerNavHost(navController: NavHostController) {
    val authViewModelHolder: AuthEntryPoint = hiltViewModel()
    val isLoggedIn = authViewModelHolder.isLoggedIn()

    NavHost(navController = navController, startDestination = DevLedgerDestinations.SPLASH) {

        composable(DevLedgerDestinations.SPLASH) {
            val scope = rememberCoroutineScope()
            SplashScreen(
                isLoggedIn = isLoggedIn,
                onNavigateNext = { loggedIn ->
                    scope.launch {
                        if (loggedIn) {
                            if (authViewModelHolder.needsSecuritySetup()) {
                                navController.navigate(DevLedgerDestinations.SECURITY_SETUP) {
                                    popUpTo(DevLedgerDestinations.SPLASH) { inclusive = true }
                                }
                            } else {
                                navController.navigate(DevLedgerDestinations.BIOMETRIC) {
                                    popUpTo(DevLedgerDestinations.SPLASH) { inclusive = true }
                                }
                            }
                        } else {
                            navController.navigate(DevLedgerDestinations.AUTH) {
                                popUpTo(DevLedgerDestinations.SPLASH) { inclusive = true }
                            }
                        }
                    }
                },
            )
        }

        composable(DevLedgerDestinations.AUTH) {
            val scope = rememberCoroutineScope()
            AuthScreen(
                onAuthenticated = {
                    scope.launch {
                        if (authViewModelHolder.needsSecuritySetup()) {
                            navController.navigate(DevLedgerDestinations.SECURITY_SETUP) {
                                popUpTo(DevLedgerDestinations.AUTH) { inclusive = true }
                            }
                        } else {
                            navController.navigate(DevLedgerDestinations.BIOMETRIC) {
                                popUpTo(DevLedgerDestinations.AUTH) { inclusive = true }
                            }
                        }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(DevLedgerDestinations.REGISTER)
                },
            )
        }

        composable(DevLedgerDestinations.REGISTER) {
            RegisterScreen(
                onBack = { navController.popBackStack() },
                onTermsClick = { navController.navigate(DevLedgerDestinations.TERMS) },
                onPrivacyPolicyClick = { navController.navigate(DevLedgerDestinations.PRIVACY_POLICY) },
                onSuccess = {
                    navController.navigate(DevLedgerDestinations.SECURITY_SETUP) {
                        popUpTo(DevLedgerDestinations.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(DevLedgerDestinations.SECURITY_SETUP) {
            SecuritySetupScreen(
                onComplete = {
                    navController.navigate(DevLedgerDestinations.BIOMETRIC) {
                        popUpTo(DevLedgerDestinations.SECURITY_SETUP) { inclusive = true }
                    }
                },
                onLogout = {
                    navController.navigate(DevLedgerDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(DevLedgerDestinations.BIOMETRIC) {
            BiometricScreen(
                onVerified = {
                    navController.navigate(DevLedgerDestinations.DASHBOARD) {
                        popUpTo(DevLedgerDestinations.BIOMETRIC) { inclusive = true }
                    }
                },
                onUsePinInstead = {
                    navController.navigate(DevLedgerDestinations.PIN_VERIFICATION)
                },
            )
        }

        composable(DevLedgerDestinations.PIN_VERIFICATION) {
            PinVerificationScreen(
                onBack = { navController.popBackStack() },
                onVerified = {
                    navController.navigate(DevLedgerDestinations.DASHBOARD) {
                        popUpTo(DevLedgerDestinations.BIOMETRIC) { inclusive = true }
                    }
                }
            )
        }

        composable(DevLedgerDestinations.TERMS) {
            TermsScreen(onBack = { navController.popBackStack() })
        }

        composable(DevLedgerDestinations.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(DevLedgerDestinations.PROFILE) {
            ProfileScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenSettings = {
                    navController.navigate(DevLedgerDestinations.SETTINGS)
                },
            )
        }

        // Main, bottom-nav-hosted screens
        composable(DevLedgerDestinations.DASHBOARD) {
            MainScaffold(navController, BottomNavTab.DASHBOARD) {
                DashboardScreen(
                    onOpenSnippets = {
                        navController.navigate(DevLedgerDestinations.SNIPPET_VAULT)
                    },
                    onOpenProfile = {
                        navController.navigate(DevLedgerDestinations.PROFILE)
                    },
                    onOpenFinance = {
                        navController.navigate(DevLedgerDestinations.FINANCE)
                    },
                    onOpenSettings = {
                        navController.navigate(DevLedgerDestinations.SETTINGS)
                    },
                    onOpenNotifications = {
                        navController.navigate(DevLedgerDestinations.NOTIFICATIONS)
                    },
                    onOpenSync = {
                        navController.navigate(DevLedgerDestinations.SYNC_STATUS)
                    },
                    onOpenRecycle = {
                        navController.navigate(DevLedgerDestinations.RECYCLE)
                    },
                    onCreateSnippet = {
                        navController.navigate(
                            DevLedgerDestinations.snippetEditorRoute()
                        )
                    },
                    onAddTransaction = {
                        navController.navigate(
                            DevLedgerDestinations.ADD_TRANSACTION
                        )
                    },
                )
            }
        }

        composable(DevLedgerDestinations.SNIPPET_VAULT) {
            MainScaffold(navController, BottomNavTab.SNIPPETS) {
                SnippetVaultScreen(
                    onOpenSnippet = { id -> navController.navigate(DevLedgerDestinations.snippetDetailRoute(id)) },
                    onOpenFoldersAndTags = { navController.navigate(DevLedgerDestinations.FOLDERS_TAGS) },
                    onOpenNotifications = { navController.navigate(DevLedgerDestinations.NOTIFICATIONS) },
                    onOpenDrawer = { /* Drawer not implemented */ },
                    onOpenSync = { navController.navigate(DevLedgerDestinations.SYNC_STATUS) },
                )
            }
        }


        composable(
            route = DevLedgerDestinations.SNIPPET_DETAIL,
            arguments = listOf(navArgument("snippetId") { type = NavType.StringType }),
        ) {
            SnippetDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(DevLedgerDestinations.snippetEditorRoute(id)) },
                onDuplicated = { newId -> navController.navigate(DevLedgerDestinations.snippetDetailRoute(newId)) { popUpTo(DevLedgerDestinations.SNIPPET_VAULT) } },
            )
        }

        composable(DevLedgerDestinations.FOLDERS_TAGS) {
            FoldersTagsScreen(onBack = { navController.popBackStack() })
        }

        composable(DevLedgerDestinations.FINANCE) {
            MainScaffold(
                navController = navController,
                active = BottomNavTab.FINANCE,
                onFabClick = { navController.navigate(DevLedgerDestinations.ADD_TRANSACTION) }
            ) {
                FinancialTrackerScreen(
                    onAddTransaction = { navController.navigate(DevLedgerDestinations.ADD_TRANSACTION) },
                    onViewIncome = { navController.navigate(DevLedgerDestinations.transactionListRoute("INCOME")) },
                    onViewExpenses = { navController.navigate(DevLedgerDestinations.transactionListRoute("EXPENSE")) },
                    onOpenTransaction = { id -> navController.navigate(DevLedgerDestinations.transactionDetailRoute(id)) },
                )
            }
        }

        composable(
            route = DevLedgerDestinations.TRANSACTION_LIST,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) {
            TransactionListScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate(DevLedgerDestinations.transactionDetailRoute(id)) },
            )
        }

        composable(
            route = DevLedgerDestinations.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) {
            TransactionDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(DevLedgerDestinations.NOTIFICATIONS) {
            MainScaffold(navController, BottomNavTab.NOTIFICATIONS) {
                NotificationsScreen()
            }
        }

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
                onBack = {
                    navController.popBackStack()
                },
                onOpenSync = {
                    navController.navigate(DevLedgerDestinations.SYNC_STATUS)
                },
                onOpenRecycle = {
                    navController.navigate(DevLedgerDestinations.RECYCLE)
                },
                onSignedOut = {
                    navController.navigate(DevLedgerDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(DevLedgerDestinations.SYNC_STATUS) {
            SyncScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(DevLedgerDestinations.RECYCLE) {
            RecycleScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

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
        containerColor = Color.Transparent,
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

@HiltViewModel
class AuthEntryPoint @Inject constructor(
    private val authRepository: AuthRepository,
    private val pinStorage: PinStorage,
) : ViewModel() {
    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    suspend fun needsSecuritySetup(): Boolean {
        if (!isLoggedIn()) return false

        return try {
            val user = withTimeoutOrNull(5000) {
                authRepository.currentUser.first { it != null }
            }

            if (user == null) {
                Log.w("AuthEntryPoint", "needsSecuritySetup: User timeout, assuming verification is enough")
                return false
            }

            if (pinStorage.hasPin(user.email)) {
                Log.i("AuthEntryPoint", "needsSecuritySetup: Local PIN found for ${user.email}")
                return false
            }

            val needsSetup = user.pinHash == null
            Log.i("AuthEntryPoint", "needsSecuritySetup: email=${user.email}, needsSetup=$needsSetup")
            needsSetup
        } catch (e: Exception) {
            Log.e("AuthEntryPoint", "needsSecuritySetup: Failed", e)
            false
        }
    }
}