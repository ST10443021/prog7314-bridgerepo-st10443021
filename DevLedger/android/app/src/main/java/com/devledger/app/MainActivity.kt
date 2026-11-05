package com.devledger.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.devledger.app.notifications.NotificationDeepLink
import com.devledger.app.ui.navigation.DevLedgerNavHost
import com.devledger.app.ui.screens.auth.GitHubOAuthEvents
import com.devledger.app.ui.screens.auth.GitHubOAuthResult
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity

// Reference scope: Android application component and window insets. (Google Developers, n.d.a; n.d.t; n.d.u). See REFERENCES-CODE.md for full entries.


/**
 * Single-activity host for the whole DevLedger Compose UI. Navigation between all
 * screens is handled by [DevLedgerNavHost] using Navigation-Compose, matching the
 * navigation wireflow diagram from the Part 1 Planning and Design document.
 *
 * Also the landing point for the GitHub OAuth redirect (devledger://oauth/github/callback),
 * which arrives as a plain VIEW Intent rather than through Compose navigation — see
 * [handleGitHubRedirect]. Requires android:launchMode="singleTask" in the manifest so
 * [onNewIntent] fires instead of a second MainActivity instance being created.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // We record local notifications in Room regardless of system tray permissions,
        // so no special handling is needed if denied — they'll still see the badge.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DevLedger only ships a dark UI, so both system bars are forced to the "dark" style
        // (transparent scrim, light icons). With the default auto style a phone set to light mode
        // would draw dark status-bar icons on top of the dark app background (Google Developers,
        // n.d.t).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            DevLedgerApp()
        }
        // Cold start: the app was not already running when GitHub redirected back.
        handleGitHubRedirect(intent)
        handleNotificationTap(intent)

        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: this is the path that fires for the normal case — the Custom
        // Tab was sitting on top of the already-running app.
        handleGitHubRedirect(intent)
        handleNotificationTap(intent)
    }

    /** A tap on a DevLedger notification asks the nav host to open the Notifications list. */
    private fun handleNotificationTap(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationDeepLink.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            NotificationDeepLink.request()
            intent.removeExtra(NotificationDeepLink.EXTRA_OPEN_NOTIFICATIONS)
        }
    }

    private fun handleGitHubRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "devledger" || uri.host != "oauth") return

        lifecycleScope.launch {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            when {
                code != null -> GitHubOAuthEvents.emit(GitHubOAuthResult.Success(code))
                error != null -> GitHubOAuthEvents.emit(GitHubOAuthResult.Failure(error))
                else -> GitHubOAuthEvents.emit(GitHubOAuthResult.Failure("No code or error in redirect URI"))
            }
        }
    }
}

@Composable
private fun DevLedgerApp() {
    DevLedgerTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DevLedgerColors.BgDeep),
            color = DevLedgerColors.BgDeep,
        ) {
            // Edge-to-edge is enabled in onCreate, so the status bar and the phone's navigation bar
            // are drawn over the app. Applying the system-bar insets once here keeps every screen
            // (splash, auth, dashboard, finance, editors, ...) clear of both bars. systemBarsPadding
            // consumes the insets, so screens that call statusBarsPadding()/imePadding() themselves
            // are not padded twice (Google Developers, n.d.t; n.d.u).
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                val navController = rememberNavController()
                DevLedgerNavHost(navController = navController)
            }
        }
    }
}