package com.devledger.app.ui.screens.biometric

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
import androidx.fragment.app.FragmentActivity
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
    var errorText by mutableStateOf<String?>(null)
    var isSaved by mutableStateOf(false)
    var isLoggedOut by mutableStateOf(false)

    fun saveSetup() {
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            errorText = "PIN must be exactly 4 digits."
            return
        }
        if (pin != confirmPin) {
            errorText = "PINs do not match."
            return
        }
        if (isFingerprintEnabled && !isFingerprintVerified) {
            errorText = "Please verify your fingerprint to enable biometric access."
            return
        }
        
        errorText = null
        viewModelScope.launch {
            val email = authRepository.currentUser.first()?.email
            pinStorage.savePin(pin, email)
            pinStorage.setFingerprintRegistered(isFingerprintEnabled, email)
            isSaved = true
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
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.isSaved) {
        if (viewModel.isSaved) onComplete()
    }
    
    LaunchedEffect(viewModel.isLoggedOut) {
        if (viewModel.isLoggedOut) onLogout()
    }

    fun triggerBiometricVerification() {
        if (activity == null) return
        val helper = BiometricAuthHelper(activity)
        if (helper.canAuthenticate()) {
            helper.authenticate(
                title = "Link Fingerprint",
                subtitle = "Touch the sensor to verify and link your device biometric to this DevLedger account",
                onSuccess = {
                    viewModel.isFingerprintVerified = true
                    viewModel.errorText = null
                },
                onError = { message ->
                    viewModel.errorText = "Verification failed: $message"
                    viewModel.isFingerprintVerified = false
                }
            )
        } else {
            viewModel.errorText = "Biometric hardware unavailable."
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
                    Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("Are you sure you want to log out and return to the home screen?", color = DevLedgerColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout() }) {
                    Text("Logout", color = DevLedgerColors.ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = DevLedgerColors.TextMuted)
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
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Security Configuration",
                color = DevLedgerColors.AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Registration Step",
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
            text = "Register Your Security Vault",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Set up your unique security PIN and register your fingerprint for biometric access to this workspace.",
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
            label = "CREATE 4-DIGIT PIN",
            placeholder = "0000",
            isPassword = true,
            isValid = viewModel.pin.length == 4,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        DevLedgerTextField(
            value = viewModel.confirmPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) viewModel.confirmPin = it },
            label = "CONFIRM 4-DIGIT PIN",
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
                    text = "Register Biometrics",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (viewModel.isFingerprintVerified) "Fingerprint registered successfully" else "Enable secure biometric quick access",
                    color = if (viewModel.isFingerprintVerified) DevLedgerColors.SuccessGreen else DevLedgerColors.TextMuted,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = viewModel.isFingerprintEnabled,
                onCheckedChange = { 
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
                text = viewModel.errorText!!,
                color = DevLedgerColors.ErrorRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Large Bottom Button: Scan Fingerprint (if enabled but not verified) or Save
        if (viewModel.isFingerprintEnabled && !viewModel.isFingerprintVerified) {
            DevLedgerPrimaryButton(
                text = "Scan Fingerprint to Register",
                onClick = { triggerBiometricVerification() }
            )
        } else {
            DevLedgerPrimaryButton(
                text = "Save & Continue",
                onClick = { viewModel.saveSetup() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Red Logout Button as requested
        Button(
            onClick = { showLogoutDialog = true },
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
                Text("Logout & Cancel Setup", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}