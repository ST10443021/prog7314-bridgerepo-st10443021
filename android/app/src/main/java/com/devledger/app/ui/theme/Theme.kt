package com.devledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// DevLedger ships a single, permanent dark theme matching the approved Part 1 design —
// deliberately not following system light/dark mode, the same way the reference apps
// researched in Part 1 (Dash, DevDocs) default to a developer-focused dark UI.
private val DevLedgerDarkScheme = darkColorScheme(
    primary = DevLedgerColors.AccentBlue,
    onPrimary = DevLedgerColors.TextPrimary,
    background = DevLedgerColors.BgDeep,
    onBackground = DevLedgerColors.TextPrimary,
    surface = DevLedgerColors.SurfaceCard,
    onSurface = DevLedgerColors.TextPrimary,
    error = DevLedgerColors.ErrorRed,
    onError = DevLedgerColors.TextPrimary,
    outline = DevLedgerColors.Border,
)

@Composable
fun DevLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DevLedgerDarkScheme,
        typography = DevLedgerTypography,
        content = content,
    )
}
