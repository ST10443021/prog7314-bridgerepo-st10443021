package com.devledger.app.ui.screens.biometric

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.devledger.app.R
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class BiometricViewModel @Inject constructor(
    private val pinStorage: PinStorage,
    private val authRepository: AuthRepository,
) : ViewModel() {
    fun isFingerprintRegistered(): Boolean {
        return runBlocking {
            val email = authRepository.currentUser.first()?.email
            pinStorage.isFingerprintRegistered(email)
        }
    }
}

@Composable
fun BiometricScreen(
    onVerified: () -> Unit,
    onUsePinInstead: () -> Unit,
    viewModel: BiometricViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var errorText by remember { mutableStateOf<String?>(null) }
    val isRegistered = remember { viewModel.isFingerprintRegistered() }

    fun triggerBiometric() {
        if (!isRegistered) {
            onUsePinInstead()
            return
        }
        errorText = null
        if (activity != null) {
            val helper = BiometricAuthHelper(activity)
            if (helper.canAuthenticate()) {
                helper.authenticate(
                    title = "Verify Identity",
                    subtitle = "Touch the sensor to unlock your workspace",
                    onSuccess = { onVerified() },
                    onError = { message -> errorText = message }
                )
            } else {
                errorText = "Biometric not available on this device."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp).clickable { /* Keep on identity page for safety */ }
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = DevLedgerColors.AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("DevLedger", color = DevLedgerColors.AccentBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.weight(1.2f))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Verify Your Identity",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (isRegistered) "Use your biometric to securely access your DevLedger vault" 
                   else "Please use your security PIN to access your vault",
            color = DevLedgerColors.TextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp, start = 20.dp, end = 20.dp)
        )

        if (isRegistered) {
            // Large Glowing Fingerprint with Full Ring per request
            Box(
                contentAlignment = Alignment.Center, 
                modifier = Modifier.size(200.dp).clickable { triggerBiometric() }
            ) {
                CircularProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier.fillMaxSize(),
                    color = DevLedgerColors.AccentBlue,
                    strokeWidth = 4.dp,
                    trackColor = Color(0xFF1E2A3E)
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F2550).copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = DevLedgerColors.AccentBlue,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Touch the fingerprint sensor",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Make sure your finger is clean and dry",
                color = DevLedgerColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DevLedgerColors.Border))
                Text("   OR   ", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DevLedgerColors.Border))
            }
        } else {
            // If not registered, show a lock icon instead of fingerprint
            Box(
                contentAlignment = Alignment.Center, 
                modifier = Modifier.size(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2A3E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = DevLedgerColors.TextMuted,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(64.dp))
        }

        // Feature Cards - Removed Face ID
        BiometricActionCard(
            icon = Icons.Default.Lock,
            title = "Your data stays secure",
            subtitle = "Your biometric data is stored only on this device and never shared with DevLedger.",
            showChevron = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        BiometricActionCard(
            icon = Icons.Default.Info,
            title = "Having trouble?",
            subtitle = "Try using your pattern, PIN or password instead.",
            trailingText = "Use PIN",
            onTrailingClick = onUsePinInstead
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Updated Bottom Button
        if (isRegistered) {
            DevLedgerPrimaryButton(
                text = "Scan Fingerprint Again",
                onClick = { triggerBiometric() }
            )
        } else {
            DevLedgerPrimaryButton(
                text = "Use PIN Verification",
                onClick = onUsePinInstead
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Bottom PIN Button matching design
        OutlinedButton(
            onClick = onUsePinInstead,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DevLedgerColors.AccentBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.AccentBlue)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Use PIN or Password", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        if (errorText != null) {
            Text(
                text = errorText!!,
                color = DevLedgerColors.ErrorRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Auto-trigger biometric prompt on screen enter if registered
    LaunchedEffect(Unit) {
        if (isRegistered) triggerBiometric()
    }
}

@Composable
fun BiometricActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E2A3E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = DevLedgerColors.TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (showChevron) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DevLedgerColors.TextMuted)
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = DevLedgerColors.AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onTrailingClick?.invoke() }
            )
        }
    }
}