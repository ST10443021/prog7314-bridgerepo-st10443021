package com.devledger.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Reference scope: Utility helpers kept free of UI code. (Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.

class SyntaxHighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            SyntaxHighlighter.highlight(text.text),
            OffsetMapping.Identity
        )
    }
}
