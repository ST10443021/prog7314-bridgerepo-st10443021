package com.devledger.app.ui.screens.biometric

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.devledger.app.R

/**
 * Native Class 3 biometric verification. A failed scan is retryable; only success
 * verifies the user. DevLedger's own PIN remains a separate fallback.
 *
 * The prompt is bound to a hardware-backed Keystore key through a BiometricPrompt.CryptoObject
 * (see [BiometricKeyManager]), so success also requires the secure hardware to release the key.
 * Reference scope: (Google Developers, n.d.d). See REFERENCES-CODE.md for full entries.
 * https://developer.android.com/identity/sign-in/biometric-auth
 */
class BiometricAuthHelper(private val activity: FragmentActivity) {
    /** ENROLL creates a fresh key (turning biometrics on); UNLOCK uses the existing one. */
    enum class Mode { ENROLL, UNLOCK }

    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    private var prompt: BiometricPrompt? = null
    private var active = false

    fun authenticationStatus(): Int =
        BiometricManager.from(activity).canAuthenticate(authenticators)

    fun canAuthenticate(): Boolean =
        authenticationStatus() == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        title: String = activity.getString(R.string.bio_verify_title),
        subtitle: String = activity.getString(R.string.bio_verify_subtitle),
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: (String) -> Unit = {},
        mode: Mode = Mode.UNLOCK,
    ) {
        if (active) return
        val status = authenticationStatus()
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.i(TAG, "Biometric capability unavailable: $status")
            onError(statusMessage(status))
            return
        }
        // Bind the prompt to the hardware-backed key. If the enrolled biometrics changed since the
        // key was made, refuse and ask for the PIN instead of silently re-trusting the new scan.
        val cipher = when (val prepared = BiometricKeyManager.prepare(createFresh = mode == Mode.ENROLL)) {
            is BiometricKeyManager.Prepared.Ready -> prepared.cipher
            BiometricKeyManager.Prepared.Invalidated -> {
                onError(activity.getString(R.string.bio_key_invalidated))
                return
            }
            is BiometricKeyManager.Prepared.Unavailable -> null
        }
        active = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!active) return
                active = false
                if (cipher != null && !BiometricKeyManager.confirmUse(result.cryptoObject?.cipher)) {
                    Log.w(TAG, "Scan succeeded but the Keystore key refused to unlock")
                    onError(activity.getString(R.string.bio_unavailable))
                    return
                }
                Log.i(TAG, "Native biometric verification succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!active) return
                active = false
                Log.i(TAG, "Biometric prompt ended: $errorCode")
                // App-owned messages follow the selected app language, independent of OS wording.
                onError(activity.getString(biometricErrorMessageRes(errorCode)))
            }

            override fun onAuthenticationFailed() {
                if (active) onFailed(activity.getString(R.string.bio_scan_failed))
            }
        }
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText(activity.getString(R.string.bio_cancel))
            .build()
        try {
            if (cipher != null) prompt?.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            else prompt?.authenticate(info)
        } catch (_: IllegalStateException) {
            active = false
            onError(activity.getString(R.string.bio_unavailable))
        }
    }

    /** Ignore late callbacks when a screen exits or the PIN route is selected. */
    fun cancel() {
        active = false
        prompt?.cancelAuthentication()
        prompt = null
    }

    private fun statusMessage(status: Int): String = activity.getString(when (status) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> R.string.bio_not_enrolled
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> R.string.bio_no_hardware
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> R.string.bio_unavailable
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> R.string.bio_update_required
        else -> R.string.bio_unavailable
    })

    private companion object { const val TAG = "DevLedgerBiometrics" }
}

/** Compose may supply a themed ContextWrapper rather than the activity itself. */
fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findFragmentActivity()
    else -> null
}

/**
 * Error codes are stable; native/OEM error text can follow the phone's language.
 * Resolve the returned resource in the current activity when the callback arrives.
 * Reference: https://developer.android.com/reference/androidx/biometric/BiometricPrompt
 */
@StringRes
internal fun biometricErrorMessageRes(errorCode: Int): Int = when (errorCode) {
    BiometricPrompt.ERROR_CANCELED,
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> R.string.bio_prompt_cancelled
    BiometricPrompt.ERROR_TIMEOUT -> R.string.bio_prompt_timeout
    BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> R.string.bio_prompt_unable_to_process
    BiometricPrompt.ERROR_LOCKOUT -> R.string.bio_prompt_locked_out
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> R.string.bio_prompt_locked_out_permanent
    BiometricPrompt.ERROR_NO_BIOMETRICS -> R.string.bio_not_enrolled
    BiometricPrompt.ERROR_HW_NOT_PRESENT -> R.string.bio_no_hardware
    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> R.string.bio_update_required
    else -> R.string.bio_unavailable
}
