// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

object DeviceSnapshots {

  val hardened = DeviceSnapshot(
    securityPatch = "2026-09-05",
    sdkInt = 36,
    androidRelease = "16",
    deviceSecure = true,
    passphraseLock = true,
    duressPassphrase = true,
    registrationLock = true,
    notificationPrivacy = DeviceChecks.NOTIFICATIONS_SHOW_NOTHING,
    screenSecurity = true,
    incognitoKeyboard = true,
    linkPreviews = false,
    readReceipts = false,
    typingIndicators = false,
    blockUnknown = true,
    isPrimaryDevice = true
  )

  val stock = DeviceSnapshot(
    securityPatch = "2026-09-05",
    sdkInt = 36,
    androidRelease = "16",
    deviceSecure = true,
    passphraseLock = false,
    duressPassphrase = false,
    registrationLock = false,
    notificationPrivacy = DeviceChecks.NOTIFICATIONS_SHOW_ALL,
    screenSecurity = false,
    incognitoKeyboard = false,
    linkPreviews = true,
    readReceipts = true,
    typingIndicators = true,
    blockUnknown = false,
    isPrimaryDevice = true
  )
}
