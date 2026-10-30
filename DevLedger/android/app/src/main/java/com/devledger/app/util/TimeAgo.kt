package com.devledger.app.util

import com.devledger.app.R
import com.devledger.app.util.localization.AppStrings
import java.util.concurrent.TimeUnit

// Reference scope: Utility helpers kept free of UI code. (Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.

/** Relative time resolved with Android plurals for the current application language. */
object TimeAgo {
    fun format(timestampMillis: Long, strings: AppStrings): String {
        val diff = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> strings.get(R.string.lang_just_now)
            diff < TimeUnit.HOURS.toMillis(1) -> strings.plural(R.plurals.lang_minutes_ago, TimeUnit.MILLISECONDS.toMinutes(diff).toInt())
            diff < TimeUnit.DAYS.toMillis(1) -> strings.plural(R.plurals.lang_hours_ago, TimeUnit.MILLISECONDS.toHours(diff).toInt())
            diff < TimeUnit.DAYS.toMillis(7) -> strings.plural(R.plurals.lang_days_ago, TimeUnit.MILLISECONDS.toDays(diff).toInt())
            else -> strings.plural(R.plurals.lang_weeks_ago, (TimeUnit.MILLISECONDS.toDays(diff) / 7).toInt())
        }
    }
}
