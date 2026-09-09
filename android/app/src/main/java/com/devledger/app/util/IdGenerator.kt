package com.devledger.app.util

import java.util.UUID

/** Generates client-side UUIDs for entities created offline, matching the `GUID (String)`
 *  primary-key type specified across the Part 1 data model. */
object IdGenerator {
    fun newId(): String = UUID.randomUUID().toString()
}
