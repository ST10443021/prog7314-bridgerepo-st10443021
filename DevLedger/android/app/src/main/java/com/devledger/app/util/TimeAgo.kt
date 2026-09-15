package com.devledger.app.util

import java.util.concurrent.TimeUnit

/** Formats a millis timestamp as a relative "2h ago" label for snippet/notification
 *  cards, matching the relative-time labels in the Part 1 Vault mockup. */
object TimeAgo {
    fun format(timestampMillis: Long): String {
        val diff = System.currentTimeMillis() - timestampMillis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff) / 7}w ago"
        }
    }
}