// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningPlanTest {

  @Test
  fun `nothing to change on a hardened phone`() {
    assertEquals(emptyList<HardenedSetting>(), HardeningPlan.changesFor(DeviceSnapshots.hardened))
  }

  @Test
  fun `a stock install gets all seven changes in display order`() {
    assertEquals(HardenedSetting.entries.toList(), HardeningPlan.changesFor(DeviceSnapshots.stock))
  }

  @Test
  fun `the plan lists only what differs`() {
    val snapshot = DeviceSnapshots.hardened.copy(linkPreviews = true, blockUnknown = false)
    assertEquals(listOf(HardenedSetting.LINK_PREVIEWS, HardenedSetting.BLOCK_UNKNOWN), HardeningPlan.changesFor(snapshot))
  }

  @Test
  fun `applying the plan leaves nothing left to change`() {
    val after = HardeningPlan.applyTo(DeviceSnapshots.stock, HardeningPlan.changesFor(DeviceSnapshots.stock))
    assertEquals(emptyList<HardenedSetting>(), HardeningPlan.changesFor(after))
    assertEquals(DeviceChecks.NOTIFICATIONS_SHOW_NOTHING, after.notificationPrivacy)
  }

  @Test
  fun `the plan never touches the passphrase, duress or Registration Lock`() {
    val weakSecrets = DeviceSnapshots.hardened.copy(passphraseLock = false, duressPassphrase = false, registrationLock = false, deviceSecure = false)
    assertEquals(emptyList<HardenedSetting>(), HardeningPlan.changesFor(weakSecrets))

    val after = HardeningPlan.applyTo(DeviceSnapshots.stock, HardenedSetting.entries)
    assertFalse(after.passphraseLock)
    assertFalse(after.duressPassphrase)
    assertFalse(after.registrationLock)
    assertEquals(DeviceSnapshots.stock.deviceSecure, after.deviceSecure)

    for (id in listOf(CheckId.PASSPHRASE_LOCK, CheckId.DURESS_PASSPHRASE, CheckId.REGISTRATION_LOCK, CheckId.SCREEN_LOCK, CheckId.SECURITY_PATCH, CheckId.ANDROID_VERSION)) {
      assertNull(id.name, HardenedSetting.forCheck(id))
    }
  }

  @Test
  fun `a linked device skips the settings only the primary can change`() {
    val linked = DeviceSnapshots.stock.copy(isPrimaryDevice = false)
    val plan = HardeningPlan.changesFor(linked)
    assertFalse(HardenedSetting.READ_RECEIPTS in plan)
    assertFalse(HardenedSetting.TYPING_INDICATORS in plan)
    assertEquals(5, plan.size)
  }

  @Test
  fun `withSetting round-trips each setting both ways`() {
    for (setting in HardenedSetting.entries) {
      val on = HardeningPlan.withSetting(DeviceSnapshots.stock, setting, hardened = true)
      val off = HardeningPlan.withSetting(on, setting, hardened = false)
      assertTrue(setting.name, setting.isHardened(on))
      assertFalse(setting.name, setting.isHardened(off))
      assertEquals(setting.name, listOf(setting), HardenedSetting.entries.filter { it.isHardened(on) != it.isHardened(DeviceSnapshots.stock) })
    }
  }

  @Test
  fun `every hardened setting maps back to its own check`() {
    for (setting in HardenedSetting.entries) {
      assertEquals(setting, HardenedSetting.forCheck(setting.check))
    }
  }
}
