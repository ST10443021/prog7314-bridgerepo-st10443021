package com.devledger.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Initialises Hilt dependency injection for DevLedger. */
@HiltAndroidApp
class DevLedgerApplication : Application()