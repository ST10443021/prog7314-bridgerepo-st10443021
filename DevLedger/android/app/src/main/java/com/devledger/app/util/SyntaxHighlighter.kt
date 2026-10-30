package com.devledger.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.devledger.app.ui.theme.DevLedgerColors

// Reference scope: Utility helpers kept free of UI code. (Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.

/**
 * Very basic syntax highlighter using regex for the code editor and viewer.
 * Highlighting colors match the DevLedger pixel-accurate Figma export.
 */
object SyntaxHighlighter {
    private val Keywords = setOf(
        "package", "import", "class", "interface", "fun", "val", "var", "if", "else", "for", "while",
        "return", "when", "is", "as", "null", "true", "false", "this", "super", "override", "private",
        "public", "internal", "protected", "sealed", "data", "object", "companion", "suspend", "inline",
        "operator", "infix", "typealias", "constructor", "init", "get", "set"
    )

    fun highlight(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        
        // Keywords
        val keywordRegex = Regex("\\b(${Keywords.joinToString("|")})\\b")
        keywordRegex.findAll(code).forEach { match ->
            addStyle(SpanStyle(color = DevLedgerColors.AccentPurple), match.range.first, match.range.last + 1)
        }

        // Strings
        val stringRegex = Regex("\".*?\"")
        stringRegex.findAll(code).forEach { match ->
            addStyle(SpanStyle(color = DevLedgerColors.SuccessGreen), match.range.first, match.range.last + 1)
        }

        // Comments
        val commentRegex = Regex("//.*|/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        commentRegex.findAll(code).forEach { match ->
            addStyle(SpanStyle(color = DevLedgerColors.TextMuted), match.range.first, match.range.last + 1)
        }

        // Annotations
        val annotationRegex = Regex("@\\w+")
        annotationRegex.findAll(code).forEach { match ->
            addStyle(SpanStyle(color = DevLedgerColors.WarningAmber), match.range.first, match.range.last + 1)
        }
    }
}
