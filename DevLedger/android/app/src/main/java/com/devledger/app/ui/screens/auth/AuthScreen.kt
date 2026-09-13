package com.devledger.app.ui.screens.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import com.devledger.app.BuildConfig
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerSecondaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.material3.MaterialTheme

private const val TAG = "AuthScreen"

/**
 * Authentication screen: Google/GitHub SSO buttons plus an email fallback, matching
 * the "Authentication Screen" mockup (Figure 5, Part 1 design doc) and satisfying the
 * "register and log in ... using single sign-on (SSO)" core requirement.
 *
 * SSO WIRING STATUS:
 * - Google: real — launches the on-device Google Sign-In flow via play-services-auth.
 * - GitHub: real — opens GitHub's authorize page in a Custom Tab; the resulting
 *   redirect is caught by [com.devledger.app.MainActivity] (outside the Compose tree,
 *   since it arrives as a plain Intent) and republished here via [GitHubOAuthEvents].
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                Log.i(TAG, "Google Sign-In succeeded for ${account.email}, forwarding ID token to API")
                viewModel.signInWithGoogle(idToken)
            } else {
                Log.w(TAG, "Google Sign-In returned no ID token")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In failed with status code ${e.statusCode}", e)
        }
    }

    // Collects the GitHub redirect result that MainActivity received as a deep-link
    // Intent. LaunchedEffect(Unit) means this subscribes once and stays subscribed
    // for the lifetime of this composable, which is what we want — the user may be
    // away from the app (in the Custom Tab) for several seconds.
    LaunchedEffect(Unit) {
        GitHubOAuthEvents.events.collect { result ->
            when (result) {
                is GitHubOAuthResult.Success -> {
                    Log.i(TAG, "GitHub OAuth redirect received an authorization code, forwarding to API")
                    viewModel.signInWithGitHub(result.code)
                }
                is GitHubOAuthResult.Failure -> {
                    Log.w(TAG, "GitHub OAuth redirect failed: ${result.reason}")
                    viewModel.onGitHubOAuthFailure(result.reason)
                }
            }
        }
    }

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = DevLedgerColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            color = DevLedgerColors.AccentBlue,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 48.dp, top = 2.dp),
        )

        Text(
            text = "Welcome back!",
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Sign in to continue to your workspace.",
            color = DevLedgerColors.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp, top = 4.dp)
        )

        DevLedgerSecondaryButton(
            text = "Sign in with Google",
            onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
        )
        Spacer(modifier = Modifier.height(16.dp))
        DevLedgerSecondaryButton(
            text = "Sign in with GitHub",
            onClick = {
                val authorizeUrl = "https://github.com/login/oauth/authorize" +
                        "?client_id=${BuildConfig.GITHUB_CLIENT_ID}" +
                        "&redirect_uri=devledger://oauth/github/callback" +
                        "&scope=read:user%20user:email"
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authorizeUrl))
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDividerLine()
            Text("  OR  ", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
            HorizontalDividerLine()
        }

        DevLedgerTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "",
            placeholder = "Email",
            isError = state.emailError != null,
            supportingText = state.emailError,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        DevLedgerTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "",
            placeholder = "Password",
            isPassword = true,
            isError = state.passwordError != null,
            supportingText = state.passwordError,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage!!,
                color = DevLedgerColors.ErrorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp),
            )
        }

        DevLedgerPrimaryButton(
            text = "Sign in with email",
            onClick = viewModel::signInWithEmail,
            loading = state.isLoading,
        )

        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("Don't have an account? ", color = DevLedgerColors.TextMuted, fontSize = 14.sp)
            Text(
                text = "Create one",
                color = DevLedgerColors.AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onNavigateToRegister),
            )
        }
    }
}

@Composable
private fun RowScope.HorizontalDividerLine() {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(DevLedgerColors.Border),
    )
}