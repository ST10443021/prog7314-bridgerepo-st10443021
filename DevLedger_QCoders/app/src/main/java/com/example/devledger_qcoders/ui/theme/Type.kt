package com.devledger.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The Figma design uses the Inter font family. Compose falls back to the system
// sans-serif font here; drop Inter's .ttf files into res/font/ and reference them
// via FontFamily(Font(R.font.inter_regular), ...) for a byte-for-byte match.
val DevLedgerFontFamily = FontFamily.SansSerif

val DevLedgerTypography = Typography(
    headlineMedium = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = DevLedgerFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)
