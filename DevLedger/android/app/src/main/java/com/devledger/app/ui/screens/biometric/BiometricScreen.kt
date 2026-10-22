package com.devledger.app.ui.screens.biometric

import com.devledger.app.util.localization.rememberAppStrings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.R
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiometricViewModel @Inject constructor(
    private val pinStorage: PinStorage,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val isFingerprintRegistered = mutableStateOf<Boolean?>(null)

    init {
        viewModelScope.launch {
            // Never enter the workspace from a preference alone.
            authRepository.currentUser.collect { user ->
                isFingerprintRegistered.value = user != null && user.biometricEnabled &&
                    pinStorage.isFingerprintRegistered(user.email)
            }
        }
    }
}

@Composable
fun BiometricScreen(
    onVerified: () -> Unit,
    onUsePinInstead: () -> Unit,
    viewModel: BiometricViewModel = hiltViewModel()
) {
    val strings = rememberAppStrings()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val helper = remember(activity) { activity?.let { BiometricAuthHelper(it) } }
    DisposableEffect(helper) { onDispose { helper?.cancel() } }
    var errorText by remember { mutableStateOf<String?>(null) }
    val isRegistered = viewModel.isFingerprintRegistered.value

    if (isRegistered == null) {
        // Loading state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DevLedgerColors.AccentBlue)
        }
        return
    }

    fun triggerBiometric() {
        if (!isRegistered) {
            onUsePinInstead()
            return
        }
        errorText = null
        if (helper != null) {
            if (helper.canAuthenticate()) {
                helper.authenticate(
                    title = context.getString(R.string.bio_verify_title),
                    subtitle = context.getString(R.string.bio_verify_subtitle),
                    onSuccess = { onVerified() },
                    onError = { message -> errorText = message },
                    onFailed = { message -> errorText = message },
                )
            } else {
                errorText = context.getString(R.string.bio_unavailable)
            }
        } else {
            errorText = context.getString(R.string.bio_unavailable)
        }
    }

    fun usePin() {
        helper?.cancel()
        onUsePinInstead()
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
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("DevLedger", color = DevLedgerColors.AccentBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.weight(1.2f))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = strings.get(R.string.lang_verify_your_identity),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (isRegistered) strings.get(R.string.lang_use_your_biometric_to_securely_access_your_devledger_vault)
                   else strings.get(R.string.lang_please_use_your_security_pin_to_access_your_vault),
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
                text = strings.get(R.string.lang_touch_the_fingerprint_sensor),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = strings.get(R.string.lang_make_sure_your_finger_is_clean_and_dry),
                color = DevLedgerColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DevLedgerColors.Border))
                Text(strings.get(R.string.lang_or), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
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
            title = strings.get(R.string.lang_your_data_stays_secure),
            subtitle = stringResource(R.string.bio_privacy),
            showChevron = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        BiometricActionCard(
            icon = Icons.Default.Info,
            title = strings.get(R.string.lang_having_trouble),
            subtitle = strings.get(R.string.lang_use_your_devledger_security_pin_instead),
            trailingText = strings.get(R.string.lang_use_pin),
            onTrailingClick = ::usePin
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Updated Bottom Button
        if (isRegistered) {
            DevLedgerPrimaryButton(
                text = strings.get(R.string.lang_scan_fingerprint_again),
                onClick = { triggerBiometric() }
            )
        } else {
            DevLedgerPrimaryButton(
                text = strings.get(R.string.lang_use_pin_verification),
                onClick = ::usePin
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Bottom PIN Button matching design
        OutlinedButton(
            onClick = ::usePin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DevLedgerColors.AccentBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.AccentBlue)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(strings.get(R.string.lang_use_devledger_pin), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
