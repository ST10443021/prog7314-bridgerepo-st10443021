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
 * Displays the DevLedger Privacy Policy.
 *
 * This screen contains static privacy information and therefore does not
 * require a ViewModel.
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
    ) {

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
                text = "Privacy Policy",
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
                text = "DevLedger Privacy Policy",
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
                    text = "This Privacy Policy explains how DevLedger collects, uses, stores and protects information when you use the application.",
                    color = DevLedgerColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            PrivacySection(
                number = "1",
                title = "Information We Collect",
                body = """
                    DevLedger may collect information that you provide when creating and using an account, including your name, email address, profile information and application preferences.

                    The application may also store information that you create through its features, including code snippets, folders, tags and financial tracking records.
                """.trimIndent()
            )

            PrivacySection(
                number = "2",
                title = "Authentication Information",
                body = """
                    DevLedger may use email authentication and third-party Single Sign-On providers such as Google and GitHub.

                    Authentication providers may supply DevLedger with limited account information required to identify and authenticate you. DevLedger does not require access to your third-party account password.
                """.trimIndent()
            )

            PrivacySection(
                number = "3",
                title = "Biometric and PIN Security",
                body = """
                    DevLedger may use biometric authentication or a PIN to protect access to sensitive application features.

                    Biometric verification is performed through supported Android device security mechanisms. DevLedger should not directly store raw fingerprint or facial biometric data.
                """.trimIndent()
            )

            PrivacySection(
                number = "4",
                title = "How We Use Your Information",
                body = """
                    Information is used to provide application functionality, authenticate users, manage user preferences, store developer assets, display financial records, synchronise data and improve the reliability of the application.
                """.trimIndent()
            )

            PrivacySection(
                number = "5",
                title = "Local Storage",
                body = """
                    DevLedger follows an offline-first design. Certain information may be stored locally on your device so that core functionality remains available without a network connection.

                    Local information may later be synchronised with DevLedger's cloud services when connectivity becomes available.
                """.trimIndent()
            )

            PrivacySection(
                number = "6",
                title = "Cloud Storage and Synchronisation",
                body = """
                    Account and application data may be transmitted to DevLedger's REST API and stored in its linked database for synchronisation and account access purposes.

                    Appropriate safeguards should be used to protect data during transmission and storage.
                """.trimIndent()
            )

            PrivacySection(
                number = "7",
                title = "Third-Party Services",
                body = """
                    DevLedger may integrate with services including Google, GitHub and Firebase.

                    These providers may process limited information required to provide authentication, notifications or other connected functionality. Their own privacy policies apply to information processed by those services.
                """.trimIndent()
            )

            PrivacySection(
                number = "8",
                title = "Notifications",
                body = """
                    If notifications are enabled, DevLedger may use Firebase Cloud Messaging to deliver application alerts and reminders.

                    Notification preferences may be changed through the application's Settings screen.
                """.trimIndent()
            )

            PrivacySection(
                number = "9",
                title = "Financial Data",
                body = """
                    Financial records entered into DevLedger are used to support the application's personal income and expense tracking functionality.

                    Users should avoid entering unnecessary sensitive banking or payment credentials into free-text fields.
                """.trimIndent()
            )

            PrivacySection(
                number = "10",
                title = "Data Security",
                body = """
                    DevLedger uses application security measures including authentication, device-level security and protected communication with connected services.

                    No software system can guarantee absolute security, and users remain responsible for protecting access to their devices and accounts.
                """.trimIndent()
            )

            PrivacySection(
                number = "11",
                title = "Your Choices",
                body = """
                    You may manage supported preferences through the Settings screen, including language, notification and security settings.

                    Where supported by the application, account and profile information may also be updated or removed.
                """.trimIndent()
            )

            PrivacySection(
                number = "12",
                title = "Changes to This Policy",
                body = """
                    This Privacy Policy may be updated as DevLedger develops or as its services change. The latest version will be made available through the application.
                """.trimIndent()
            )

            PrivacySection(
                number = "13",
                title = "Contact",
                body = """
                    Questions regarding this Privacy Policy can be directed to the DevLedger development team.
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
private fun PrivacySection(
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