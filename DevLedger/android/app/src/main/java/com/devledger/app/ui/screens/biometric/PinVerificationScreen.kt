package com.devledger.app.ui.screens.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors

@Composable
fun PinVerificationScreen(
    onBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: PinVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isVerified) {
        if (state.isVerified) {
            onVerified()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = DevLedgerColors.TextSecondary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Back",
                color = DevLedgerColors.TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = DevLedgerColors.AccentBlue,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Verify with PIN",
            color = DevLedgerColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter the 4-digit PIN you created when registering your DevLedger account.",
            color = DevLedgerColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        DevLedgerTextField(
            value = state.pin,
            onValueChange = viewModel::onPinChange,
            label = "",
            placeholder = "Enter 4-digit PIN",
            isPassword = true,
            leadingIcon = Icons.Filled.Lock,
            isValid = state.pin.length == 4 && state.errorMessage == null,
            isError = state.errorMessage != null,
            supportingText = state.errorMessage,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        DevLedgerPrimaryButton(
            text = "Verify PIN",
            onClick = viewModel::verifyPin,
            loading = state.isVerifying
        )

        Spacer(modifier = Modifier.height(24.dp))

        DevLedgerCard {
            Text(
                text = "Your PIN is verified securely on this device.",
                color = DevLedgerColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}