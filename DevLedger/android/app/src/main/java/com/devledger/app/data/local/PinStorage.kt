package com.devledger.app.data.local

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
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
data class PinCredentials(val hash: String, val salt: String)

@Singleton
class PinStorage @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun savePin(pin: String, email: String? = null) {
        savePinCredentials(createPinCredentials(pin), email)
    }

    /** Generate first, so Security Setup can save remotely before committing locally. */
    fun createPinCredentials(pin: String): PinCredentials {
        require(pin.length == 4 && pin.all { it in '0'..'9' }) {
            "PIN must contain exactly four digits."
        }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        return PinCredentials(
            Base64.encodeToString(hashPin(pin, salt), Base64.NO_WRAP),
            Base64.encodeToString(salt, Base64.NO_WRAP),
        )
    }

    fun savePinCredentials(credentials: PinCredentials, email: String?) {
        val suffix = suffix(email)
        preferences.edit()
            .putString(KEY_PIN_SALT + suffix, credentials.salt)
            .putString(KEY_PIN_HASH + suffix, credentials.hash)
            .apply()
    }

    fun verifyPin(pin: String, email: String? = null): Boolean {
        val suffix = suffix(email)
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
        val suffix = suffix(email)
        if (preferences.contains(KEY_PIN_SALT + suffix) &&
            preferences.contains(KEY_PIN_HASH + suffix)) return true

        // A legacy unscoped PIN cannot safely be assigned to an arbitrary account.
        // Existing account-scoped PINs are retained; otherwise use Security Setup.
        return false
    }

    fun isFingerprintRegistered(email: String? = null): Boolean {
        val suffix = suffix(email)
        return preferences.getBoolean(KEY_DEVICE_VERIFIED + suffix, false)
    }

    fun setFingerprintRegistered(registered: Boolean, email: String? = null) {
        val suffix = suffix(email)
        preferences.edit().putBoolean(KEY_DEVICE_VERIFIED + suffix, registered).apply()
    }

    fun clearPin(email: String? = null) {
        val suffix = suffix(email)
        preferences.edit()
            .remove(KEY_PIN_SALT + suffix)
            .remove(KEY_PIN_HASH + suffix)
            .remove("fingerprint_registered" + suffix)
            .remove(KEY_DEVICE_VERIFIED + suffix)
            .apply()
    }

    // Server preferences are not proof of a native scan on this phone.
    @Suppress("UNUSED_PARAMETER") // Retain compatibility with existing authentication call sites.
    fun syncFromBackend(email: String, hash: String, salt: String, biometricEnabled: Boolean = false) {
        val suffix = suffix(email)
        // Only sync if we don't already have a local PIN for this user
        if (!preferences.contains(KEY_PIN_SALT + suffix)) {
            preferences.edit()
                .putString(KEY_PIN_SALT + suffix, salt)
                .putString(KEY_PIN_HASH + suffix, hash)
                .apply()
        }
    }

    fun getLocalHash(email: String?): String? {
        val suffix = suffix(email)
        return preferences.getString(KEY_PIN_HASH + suffix, null)
    }

    fun getLocalSalt(email: String?): String? {
        val suffix = suffix(email)
        return preferences.getString(KEY_PIN_SALT + suffix, null)
    }

    private fun suffix(email: String?): String =
        if (email.isNullOrBlank()) "" else "_${email.trim().lowercase(Locale.ROOT)}"

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

        // Old fingerprint_registered keys may have been written from a server flag,
        // so require one fresh native verification after this patch is installed.
        private const val KEY_DEVICE_VERIFIED = "biometric_verified_v3"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"

        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH = 16
    }
}
