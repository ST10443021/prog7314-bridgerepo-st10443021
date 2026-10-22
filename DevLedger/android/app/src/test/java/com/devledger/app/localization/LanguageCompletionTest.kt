package com.devledger.app.localization

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.R
import com.devledger.app.ui.screens.biometric.biometricErrorMessageRes
import com.devledger.app.util.localization.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LanguageCompletionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun strings(tag: String) = AppStrings.forLocale(context, tag)

    @Test fun cancellationCodesUseTranslatedMessages() {
        listOf(BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON).forEach { code ->
            assertEquals(R.string.bio_prompt_cancelled, biometricErrorMessageRes(code))
        }
        val message = biometricErrorMessageRes(BiometricPrompt.ERROR_USER_CANCELED)
        assertEquals("Biometric verification cancelled. Try again or use your DevLedger PIN.",
            strings("en").get(message))
        assertEquals("Biometriese bevestiging is gekanselleer. Probeer weer of gebruik jou DevLedger-PIN.",
            strings("af").get(message))
        assertEquals("Ukuqinisekiswa ngebhayometriki kurhoxisiwe. Zama kwakhona okanye usebenzise iPIN yakho yeDevLedger.",
            strings("xh").get(message))
    }

    @Test fun lockoutsAndHardwareErrorsKeepDistinctMessages() {
        val expected = mapOf(
            BiometricPrompt.ERROR_TIMEOUT to R.string.bio_prompt_timeout,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS to R.string.bio_prompt_unable_to_process,
            BiometricPrompt.ERROR_LOCKOUT to R.string.bio_prompt_locked_out,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT to R.string.bio_prompt_locked_out_permanent,
            BiometricPrompt.ERROR_NO_BIOMETRICS to R.string.bio_not_enrolled,
            BiometricPrompt.ERROR_HW_NOT_PRESENT to R.string.bio_no_hardware,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED to R.string.bio_update_required,
        )
        expected.forEach { (code, resource) ->
            assertEquals(resource, biometricErrorMessageRes(code))
        }
        val message = biometricErrorMessageRes(BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
        listOf("en", "af", "xh").forEach { tag ->
            assertFalse(strings(tag).get(message).isBlank())
        }
        assertEquals(R.string.bio_unavailable,
            biometricErrorMessageRes(BiometricPrompt.ERROR_HW_UNAVAILABLE))
        assertEquals(R.string.bio_unavailable, biometricErrorMessageRes(98765))
    }

    @Test fun languagePickerNamesRemainRecognizable() {
        listOf("en", "af", "xh").forEach { tag ->
            val localized = strings(tag)
            assertEquals("English", localized.get(R.string.language_english))
            assertEquals("Afrikaans", localized.get(R.string.language_afrikaans))
            assertEquals("isiXhosa", localized.get(R.string.language_xhosa))
        }
    }

    @Test fun olderScreenResourcesAndDateConfirmationAreTranslated() {
        assertEquals("Kodebrokkies", strings("af").get(R.string.nav_snippets))
        assertEquals("Merkers", strings("af").get(R.string.editor_field_tags))
        assertEquals("Goed", strings("af").get(R.string.lang_ok))
        assertEquals("Useto", strings("xh").get(R.string.settings_title))
        assertEquals("Ingeniso eshiyekileyo", strings("xh").get(R.string.dashboard_net_income))
        assertEquals("Imali eseleyo", strings("xh").get(R.string.finance_net_balance))
        assertEquals("Kulungile", strings("xh").get(R.string.lang_ok))
    }
}
