package com.devledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.devledger.app.ui.navigation.DevLedgerNavHost
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the whole DevLedger Compose UI. Navigation between all
 * screens is handled by [DevLedgerNavHost] using Navigation-Compose, matching the
 * navigation wireflow diagram from the Part 1 Planning and Design document.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevLedgerApp()
        }
    }
}

@Composable
private fun DevLedgerApp() {
    DevLedgerTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DevLedgerColors.BgDeep),
        ) {
            val navController = rememberNavController()
            DevLedgerNavHost(navController = navController)
        }
    }
}
