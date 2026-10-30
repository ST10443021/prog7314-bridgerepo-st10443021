package com.devledger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Reference scope: Jetpack Compose theming and reusable components. (Google Developers, n.d.p). See REFERENCES-CODE.md for full entries.

// Central colour scheme for the current DevLedger dark UI.
private val DevLedgerDarkScheme = darkColorScheme(
    primary = DevLedgerColors.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = DevLedgerColors.AccentBlueDark,
    onPrimaryContainer = DevLedgerColors.TextPrimary,

    secondary = DevLedgerColors.AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = DevLedgerColors.SurfaceCardAlt,
    onSecondaryContainer = DevLedgerColors.TextPrimary,

    tertiary = DevLedgerColors.AccentCyan,
    onTertiary = DevLedgerColors.BgDeep,
    tertiaryContainer = DevLedgerColors.SurfaceCardAlt,
    onTertiaryContainer = DevLedgerColors.TextPrimary,

    background = DevLedgerColors.BgDeep,
    onBackground = DevLedgerColors.TextPrimary,

    surface = DevLedgerColors.SurfaceCard,
    onSurface = DevLedgerColors.TextPrimary,
    surfaceVariant = DevLedgerColors.SurfaceCardAlt,
    onSurfaceVariant = DevLedgerColors.TextSecondary,
    surfaceTint = DevLedgerColors.AccentBlue,

    error = DevLedgerColors.ErrorRed,
    onError = Color.White,
    errorContainer = DevLedgerColors.ErrorRedBg,
    onErrorContainer = DevLedgerColors.TextPrimary,

    outline = DevLedgerColors.Border,
    outlineVariant = DevLedgerColors.BorderLight,
    scrim = Color.Black,
)

@Composable
fun DevLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DevLedgerDarkScheme,
        typography = DevLedgerTypography,
        content = content,
    )
}