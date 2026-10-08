// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import java.time.LocalDate

enum class CheckStatus {
  GOOD,
  WARNING,
  ALERT
}

enum class CheckId {
  SECURITY_PATCH,
  ANDROID_VERSION,
  SCREEN_LOCK,
  PASSPHRASE_LOCK,
  DURESS_PASSPHRASE,
  REGISTRATION_LOCK,
  NOTIFICATION_PRIVACY,
  SCREEN_SECURITY,
  INCOGNITO_KEYBOARD,
  LINK_PREVIEWS,
  READ_RECEIPTS,
  TYPING_INDICATORS,
  BLOCK_UNKNOWN
}

data class DeviceSnapshot(
  val securityPatch: String?,
  val sdkInt: Int,
  val androidRelease: String,
  val deviceSecure: Boolean,
  val passphraseLock: Boolean,
  val duressPassphrase: Boolean,
  val registrationLock: Boolean,
  val notificationPrivacy: String,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val linkPreviews: Boolean,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val blockUnknown: Boolean,
  val isPrimaryDevice: Boolean
)

data class CheckResult(val id: CheckId, val status: CheckStatus)

object DeviceChecks {

  const val MIN_SUPPORTED_SDK = 29

  const val NOTIFICATIONS_SHOW_ALL = "all"
  const val NOTIFICATIONS_SHOW_NAME = "contact"
  const val NOTIFICATIONS_SHOW_NOTHING = "none"

  fun evaluate(snapshot: DeviceSnapshot, today: LocalDate): List<CheckResult> {
    return CheckId.entries.map { id -> CheckResult(id, statusOf(id, snapshot, today)) }
  }

  fun countGood(results: List<CheckResult>): Int = results.count { it.status == CheckStatus.GOOD }

  private fun statusOf(id: CheckId, snapshot: DeviceSnapshot, today: LocalDate): CheckStatus {
    return when (id) {
      CheckId.SECURITY_PATCH -> PatchLevel.status(PatchLevel.parse(snapshot.securityPatch), today)
      CheckId.ANDROID_VERSION -> warnUnless(snapshot.sdkInt >= MIN_SUPPORTED_SDK)
      CheckId.SCREEN_LOCK -> if (snapshot.deviceSecure) CheckStatus.GOOD else CheckStatus.ALERT
      CheckId.PASSPHRASE_LOCK -> warnUnless(snapshot.passphraseLock)
      CheckId.DURESS_PASSPHRASE -> warnUnless(snapshot.duressPassphrase)
      CheckId.REGISTRATION_LOCK -> warnUnless(snapshot.registrationLock)
      CheckId.NOTIFICATION_PRIVACY,
      CheckId.SCREEN_SECURITY,
      CheckId.INCOGNITO_KEYBOARD,
      CheckId.LINK_PREVIEWS,
      CheckId.READ_RECEIPTS,
      CheckId.TYPING_INDICATORS,
      CheckId.BLOCK_UNKNOWN -> warnUnless(HardenedSetting.forCheck(id)!!.isHardened(snapshot))
    }
  }

  private fun warnUnless(good: Boolean): CheckStatus = if (good) CheckStatus.GOOD else CheckStatus.WARNING
}
