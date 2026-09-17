package com.devledger.app.ui.screens.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import com.devledger.app.BuildConfig
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val TAG = "AuthScreen"

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showEmailForm by remember { mutableStateOf(false) }

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
                viewModel.onGoogleSignInFailure("No ID token returned from Google.")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In failed with status code ${e.statusCode}", e)
            viewModel.onGoogleSignInFailure(if (e.statusCode == 12501) "Sign-in cancelled." else "Google Sign-In failed (${e.statusCode}).")
        }
    }

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
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Center Shield Logo (R.drawable.ic_launcher_foreground is the high-res shield)
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White)) {
                    append("Dev")
                }
                withStyle(style = SpanStyle(color = DevLedgerColors.AccentBlue)) {
                    append("Ledger")
                }
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            color = DevLedgerColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 24.dp),
        )

        Text(
            text = "Welcome back!",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Sign in to continue to your workspace.",
            color = DevLedgerColors.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp, top = 4.dp),
            textAlign = TextAlign.Center
        )

        // Center Laptop and security workspace preview graphic illustration
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F1320)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                alpha = 0.2f
            )
            // Decorative elements mirroring the screenshot illustration
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1D6FE8).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("</>", color = DevLedgerColors.AccentBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp, 20.dp).background(Color(0xFF1D6FE8).copy(alpha = 0.2f)))
                        Box(modifier = Modifier.size(8.dp, 24.dp).background(Color(0xFF1D6FE8).copy(alpha = 0.3f)))
                        Box(modifier = Modifier.size(8.dp, 16.dp).background(Color(0xFF1D6FE8).copy(alpha = 0.15f)))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1D6FE8).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Custom Styled Google Sign in Button with Loading state
        Button(
            onClick = { 
                viewModel.onGoogleSignInClicked()
                // Only sign out if an account is already signed in to allow account switcher
                val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                if (lastAccount != null) {
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                } else {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isGoogleLoading && !state.isGitHubLoading
        ) {
            if (state.isGoogleLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Gray, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Styled GitHub Button with Loading state
        Button(
            onClick = {
                viewModel.onGitHubSignInClicked()
                val authorizeUrl = "https://github.com/login/oauth/authorize" +
                        "?client_id=${BuildConfig.GITHUB_CLIENT_ID}" +
                        "&redirect_uri=devledger://oauth/github/callback" +
                        "&scope=read:user%20user:email"
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authorizeUrl))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D2235)),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isGoogleLoading && !state.isGitHubLoading
        ) {
            if (state.isGitHubLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github_logo),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Sign in with GitHub", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(DevLedgerColors.Border))
            Text("   OR   ", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
            Box(modifier = Modifier.weight(1f).height(1.dp).background(DevLedgerColors.Border))
        }

        if (!showEmailForm) {
            Row(
                modifier = Modifier
                    .clickable { showEmailForm = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Filled.Email, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with email", color = DevLedgerColors.AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        } else {
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
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    color = DevLedgerColors.ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            DevLedgerPrimaryButton(
                text = "Sign in with email",
                onClick = viewModel::signInWithEmail,
                loading = state.isLoading,
            )
        }

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