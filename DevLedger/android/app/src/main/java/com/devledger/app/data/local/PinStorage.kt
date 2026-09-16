package com.devledger.app.data.local

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Securely stores and verifies the user's fallback PIN and Biometric state.
 * Supports multi-user separation by using email prefixes to avoid cross-account bleeding.
 */
@Singleton
class PinStorage @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun savePin(pin: String, email: String? = null) {
        require(pin.length == 4 && pin.all { it.isDigit() }) {
            "PIN must contain exactly four digits."
        }

        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        val hash = hashPin(pin, salt)
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"

        preferences.edit()
            .putString(KEY_PIN_SALT + suffix, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH + suffix, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String, email: String? = null): Boolean {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        val storedSalt = preferences.getString(KEY_PIN_SALT + suffix, null)
            ?: return false

        val storedHash = preferences.getString(KEY_PIN_HASH + suffix, null)
            ?: return false

        return try {
            val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
            val expectedHash = Base64.decode(storedHash, Base64.NO_WRAP)
            val enteredHash = hashPin(pin, salt)

            MessageDigest.isEqual(
                expectedHash,
                enteredHash
            )
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun hasPin(email: String? = null): Boolean {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        if (preferences.contains(KEY_PIN_SALT + suffix)) return true
        
        // Migration: If we have an unsuffixed PIN, migrate it to the new suffixed key
        if (!email.isNullOrBlank() && preferences.contains(KEY_PIN_SALT)) {
            val salt = preferences.getString(KEY_PIN_SALT, null)
            val hash = preferences.getString(KEY_PIN_HASH, null)
            val fingerprint = preferences.getBoolean("fingerprint_registered", false)
            
            if (salt != null && hash != null) {
                preferences.edit()
                    .putString(KEY_PIN_SALT + suffix, salt)
                    .putString(KEY_PIN_HASH + suffix, hash)
                    .putBoolean("fingerprint_registered" + suffix, fingerprint)
                    .apply()
                return true
            }
        }
        return false
    }

    fun isFingerprintRegistered(email: String? = null): Boolean {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        return preferences.getBoolean("fingerprint_registered" + suffix, false)
    }

    fun setFingerprintRegistered(registered: Boolean, email: String? = null) {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        preferences.edit().putBoolean("fingerprint_registered" + suffix, registered).apply()
    }

    fun clearPin(email: String? = null) {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        preferences.edit()
            .remove(KEY_PIN_SALT + suffix)
            .remove(KEY_PIN_HASH + suffix)
            .remove("fingerprint_registered" + suffix)
            .apply()
    }

    fun syncFromBackend(email: String, hash: String, salt: String, biometricEnabled: Boolean = false) {
        val suffix = "_${email.lowercase().trim()}"
        // Only sync if we don't already have a local PIN for this user
        if (!preferences.contains(KEY_PIN_SALT + suffix)) {
            preferences.edit()
                .putString(KEY_PIN_SALT + suffix, salt)
                .putString(KEY_PIN_HASH + suffix, hash)
                .putBoolean("fingerprint_registered" + suffix, biometricEnabled)
                .apply()
        }
    }

    fun getLocalHash(email: String?): String? {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        return preferences.getString(KEY_PIN_HASH + suffix, null)
    }

    fun getLocalSalt(email: String?): String? {
        val suffix = if (email.isNullOrBlank()) "" else "_${email.lowercase()}"
        return preferences.getString(KEY_PIN_SALT + suffix, null)
    }

    private fun hashPin(
        pin: String,
        salt: ByteArray
    ): ByteArray {
        val specification = PBEKeySpec(
            pin.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH_BITS
        )

        return try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
        } finally {
            specification.clearPassword()
        }
    }

    companion object {
        private const val PREFS_NAME = "devledger_security"

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"

        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH = 16
    }
}