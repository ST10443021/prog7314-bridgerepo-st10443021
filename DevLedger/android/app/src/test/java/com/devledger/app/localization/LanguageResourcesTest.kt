package com.devledger.app.localization

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.R
import com.devledger.app.util.localization.AppStrings
import com.devledger.app.util.localization.UiText
import com.devledger.app.ui.screens.auth.AuthUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LanguageResourcesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun strings(tag: String) = AppStrings.forLocale(context, tag)

    @Test fun `existing validation state resolves again after changing language`() {
        val state = AuthUiState(emailError = UiText.Resource(R.string.lang_enter_your_email_address))
        val message = requireNotNull(state.emailError)
        assertEquals("Enter your email address.", message.resolve(strings("en")))
        assertEquals("Voer jou e-posadres in.", message.resolve(strings("af")))
        assertEquals("Faka idilesi yakho ye-imeyile.", message.resolve(strings("xh")))
    }

    @Test fun `translated labels preserve canonical values and custom content`() {
        val storedCategory = "Project Payment"
        assertEquals("Projekbetaling", strings("af").label(storedCategory))
        assertEquals("Intlawulo yeprojekthi", strings("xh").label(storedCategory))
        assertEquals("Project Payment", storedCategory)
        assertEquals("My custom category", strings("xh").label("My custom category"))
        assertEquals("Nyanga nganye", strings("xh").label("MONTHLY"))
    }

    @Test fun `plural and formatted text resolve without leaking placeholders`() {
        assertEquals("1 snippet", strings("en").plural(R.plurals.lang_snippet_count, 1))
        assertEquals("2 snippets", strings("en").plural(R.plurals.lang_snippet_count, 2))
        assertEquals("2 kodebrokkies", strings("af").plural(R.plurals.lang_snippet_count, 2))
        assertEquals("Iziqwenga ezi-2", strings("xh").plural(R.plurals.lang_snippet_count, 2))
        assertEquals("Verwyder \"My code\"?", strings("af").get(R.string.lang_delete_named, "My code"))
        assertEquals("+5.0% teenoor Sep", strings("af").get(R.string.lang_change_vs_month, "+5.0", "Sep"))
    }

    @Test fun `nested local notification text translates its built in category`() {
        val body = UiText.Resource(R.string.lang_income_body,
            listOf("ZAR", "50.00", UiText.Domain("Project Payment")))
        assertEquals("ZAR 50.00 vir Projekbetaling ontvang.", body.resolve(strings("af")))
        assertEquals("Received ZAR 50.00 for Project Payment.", body.resolve(strings("en")))
    }

    @Test fun `unknown locale deliberately falls back to English`() {
        assertEquals("Back", strings("unsupported").get(R.string.lang_back))
    }
}
