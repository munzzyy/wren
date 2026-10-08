// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DeviceChecksTest {

  private val today = LocalDate.of(2026, 10, 8)

  private fun statuses(snapshot: DeviceSnapshot): Map<CheckId, CheckStatus> {
    return DeviceChecks.evaluate(snapshot, today).associate { it.id to it.status }
  }

  private fun onlyChanged(snapshot: DeviceSnapshot): Map<CheckId, CheckStatus> {
    return statuses(snapshot).filterValues { it != CheckStatus.GOOD }
  }

  @Test
  fun `every check has exactly one result in a stable order`() {
    val results = DeviceChecks.evaluate(DeviceSnapshots.hardened, today)
    assertEquals(CheckId.entries.toList(), results.map { it.id })
  }

  @Test
  fun `a hardened phone passes everything`() {
    val results = DeviceChecks.evaluate(DeviceSnapshots.hardened, today)
    assertEquals(emptyMap<CheckId, CheckStatus>(), onlyChanged(DeviceSnapshots.hardened))
    assertEquals(CheckId.entries.size, DeviceChecks.countGood(results))
  }

  @Test
  fun `each weak setting flags its own row and nothing else`() {
    val base = DeviceSnapshots.hardened
    val cases = mapOf(
      base.copy(securityPatch = "2026-03-01") to (CheckId.SECURITY_PATCH to CheckStatus.ALERT),
      base.copy(securityPatch = "2026-06-01") to (CheckId.SECURITY_PATCH to CheckStatus.WARNING),
      base.copy(securityPatch = null) to (CheckId.SECURITY_PATCH to CheckStatus.WARNING),
      base.copy(sdkInt = 28) to (CheckId.ANDROID_VERSION to CheckStatus.WARNING),
      base.copy(deviceSecure = false) to (CheckId.SCREEN_LOCK to CheckStatus.ALERT),
      base.copy(passphraseLock = false) to (CheckId.PASSPHRASE_LOCK to CheckStatus.WARNING),
      base.copy(duressPassphrase = false) to (CheckId.DURESS_PASSPHRASE to CheckStatus.WARNING),
      base.copy(registrationLock = false) to (CheckId.REGISTRATION_LOCK to CheckStatus.WARNING),
      base.copy(notificationPrivacy = DeviceChecks.NOTIFICATIONS_SHOW_NAME) to (CheckId.NOTIFICATION_PRIVACY to CheckStatus.WARNING),
      base.copy(notificationPrivacy = DeviceChecks.NOTIFICATIONS_SHOW_ALL) to (CheckId.NOTIFICATION_PRIVACY to CheckStatus.WARNING),
      base.copy(screenSecurity = false) to (CheckId.SCREEN_SECURITY to CheckStatus.WARNING),
      base.copy(incognitoKeyboard = false) to (CheckId.INCOGNITO_KEYBOARD to CheckStatus.WARNING),
      base.copy(linkPreviews = true) to (CheckId.LINK_PREVIEWS to CheckStatus.WARNING),
      base.copy(readReceipts = true) to (CheckId.READ_RECEIPTS to CheckStatus.WARNING),
      base.copy(typingIndicators = true) to (CheckId.TYPING_INDICATORS to CheckStatus.WARNING),
      base.copy(blockUnknown = false) to (CheckId.BLOCK_UNKNOWN to CheckStatus.WARNING),
      base.copy(routedThroughOrbot = false) to (CheckId.ORBOT to CheckStatus.WARNING)
    )

    for ((snapshot, expected) in cases) {
      assertEquals(snapshot.toString(), mapOf(expected), onlyChanged(snapshot))
    }
  }

  @Test
  fun `android 10 is the lowest version that passes`() {
    assertEquals(CheckStatus.GOOD, statuses(DeviceSnapshots.hardened.copy(sdkInt = 29))[CheckId.ANDROID_VERSION])
    assertEquals(CheckStatus.WARNING, statuses(DeviceSnapshots.hardened.copy(sdkInt = 28))[CheckId.ANDROID_VERSION])
    assertEquals(CheckStatus.WARNING, statuses(DeviceSnapshots.hardened.copy(sdkInt = 27))[CheckId.ANDROID_VERSION])
  }

  @Test
  fun `a stock install flags every Wren setting but not the phone`() {
    val flagged = onlyChanged(DeviceSnapshots.stock).keys
    val expected = CheckId.entries.toSet() - setOf(CheckId.SECURITY_PATCH, CheckId.ANDROID_VERSION, CheckId.SCREEN_LOCK)
    assertEquals(expected, flagged)
    assertEquals(3, DeviceChecks.countGood(DeviceChecks.evaluate(DeviceSnapshots.stock, today)))
  }

  @Test
  fun `the Orbot row only shows when Orbot is installed`() {
    val withoutOrbot = DeviceSnapshots.stock.copy(orbotInstalled = false)
    val ids = DeviceChecks.evaluate(withoutOrbot, today).map { it.id }
    assertEquals(CheckId.entries.toList() - CheckId.ORBOT, ids)
    assertEquals(CheckStatus.WARNING, statuses(DeviceSnapshots.stock)[CheckId.ORBOT])
  }

  @Test
  fun `an unknown notification privacy value is not treated as hidden`() {
    val snapshot = DeviceSnapshots.hardened.copy(notificationPrivacy = "")
    assertEquals(CheckStatus.WARNING, statuses(snapshot)[CheckId.NOTIFICATION_PRIVACY])
  }
}
