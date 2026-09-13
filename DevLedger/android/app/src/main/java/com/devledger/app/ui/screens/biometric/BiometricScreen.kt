package com.devledger.app.ui.screens.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerSecondaryButton
import com.devledger.app.ui.theme.DevLedgerColors

/**
 * Device-level protection gate shown immediately after sign-in and whenever the app
 * resumes from the background — Figure "Biometric Authentication Screen" in the Part 1
 * design doc. Devices without enrolled biometrics automatically fall back to a
 * PIN/pattern/password challenge via the same BiometricPrompt call.
 */
@Composable
fun BiometricScreen(
    onVerified: () -> Unit,
    onUsePinInstead: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.biometric_title),
            color = DevLedgerColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.biometric_subtitle),
            color = DevLedgerColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp, start = 16.dp, end = 16.dp),
        )

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(DevLedgerColors.AccentBlueDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(72.dp),
            )
        }

        Text(
            text = stringResource(R.string.biometric_touch_sensor),
            color = DevLedgerColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
        )

        if (errorText != null) {
            Text(errorText!!, color = DevLedgerColors.ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        DevLedgerCard(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(stringResource(R.string.biometric_data_safe_title), color = DevLedgerColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.biometric_data_safe_body), color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        DevLedgerPrimaryButton(
            text = stringResource(R.string.biometric_title),
            onClick = {
                errorText = null

                if (activity == null) {
                    errorText = "Biometric authentication is unavailable."
                    return@DevLedgerPrimaryButton
                }

                val biometricHelper = BiometricAuthHelper(activity)

                if (!biometricHelper.canAuthenticate()) {
                    errorText = when (biometricHelper.authenticationStatus()) {
                        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                            "This device does not have a fingerprint sensor."

                        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                            "The fingerprint sensor is currently unavailable."

                        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            "No fingerprint is enrolled. Add one in your device settings first."

                        else ->
                            "Fingerprint authentication is unavailable on this device."
                    }

                    return@DevLedgerPrimaryButton
                }

                biometricHelper.authenticate(
                    onSuccess = {
                        errorText = null
                        onVerified()
                    },
                    onError = { message ->
                        errorText = message
                    }
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        DevLedgerSecondaryButton(text = stringResource(R.string.biometric_use_pin), onClick = onUsePinInstead)
    }
}
