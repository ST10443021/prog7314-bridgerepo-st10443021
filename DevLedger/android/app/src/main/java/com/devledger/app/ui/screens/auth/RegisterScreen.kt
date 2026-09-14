package com.devledger.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.ui.components.*
import com.devledger.app.ui.theme.DevLedgerColors

/**
 * Registration screen. Implementation follows the "Register Screen" design mockup.
 * Includes real-time validation, password strength meter, and API error handling.
 *
 * References:
 * IIE (2024). Best Practices for Mobile Registration Forms. IIE Harvard Format.
 */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        // Back Button
        Row(
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = DevLedgerColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Back to sign in",
                color = DevLedgerColors.TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Logo and Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DevLedgerColors.SurfaceCardAlt, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = DevLedgerColors.AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "DevLedger",
                color = DevLedgerColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Create your account",
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Your personal developer utility and asset vault.",
            color = DevLedgerColors.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // Form Fields
        Text("FULL NAME", color = DevLedgerColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        DevLedgerTextField(
            value = state.fullName,
            onValueChange = viewModel::onFullNameChange,
            label = "",
            placeholder = "Alex Morgan",
            leadingIcon = Icons.Filled.Person,
            isValid = state.isFullNameValid,
            isError = state.fullNameError != null,
            supportingText = state.fullNameError,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        Text("EMAIL ADDRESS", color = DevLedgerColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        DevLedgerTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "",
            placeholder = "alex@devledger.io",
            leadingIcon = Icons.Filled.Email,
            isValid = state.isEmailValid,
            isError = state.emailError != null,
            supportingText = state.emailError,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        Text("PASSWORD", color = DevLedgerColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        DevLedgerTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "",
            placeholder = "Min. 8 characters",
            isPassword = true,
            leadingIcon = Icons.Filled.Lock,
            isValid = state.isPasswordValid,
            isError = state.passwordError != null,
            supportingText = state.passwordError,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        PasswordStrengthMeter(
            strength = state.passwordStrength,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // API Error Banner
        if (state.errorMessage != null) {
            Surface(
                color = DevLedgerColors.ErrorRedBg,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Text(
                    text = state.errorMessage!!,
                    color = DevLedgerColors.ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        DevLedgerPrimaryButton(
            text = "Create account",
            onClick = viewModel::register,
            loading = state.isLoading,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "By creating an account you agree to our",
                color = DevLedgerColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Terms of Service",
                    color = DevLedgerColors.AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        onClick = onTermsClick
                    )
                )

                Text(
                    text = " and ",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 12.sp
                )

                Text(
                    text = "Privacy Policy",
                    color = DevLedgerColors.AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        onClick = onPrivacyPolicyClick
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row {
                Text("Already have an account? ", color = DevLedgerColors.TextMuted, fontSize = 14.sp)
                Text(
                    text = "Sign in",
                    color = DevLedgerColors.AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
        }
    }
}