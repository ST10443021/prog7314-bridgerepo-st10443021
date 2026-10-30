package com.devledger.app.ui.theme

import androidx.compose.ui.graphics.Color

// Reference scope: Jetpack Compose theming and reusable components. (Google Developers, n.d.p). See REFERENCES-CODE.md for full entries.

/**
 * Colour tokens lifted directly from the Part 1 pixel-accurate Figma Make export
 * (src/index.css, StatusBar.tsx, BottomNav.tsx and the individual screen components)
 * so the native Compose UI matches the approved design 1:1.
 */
object DevLedgerColors {
    val BgDeep = Color(0xFF060810)
    val SurfaceNav = Color(0xFF0F1320)
    val SurfaceCard = Color(0xFF141824)
    val SurfaceCardAlt = Color(0xFF1D2235)
    val Border = Color(0xFF1E2A3E)
    val BorderLight = Color(0xFF2A3A55)

    val TextPrimary = Color(0xFFE8EDF5)
    val TextSecondary = Color(0xFF8892A4)
    val TextMuted = Color(0xFF6B7891)

    val AccentBlue = Color(0xFF1D6FE8)
    val AccentBlueDark = Color(0xFF0F2550)
    val SuccessGreen = Color(0xFF22C55E)
    val SuccessGreenBg = Color(0xFF0F3D24)
    val ErrorRed = Color(0xFFEF4444)
    val ErrorRedBg = Color(0xFF3D1212)
    val WarningAmber = Color(0xFFF59E0B)
    val AccentPurple = Color(0xFFA855F7)
    val AccentCyan = Color(0xFF06B6D4)
}
