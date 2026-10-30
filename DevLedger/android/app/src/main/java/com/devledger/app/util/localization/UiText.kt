package com.devledger.app.util.localization

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

// Reference scope: Per-app language support. (Google Developers, n.d.r). See REFERENCES-CODE.md for full entries.

/** Locale-independent UI state: resolve only at the screen, using its current locale. */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Quantity(@PluralsRes val id: Int, val count: Int) : UiText
    /** Reserved for messages already supplied by Android's native biometric prompt. */
    data class Dynamic(val value: String) : UiText
    data class Domain(val value: String) : UiText

    fun resolve(strings: AppStrings): String = when (this) {
        is Resource -> strings.get(id, *args.map { if (it is UiText) it.resolve(strings) else it }.toTypedArray())
        is Quantity -> strings.plural(id, count)
        is Dynamic -> value
        is Domain -> strings.label(value)
    }
}
