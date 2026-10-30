package com.devledger.app.ui.screens.biometric

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Hardware-backed key that gates biometric unlock.
 *
 * A BiometricPrompt that only reports "success" can be bypassed on a compromised device. Binding
 * the prompt to an Android Keystore key that requires a fresh strong-biometric authentication for
 * every use means a successful scan must also unlock a cryptographic operation performed by the
 * secure hardware (Google Developers, n.d.d; Google Developers, n.d.m). The key is also
 * invalidated when a new fingerprint or face is enrolled, so someone who adds their own biometric
 * to an unlocked phone cannot use it to open DevLedger.
 *
 * The key never leaves the Keystore and protects no data itself; it is a proof-of-presence gate.
 * The PIN fallback remains separate. See REFERENCES-CODE.md for full entries.
 */
object BiometricKeyManager {
    private const val TAG = "DevLedgerBiometricKey"
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "devledger_biometric_gate_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val CHALLENGE = byteArrayOf(0x44, 0x4C, 0x47) // "DLG"

    /** Result of preparing the cipher that is handed to BiometricPrompt. */
    sealed interface Prepared {
        data class Ready(val cipher: Cipher) : Prepared
        /** A biometric was added or removed since the key was created; the user must re-enable. */
        data object Invalidated : Prepared
        /** Keystore problem on this device; the caller may fall back to an unbound prompt. */
        data class Unavailable(val cause: Throwable) : Prepared
    }

    /**
     * Prepares an initialised cipher. [createFresh] replaces any existing key and is used only when
     * the user is (re-)enabling biometrics after a successful PIN-protected flow.
     */
    fun prepare(createFresh: Boolean): Prepared = try {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (createFresh || !keyStore.containsAlias(ALIAS)) createKey()
        val key = keyStore.getKey(ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        Prepared.Ready(cipher)
    } catch (invalidated: KeyPermanentlyInvalidatedException) {
        Log.w(TAG, "Biometric key invalidated by a change in enrolled biometrics")
        Prepared.Invalidated
    } catch (error: Exception) {
        Log.w(TAG, "Keystore unavailable for biometric binding", error)
        Prepared.Unavailable(error)
    }

    /** Proves the key was unlocked by the scan: the operation throws if authentication did not happen. */
    fun confirmUse(cipher: Cipher?): Boolean = try {
        cipher != null && cipher.doFinal(CHALLENGE).isNotEmpty()
    } catch (error: Exception) {
        Log.w(TAG, "Biometric-bound key refused the operation", error)
        false
    }

    /** Removes the key, e.g. when the user switches biometric unlock off. */
    fun clear() {
        try {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
        } catch (error: Exception) {
            Log.w(TAG, "Could not delete biometric key", error)
        }
    }

    private fun createKey() {
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Every use needs a fresh strong-biometric authentication (timeout 0 = per-use).
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(builder.build()) }
            .generateKey()
    }
}
