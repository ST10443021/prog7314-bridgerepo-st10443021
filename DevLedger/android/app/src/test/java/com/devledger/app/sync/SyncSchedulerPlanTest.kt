package com.devledger.app.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Automatic sync off must cancel the periodic job; on must schedule it. (Google Developers, n.d.c). See REFERENCES-CODE.md. */
class SyncSchedulerPlanTest {
    @Test fun `automatic sync on schedules the periodic job`() =
        assertThat(SyncScheduler.planFor(SyncPreferences(autoSync = true))).isEqualTo(SyncPlan.PERIODIC)

    @Test fun `automatic sync off cancels the periodic job`() =
        assertThat(SyncScheduler.planFor(SyncPreferences(autoSync = false))).isEqualTo(SyncPlan.CANCEL)

    @Test fun `defaults match a fresh install`() {
        val defaults = SyncPreferences()
        assertThat(defaults.autoSync).isTrue()
        assertThat(defaults.wifiOnly).isFalse()
        assertThat(defaults.frequencyMinutes).isEqualTo(15)
        assertThat(defaults.chargingOnly).isFalse()
    }
}
