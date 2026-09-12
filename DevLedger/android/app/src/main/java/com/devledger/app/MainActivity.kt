package com.devledger.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.devledger.app.ui.navigation.DevLedgerNavHost
import com.devledger.app.ui.screens.auth.GitHubOAuthEvents
import com.devledger.app.ui.screens.auth.GitHubOAuthResult
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevLedgerApp()
        }
        // Cold start: the app was not already running when GitHub redirected back.
        handleGitHubRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: this is the path that fires for the normal case — the Custom
        // Tab was sitting on top of the already-running app.
        handleGitHubRedirect(intent)
    }

    private fun handleGitHubRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "devledger" || uri.host != "oauth") return

        Log.i("MainActivity", "Received GitHub OAuth redirect: $uri")

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
        ) {
            val navController = rememberNavController()
            DevLedgerNavHost(navController = navController)
        }
    }
}