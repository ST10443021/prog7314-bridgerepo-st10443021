package com.devledger.app.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.data.local.PinStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BiometricPinStorageTest {
    private lateinit var context: Context
    private lateinit var pins: PinStorage

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("devledger_security", Context.MODE_PRIVATE).edit().clear().commit()
        pins = PinStorage(context)
    }

    @Test fun `backend preference and old flags never verify this phone`() {
        context.getSharedPreferences("devledger_security", Context.MODE_PRIVATE).edit()
            .putBoolean("fingerprint_registered_a@example.test", true).commit()
        pins.syncFromBackend("a@example.test", "hash", "salt", true)
        assertFalse(pins.isFingerprintRegistered("a@example.test"))
    }

    @Test fun `native verification remains account scoped and normalises email`() {
        pins.setFingerprintRegistered(true, " A@Example.Test ")
        assertTrue(pins.isFingerprintRegistered("a@example.test"))
        assertFalse(pins.isFingerprintRegistered("b@example.test"))
    }

    @Test fun `PIN remains valid after biometric flag is changed`() {
        pins.savePin("1234", "a@example.test")
        pins.setFingerprintRegistered(false, "a@example.test")
        assertTrue(pins.verifyPin("1234", "a@example.test"))
        assertFalse(pins.verifyPin("1235", "a@example.test"))
        assertFalse(pins.hasPin("b@example.test"))
    }

    @Test fun `unscoped legacy PIN is never copied to a different account`() {
        pins.savePin("1234")
        assertFalse(pins.hasPin("a@example.test"))
        assertNull(pins.getLocalHash("a@example.test"))
    }
}
