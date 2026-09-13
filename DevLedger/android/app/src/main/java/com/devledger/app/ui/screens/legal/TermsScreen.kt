package com.devledger.app.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.ui.theme.DevLedgerColors

/**
 * Displays the DevLedger Terms and Conditions.
 *
 * This screen contains static legal information and therefore does not
 * require a ViewModel.
 */
@Composable
fun TermsScreen(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
    ) {

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = DevLedgerColors.TextPrimary
            )

            Text(
                text = "Terms & Conditions",
                color = DevLedgerColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        HorizontalDivider(
            color = DevLedgerColors.SurfaceCardAlt
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {

            Text(
                text = "DevLedger Terms & Conditions",
                color = DevLedgerColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last updated: 11 September 2026",
                color = DevLedgerColors.TextMuted,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = DevLedgerColors.SurfaceCardAlt,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "By creating an account or using DevLedger, you agree to these Terms & Conditions.",
                    color = DevLedgerColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            LegalSection(
                number = "1",
                title = "About DevLedger",
                body = """
                    DevLedger is a personal developer utility and asset vault designed to help users manage code snippets, developer assets, financial records and related application preferences.

                    DevLedger is currently an academic application prototype and may contain features that are still under development.
                """.trimIndent()
            )

            LegalSection(
                number = "2",
                title = "Your Account",
                body = """
                    You are responsible for providing accurate account information and for protecting access to your account.

                    You must not knowingly allow another person to use your account credentials. You should immediately take appropriate action if you believe your account or authentication credentials have been compromised.
                """.trimIndent()
            )

            LegalSection(
                number = "3",
                title = "Security and Authentication",
                body = """
                    DevLedger may use email authentication, Single Sign-On services and device-level biometric or PIN verification to protect access to your account and stored information.

                    You are responsible for maintaining appropriate security on the device used to access DevLedger.
                """.trimIndent()
            )

            LegalSection(
                number = "4",
                title = "Your Content",
                body = """
                    You remain responsible for information that you create, upload or store through DevLedger, including code snippets, configuration information and financial records.

                    You should not store content that you do not have permission to use or content that violates applicable laws or third-party rights.
                """.trimIndent()
            )

            LegalSection(
                number = "5",
                title = "Acceptable Use",
                body = """
                    DevLedger must not be used to intentionally store, distribute or manage malicious, unlawful or unauthorised content.

                    You must not attempt to interfere with the application, its authentication services, REST API, database, synchronisation services or other connected systems.
                """.trimIndent()
            )

            LegalSection(
                number = "6",
                title = "Cloud Synchronisation",
                body = """
                    DevLedger may synchronise locally stored information with its cloud services when network connectivity is available.

                    Synchronisation may occasionally be delayed or unavailable because of connectivity problems, service maintenance or technical errors.
                """.trimIndent()
            )

            LegalSection(
                number = "7",
                title = "Third-Party Services",
                body = """
                    Some DevLedger functionality may rely on third-party services such as Google, GitHub and Firebase.

                    Use of these services may also be subject to the terms and policies of the respective third-party providers.
                """.trimIndent()
            )

            LegalSection(
                number = "8",
                title = "Financial Information",
                body = """
                    DevLedger's financial tracking functionality is intended as a personal organisational tool.

                    Information displayed by DevLedger should not be considered professional financial, accounting or tax advice. Users remain responsible for verifying their own financial records.
                """.trimIndent()
            )

            LegalSection(
                number = "9",
                title = "Availability",
                body = """
                    Reasonable efforts are made to keep DevLedger available and functional. However, uninterrupted access cannot be guaranteed.

                    Features may temporarily become unavailable because of maintenance, connectivity problems or technical issues.
                """.trimIndent()
            )

            LegalSection(
                number = "10",
                title = "Privacy",
                body = """
                    The collection and handling of personal information is explained separately in the DevLedger Privacy Policy.

                    You should review the Privacy Policy before creating or using a DevLedger account.
                """.trimIndent()
            )

            LegalSection(
                number = "11",
                title = "Changes to These Terms",
                body = """
                    These Terms & Conditions may be updated as DevLedger develops. Where appropriate, the latest version will be made available through the application.
                """.trimIndent()
            )

            LegalSection(
                number = "12",
                title = "Contact",
                body = """
                    Questions regarding these Terms & Conditions can be directed to the DevLedger development team.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2026 DevLedger",
                color = DevLedgerColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalSection(
    number: String,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$number. $title",
            color = DevLedgerColors.AccentBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = body,
            color = DevLedgerColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}