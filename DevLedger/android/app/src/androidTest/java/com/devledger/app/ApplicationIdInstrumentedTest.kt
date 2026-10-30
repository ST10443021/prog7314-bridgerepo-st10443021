package com.devledger.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

// Reference scope: Android instrumented testing. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

/**
 * Minimal instrumented (on-device) sanity test replacing Android Studio's default
 * "Hello World" template test. Confirms the app under test is packaged with the
 * correct applicationId (com.devledger.app) so a misconfigured build variant or
 * package-name typo is caught immediately on any test run.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationIdInstrumentedTest {

    @Test
    fun appContext_hasTheExpectedDevLedgerPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertThat(appContext.packageName).isEqualTo("com.devledger.app")
    }
}