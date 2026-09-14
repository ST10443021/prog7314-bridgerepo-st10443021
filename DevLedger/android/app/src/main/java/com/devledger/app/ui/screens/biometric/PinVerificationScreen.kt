package com.devledger.app.ui.screens.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.ui.theme.DevLedgerColors

@Composable
fun PinVerificationScreen(
    onBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: PinVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Automatically trigger verification when 4 digits are complete
    LaunchedEffect(state.pin) {
        if (state.pin.length == 4) {
            viewModel.verifyPin()
        }
    }

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
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = "PIN / Password",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Enter your PIN or password",
            color = DevLedgerColors.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(0.4f))

        // Four rounded circle dot indicators matching the design mockup exactly
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < state.pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFilled) DevLedgerColors.AccentBlue else Color(0xFF2A3A55)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage!!,
                color = DevLedgerColors.ErrorRed,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.weight(0.6f))

        // Custom Numeric Dial Pad Grid layout matching the requested layout image
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            keys.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowKeys.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.2f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (key.isNotEmpty()) DevLedgerColors.SurfaceCard else Color.Transparent
                                )
                                .clickable(enabled = key.isNotEmpty()) {
                                    if (key == "⌫") {
                                        if (state.pin.isNotEmpty()) {
                                            viewModel.onPinChange(state.pin.dropLast(1))
                                        }
                                    } else {
                                        if (state.pin.length < 4) {
                                            viewModel.onPinChange(state.pin + key)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key.isNotEmpty()) {
                                Text(
                                    text = key,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}