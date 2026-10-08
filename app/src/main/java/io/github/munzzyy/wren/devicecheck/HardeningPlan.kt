// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

// Passphrase, duress and Registration Lock stay out: each needs a secret only the user can choose.
enum class HardenedSetting(val check: CheckId, val primaryDeviceOnly: Boolean = false, val inBulkPlan: Boolean = true) {
  SCREEN_SECURITY(CheckId.SCREEN_SECURITY),
  INCOGNITO_KEYBOARD(CheckId.INCOGNITO_KEYBOARD),
  NOTIFICATION_PRIVACY(CheckId.NOTIFICATION_PRIVACY),
  LINK_PREVIEWS(CheckId.LINK_PREVIEWS),
  READ_RECEIPTS(CheckId.READ_RECEIPTS, primaryDeviceOnly = true),
  TYPING_INDICATORS(CheckId.TYPING_INDICATORS, primaryDeviceOnly = true),
  BLOCK_UNKNOWN(CheckId.BLOCK_UNKNOWN),
  ORBOT(CheckId.ORBOT, inBulkPlan = false);

  fun isHardened(snapshot: DeviceSnapshot): Boolean {
    return when (this) {
      SCREEN_SECURITY -> snapshot.screenSecurity
      INCOGNITO_KEYBOARD -> snapshot.incognitoKeyboard
      NOTIFICATION_PRIVACY -> snapshot.notificationPrivacy == DeviceChecks.NOTIFICATIONS_SHOW_NOTHING
      LINK_PREVIEWS -> !snapshot.linkPreviews
      READ_RECEIPTS -> !snapshot.readReceipts
      TYPING_INDICATORS -> !snapshot.typingIndicators
      BLOCK_UNKNOWN -> snapshot.blockUnknown
      ORBOT -> snapshot.routedThroughOrbot
    }
  }

  fun canChange(snapshot: DeviceSnapshot): Boolean {
    return when (this) {
      ORBOT -> snapshot.orbotInstalled
      else -> !primaryDeviceOnly || snapshot.isPrimaryDevice
    }
  }

  companion object {
    fun forCheck(id: CheckId): HardenedSetting? = entries.firstOrNull { it.check == id }
  }
}

object HardeningPlan {

  fun changesFor(snapshot: DeviceSnapshot): List<HardenedSetting> {
    return HardenedSetting.entries.filter { it.inBulkPlan && !it.isHardened(snapshot) && it.canChange(snapshot) }
  }

  fun applyTo(snapshot: DeviceSnapshot, changes: Collection<HardenedSetting>): DeviceSnapshot {
    return changes.fold(snapshot) { current, setting -> withSetting(current, setting, hardened = true) }
  }

  fun withSetting(snapshot: DeviceSnapshot, setting: HardenedSetting, hardened: Boolean): DeviceSnapshot {
    return when (setting) {
      HardenedSetting.SCREEN_SECURITY -> snapshot.copy(screenSecurity = hardened)
      HardenedSetting.INCOGNITO_KEYBOARD -> snapshot.copy(incognitoKeyboard = hardened)
      HardenedSetting.NOTIFICATION_PRIVACY -> snapshot.copy(
        notificationPrivacy = if (hardened) DeviceChecks.NOTIFICATIONS_SHOW_NOTHING else DeviceChecks.NOTIFICATIONS_SHOW_ALL
      )
      HardenedSetting.LINK_PREVIEWS -> snapshot.copy(linkPreviews = !hardened)
      HardenedSetting.READ_RECEIPTS -> snapshot.copy(readReceipts = !hardened)
      HardenedSetting.TYPING_INDICATORS -> snapshot.copy(typingIndicators = !hardened)
      HardenedSetting.BLOCK_UNKNOWN -> snapshot.copy(blockUnknown = hardened)
      HardenedSetting.ORBOT -> snapshot.copy(routedThroughOrbot = hardened)
    }
  }
}
