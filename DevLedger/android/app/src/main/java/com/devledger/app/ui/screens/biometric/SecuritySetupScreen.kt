package com.devledger.app.ui.screens.biometric

import com.devledger.app.util.localization.UiText

import com.devledger.app.util.localization.rememberAppStrings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.devledger.app.util.DevLedgerResult
import com.devledger.app.R
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecuritySetupViewModel @Inject constructor(
    private val pinStorage: PinStorage,
    private val authRepository: AuthRepository,
) : ViewModel() {
    var pin by mutableStateOf("")
    var confirmPin by mutableStateOf("")
    var isFingerprintEnabled by mutableStateOf(false)
    var isFingerprintVerified by mutableStateOf(false)
    var errorText by mutableStateOf<UiText?>(null)
    var isSaved by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var isLoggedOut by mutableStateOf(false)

    fun saveSetup(): Job? {
        if (isSaving) return null
        if (pin.length != 4 || !pin.all { it in '0'..'9' }) {
            errorText = UiText.Resource(R.string.lang_pin_must_be_exactly_4_digits)
            return null
        }
        if (pin != confirmPin) {
            errorText = UiText.Resource(R.string.lang_pins_do_not_match)
            return null
        }
        if (isFingerprintEnabled && !isFingerprintVerified) {
            errorText = UiText.Resource(R.string.lang_please_verify_your_fingerprint_to_enable_biometric_access)
            return null
        }

        errorText = null
        isSaving = true
        val requestedPin = pin
        val requestedBiometrics = isFingerprintEnabled && isFingerprintVerified
        return viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                    ?: error("Sign in before configuring security.")
                val credentials = withContext(Dispatchers.Default) {
                    pinStorage.createPinCredentials(requestedPin)
                }
                val result = withTimeout(10_000L) {
                    authRepository.saveSecurityProfile(credentials.hash, credentials.salt,
                        requestedBiometrics)
                }
                when (result) {
                    is DevLedgerResult.Error -> errorText =
                        UiText.Resource(R.string.lang_security_setup_could_not_be_saved_check_your_connection_and_)
                    is DevLedgerResult.Success -> {
                        check(authRepository.currentUser.first()?.userId == user.userId) {
                            "The account changed. Sign in again."
                        }
                        pinStorage.savePinCredentials(credentials, user.email)
                        pinStorage.setFingerprintRegistered(requestedBiometrics, user.email)
                        isSaved = true
                    }
                }
            } catch (_: TimeoutCancellationException) {
                errorText = UiText.Resource(R.string.lang_saving_security_setup_timed_out_check_your_connection_and_re)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorText = UiText.Resource(R.string.lang_security_setup_could_not_be_saved_check_your_connection_and_)
            } finally {
                isSaving = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            isLoggedOut = true
        }
    }
}

@Composable
fun SecuritySetupScreen(
    onComplete: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SecuritySetupViewModel = hiltViewModel()
) {
    val strings = rememberAppStrings()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val helper = remember(activity) { activity?.let { BiometricAuthHelper(it) } }
    DisposableEffect(helper) { onDispose { helper?.cancel() } }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.isSaved) {
        if (viewModel.isSaved) onComplete()
    }

    LaunchedEffect(viewModel.isLoggedOut) {
        if (viewModel.isLoggedOut) onLogout()
    }

    fun triggerBiometricVerification() {
        if (helper == null) {
            viewModel.errorText = UiText.Resource(R.string.bio_unavailable)
            return
        }
        if (helper.canAuthenticate()) {
            helper.authenticate(
                title = context.getString(R.string.bio_enable_title),
                subtitle = context.getString(R.string.bio_enable_subtitle),
                onSuccess = {
                    viewModel.isFingerprintVerified = true
                    viewModel.errorText = null
                },
                onError = { message ->
                    viewModel.errorText = UiText.Resource(R.string.lang_verification_failed, listOf(message))
                    viewModel.isFingerprintVerified = false
                },
                onFailed = { message -> viewModel.errorText = UiText.Dynamic(message) },
            )
        } else {
            viewModel.errorText = UiText.Resource(R.string.lang_biometric_hardware_unavailable)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = DevLedgerColors.SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DevLedgerColors.AccentBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(strings.get(R.string.lang_logout), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(strings.get(R.string.lang_are_you_sure_you_want_to_log_out_and_return_to_the_home_scre), color = DevLedgerColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout() }) {
                    Text(strings.get(R.string.lang_logout), color = DevLedgerColors.ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.get(R.string.lang_cancel), color = DevLedgerColors.TextMuted)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Action Bar with Back Arrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { showLogoutDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.get(R.string.lang_back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = strings.get(R.string.lang_security_configuration),
                color = DevLedgerColors.AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = strings.get(R.string.lang_registration_step),
                color = DevLedgerColors.AccentBlue.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = DevLedgerColors.AccentBlue,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.get(R.string.lang_register_your_security_vault),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = strings.get(R.string.lang_set_up_your_unique_security_pin_and_register_your_fingerprin),
            color = DevLedgerColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
        )

        // PIN Fields with visual validation colors
        val pinMatch = viewModel.pin.length == 4 && viewModel.pin == viewModel.confirmPin

        DevLedgerTextField(
            value = viewModel.pin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) viewModel.pin = it },
            label = strings.get(R.string.lang_create_4_digit_pin),
            placeholder = "0000",
            isPassword = true,
            isValid = viewModel.pin.length == 4,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        DevLedgerTextField(
            value = viewModel.confirmPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) viewModel.confirmPin = it },
            label = strings.get(R.string.lang_confirm_4_digit_pin),
            placeholder = "0000",
            isPassword = true,
            isValid = pinMatch,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        // Register Fingerprint Toggle with Visual Progress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DevLedgerColors.SurfaceCard)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                CircularProgressIndicator(
                    progress = { if (viewModel.isFingerprintVerified) 1.0f else 0.0f },
                    color = DevLedgerColors.SuccessGreen,
                    strokeWidth = 3.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = if (viewModel.isFingerprintVerified) Icons.Filled.CheckCircle else Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = if (viewModel.isFingerprintVerified) DevLedgerColors.SuccessGreen else DevLedgerColors.AccentBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.get(R.string.lang_register_biometrics),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (viewModel.isFingerprintVerified) strings.get(R.string.lang_fingerprint_registered_successfully) else strings.get(R.string.lang_enable_secure_biometric_quick_access),
                    color = if (viewModel.isFingerprintVerified) DevLedgerColors.SuccessGreen else DevLedgerColors.TextMuted,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = viewModel.isFingerprintEnabled,
                enabled = !viewModel.isSaving,
                onCheckedChange = {
                    helper?.cancel()
                    viewModel.isFingerprintVerified = false
                    viewModel.isFingerprintEnabled = it
                    if (it && !viewModel.isFingerprintVerified) {
                        triggerBiometricVerification()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = DevLedgerColors.AccentBlue
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.errorText != null) {
            Text(
                text = viewModel.errorText!!.resolve(strings),
                color = DevLedgerColors.ErrorRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Large Bottom Button: Scan Fingerprint (if enabled but not verified) or Save
        if (viewModel.isFingerprintEnabled && !viewModel.isFingerprintVerified) {
            DevLedgerPrimaryButton(
                text = strings.get(R.string.lang_scan_fingerprint_to_register),
                onClick = { triggerBiometricVerification() }
            )
        } else {
            DevLedgerPrimaryButton(
                text = strings.get(R.string.lang_save_continue),
                enabled = !viewModel.isSaving,
                loading = viewModel.isSaving,
                onClick = { viewModel.saveSetup() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Red Logout Button as requested
        Button(
            onClick = { showLogoutDialog = true },
            enabled = !viewModel.isSaving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = DevLedgerColors.ErrorRed
            ),
            border = BorderStroke(1.dp, DevLedgerColors.ErrorRed.copy(alpha = 0.5f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.get(R.string.lang_logout_cancel_setup), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
