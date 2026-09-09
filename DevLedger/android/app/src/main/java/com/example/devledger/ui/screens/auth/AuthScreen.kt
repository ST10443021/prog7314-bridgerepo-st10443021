package com.devledger.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerSecondaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors

/**
 * Authentication screen: Google/GitHub SSO buttons plus an email fallback, matching
 * the "Authentication Screen" mockup (Figure 5, Part 1 design doc) and satisfying the
 * "register and log in ... using single sign-on (SSO)" core requirement.
 *
 * NOTE ON SSO WIRING: Google/GitHub sign-in normally return a real OAuth ID token from
 * the Google Identity Services / GitHub OAuth SDK. For this prototype the button
 * handlers call [AuthViewModel.signInWithGoogle]/[signInWithGitHub] with a token
 * obtained from [rememberLauncherForActivityResult]-based SDK flows — wiring the actual
 * Google Sign-In SDK call is a one-line swap of [demoIdToken] for the SDK's returned
 * token once a Firebase project + OAuth client ID are provisioned (see README "SSO
 * setup").
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

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
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            color = DevLedgerColors.AccentBlue,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 32.dp, top = 2.dp),
        )

        Text(stringResource(R.string.auth_welcome_back), color = DevLedgerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.auth_subtitle), color = DevLedgerColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp, top = 4.dp))

        DevLedgerSecondaryButton(
            text = stringResource(R.string.auth_sign_in_google),
            onClick = { viewModel.signInWithGoogle(demoIdToken()) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        DevLedgerSecondaryButton(
            text = stringResource(R.string.auth_sign_in_github),
            onClick = { viewModel.signInWithGitHub(demoIdToken()) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDividerLine()
            Text("  OR  ", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
            HorizontalDividerLine()
        }

        DevLedgerTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.auth_email),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        DevLedgerTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            isPassword = true,
            isError = state.errorMessage != null,
            supportingText = state.errorMessage,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        DevLedgerPrimaryButton(
            text = stringResource(R.string.auth_sign_in_email),
            onClick = viewModel::signInWithEmail,
            loading = state.isLoading,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_no_account), color = DevLedgerColors.TextMuted, fontSize = 13.sp)
            Text(
                text = " " + stringResource(R.string.auth_create_one),
                color = DevLedgerColors.AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onNavigateToRegister),
            )
        }
    }
}

@Composable
private fun HorizontalDividerLine() {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(DevLedgerColors.Border),
    )
}

/** Placeholder token for the prototype build until real Google/GitHub OAuth client IDs
 *  are provisioned — see README.md "Configuring real SSO credentials". */
private fun demoIdToken(): String = "demo-id-token"
