package com.devledger.app.util.localization

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.devledger.app.R
import java.util.Locale

// Reference scope: Per-app language support. (Google Developers, n.d.r). See REFERENCES-CODE.md for full entries.

/** Resource access captured during composition; safe in click/result callbacks too.
 * Locale changes rebuild the provider rather than caching translated text in a ViewModel.
 * References: https://developer.android.com/develop/ui/compose/resources
 * https://developer.android.com/guide/topics/resources/localization
 */
class AppStrings(val context: Context) {
    val locale: Locale get() = context.resources.configuration.locales[0]
    fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    fun plural(@PluralsRes id: Int, count: Int): String =
        context.resources.getQuantityString(id, count, count)

    /** Translate known domain labels without changing stored/API values or user text. */
    fun label(value: String): String = when (val id = labelId(value)) {
        null -> value
        else -> get(id)
    }

    companion object {
        /** Application/worker contexts do not always carry the activity's app locale. */
        fun forLocale(context: Context, language: String): AppStrings {
            val tag = language.takeIf { it in setOf("en", "af", "xh") } ?: "en"
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            return AppStrings(context.createConfigurationContext(config))
        }

        fun forContext(context: Context): AppStrings {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return AppStrings(context)
            val config = Configuration(context.resources.configuration)
            config.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
            return AppStrings(context.createConfigurationContext(config))
        }
    }
}

@Composable
fun rememberAppStrings(): AppStrings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AppStrings(context) }
}

/** Pure mapping is tested independently from Android resources. */
@StringRes
internal fun labelId(value: String): Int? = when (value) {
        "Project Payment" -> R.string.lang_project_payment
        "Freelance Work" -> R.string.lang_freelance_work
        "Consulting" -> R.string.lang_consulting
        "Salary" -> R.string.lang_salary
        "Other Income" -> R.string.lang_other_income
        "Tools & Subscriptions" -> R.string.lang_tools_subscriptions
        "Hosting & Domains" -> R.string.lang_hosting_domains
        "Marketing" -> R.string.lang_marketing
        "Equipment" -> R.string.lang_equipment
        "Other Expenses" -> R.string.lang_other_expenses
        "Bank Transfer" -> R.string.lang_bank_transfer
        "Cash" -> R.string.lang_cash
        "Credit Card" -> R.string.lang_credit_card
        "Other" -> R.string.lang_other
        "Weekly" -> R.string.lang_weekly
        "Monthly" -> R.string.lang_monthly
        "Yearly" -> R.string.lang_yearly
        "Recently Updated" -> R.string.lang_recently_updated
        "Title A-Z" -> R.string.lang_title_a_z
        "Language" -> R.string.lang_language
        "WEEKLY" -> R.string.lang_weekly
        "MONTHLY" -> R.string.lang_monthly
        "YEARLY" -> R.string.lang_yearly
    else -> null
}
