// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCheckTapTest {

  @Test
  fun `a hardened row opens its settings instead of turning off`() {
    for (setting in HardenedSetting.entries) {
      assertEquals(setting.name, TapAction.OPEN_SETTINGS, HardeningPlan.onTap(setting, DeviceSnapshots.hardened))
    }
  }

  @Test
  fun `a weak row hardens`() {
    for (setting in HardenedSetting.entries) {
      assertEquals(setting.name, TapAction.HARDEN, HardeningPlan.onTap(setting, DeviceSnapshots.stock))
    }
  }

  @Test
  fun `no tap ever produces a weaker snapshot`() {
    for (start in listOf(DeviceSnapshots.stock, DeviceSnapshots.hardened)) {
      for (setting in HardenedSetting.entries) {
        if (HardeningPlan.onTap(setting, start) == TapAction.HARDEN) {
          val after = HardeningPlan.withSetting(start, setting, hardened = true)
          for (other in HardenedSetting.entries) {
            if (other.isHardened(start)) {
              assertEquals("${setting.name} weakened ${other.name}", true, other.isHardened(after))
            }
          }
        }
      }
    }
  }

  @Test
  fun `a weak row that cannot be changed here does nothing`() {
    val linked = DeviceSnapshots.stock.copy(isPrimaryDevice = false, orbotInstalled = false)
    assertEquals(TapAction.NONE, HardeningPlan.onTap(HardenedSetting.READ_RECEIPTS, linked))
    assertEquals(TapAction.NONE, HardeningPlan.onTap(HardenedSetting.TYPING_INDICATORS, linked))
    assertEquals(TapAction.NONE, HardeningPlan.onTap(HardenedSetting.ORBOT, linked))
  }

  @Test
  fun `each setting points at the screen that holds it`() {
    assertEquals(SettingsScreen.CHATS, HardenedSetting.LINK_PREVIEWS.screen)
    assertEquals(SettingsScreen.NOTIFICATIONS, HardenedSetting.NOTIFICATION_PRIVACY.screen)
    assertEquals(SettingsScreen.PROXY, HardenedSetting.ORBOT.screen)
    for (setting in listOf(HardenedSetting.SCREEN_SECURITY, HardenedSetting.INCOGNITO_KEYBOARD, HardenedSetting.READ_RECEIPTS, HardenedSetting.TYPING_INDICATORS, HardenedSetting.BLOCK_UNKNOWN)) {
      assertEquals(setting.name, SettingsScreen.PRIVACY, setting.screen)
    }
  }
}
