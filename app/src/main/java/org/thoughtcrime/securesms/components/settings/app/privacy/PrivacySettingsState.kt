package org.thoughtcrime.securesms.components.settings.app.privacy

import io.github.munzzyy.wren.duress.PanicAction

data class PrivacySettingsState(
  val blockedCount: Int,
  val blockUnknown: Boolean,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val passphraseLock: Boolean,
  val passphraseLockTriggerValues: Set<String>,
  val passphraseLockTimeout: Long,
  val duressPassphrase: Boolean,
  val failedAttemptLimit: Int,
  val panicAction: PanicAction,
  val panicTriggerPackage: String?,
  val biometricScreenLock: Boolean,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val universalExpireTimer: Int
)
