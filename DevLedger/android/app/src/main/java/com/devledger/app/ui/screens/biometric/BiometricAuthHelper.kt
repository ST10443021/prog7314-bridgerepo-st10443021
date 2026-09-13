package com.devledger.app.ui.screens.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Handles device biometric authentication for DevLedger.
 *
 * The primary biometric action uses BIOMETRIC_STRONG only.
 * PIN/password fallback is handled separately by the DevLedger UI.
 */
class BiometricAuthHelper(
    private val activity: FragmentActivity
) {

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG

    /**
     * Returns the Android BiometricManager authentication status.
     */
    fun authenticationStatus(): Int {
        return BiometricManager
            .from(activity)
            .canAuthenticate(authenticators)
    }

    fun canAuthenticate(): Boolean {
        return authenticationStatus() == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val status = authenticationStatus()

        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            onError(statusMessage(status))
            return
        }

        val executor: Executor =
            androidx.core.content.ContextCompat.getMainExecutor(activity)

        val callback =
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(
                        "Fingerprint not recognised. Please try again."
                    )
                }
            }

        val biometricPrompt =
            BiometricPrompt(
                activity,
                executor,
                callback
            )

        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify your identity")
                .setSubtitle(
                    "Use your fingerprint to unlock DevLedger"
                )
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("Cancel")
                .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun statusMessage(status: Int): String {
        return when (status) {

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "This device does not have supported biometric hardware."

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "The biometric sensor is currently unavailable."

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No fingerprint is enrolled. Add a fingerprint in your device settings first."

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "A device security update is required before biometric authentication can be used."

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                "Biometric authentication is not supported on this device."

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                "The biometric authentication status could not be determined."

            else ->
                "Biometric authentication is currently unavailable."
        }
    }
}