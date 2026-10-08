// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import io.github.munzzyy.wren.duress.DuressStore
import org.signal.core.util.ServiceUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.TextSecurePreferences

class DeviceCheckRepository(context: Context) {

  private val context: Context = context.applicationContext

  fun snapshot(): DeviceSnapshot {
    return DeviceSnapshot(
      securityPatch = Build.VERSION.SECURITY_PATCH,
      sdkInt = Build.VERSION.SDK_INT,
      androidRelease = Build.VERSION.RELEASE,
      deviceSecure = ServiceUtil.getKeyguardManager(context).isDeviceSecure,
      passphraseLock = TextSecurePreferences.isPassphraseLockEnabled(context),
      duressPassphrase = DuressStore(context).duressEnabled,
      registrationLock = SignalStore.svr.isRegistrationLockEnabled,
      notificationPrivacy = SignalStore.settings.messageNotificationsPrivacy.toString(),
      screenSecurity = TextSecurePreferences.isScreenSecurityEnabled(context),
      incognitoKeyboard = TextSecurePreferences.isIncognitoKeyboardEnabled(context),
      linkPreviews = SignalStore.settings.isLinkPreviewsEnabled,
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context),
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context),
      blockUnknown = TextSecurePreferences.isBlockUnknownEnabled(context),
      isPrimaryDevice = SignalStore.account.isPrimaryDevice
    )
  }

  @WorkerThread
  fun applyHardenedDefaults(changes: Collection<HardenedSetting>) {
    changes.forEach { write(it, hardened = true) }
    syncIfNeeded(changes)
  }

  @WorkerThread
  fun toggle(setting: HardenedSetting) {
    write(setting, hardened = !setting.isHardened(snapshot()))
    syncIfNeeded(listOf(setting))
  }

  private fun write(setting: HardenedSetting, hardened: Boolean) {
    when (setting) {
      HardenedSetting.SCREEN_SECURITY -> TextSecurePreferences.setScreenSecurityEnabled(context, hardened)
      HardenedSetting.INCOGNITO_KEYBOARD -> TextSecurePreferences.getSharedPreferences(context).edit().putBoolean(TextSecurePreferences.INCOGNITO_KEYBOARD_PREF, hardened).apply()
      HardenedSetting.NOTIFICATION_PRIVACY -> SignalStore.settings.messageNotificationsPrivacy = NotificationPrivacyPreference(
        if (hardened) DeviceChecks.NOTIFICATIONS_SHOW_NOTHING else DeviceChecks.NOTIFICATIONS_SHOW_ALL
      )
      HardenedSetting.LINK_PREVIEWS -> SignalStore.settings.isLinkPreviewsEnabled = !hardened
      HardenedSetting.READ_RECEIPTS -> if (SignalStore.account.isPrimaryDevice) TextSecurePreferences.setReadReceiptsEnabled(context, !hardened)
      HardenedSetting.TYPING_INDICATORS -> if (SignalStore.account.isPrimaryDevice) TextSecurePreferences.setTypingIndicatorsEnabled(context, !hardened)
      HardenedSetting.BLOCK_UNKNOWN -> TextSecurePreferences.setBlockUnknownEnabled(context, hardened)
    }
  }

  private fun syncIfNeeded(changes: Collection<HardenedSetting>) {
    val synced = setOf(HardenedSetting.LINK_PREVIEWS, HardenedSetting.READ_RECEIPTS, HardenedSetting.TYPING_INDICATORS)
    if (changes.none { it in synced }) {
      return
    }

    val typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context)

    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
    AppDependencies.jobManager.add(
      MultiDeviceConfigurationUpdateJob(
        TextSecurePreferences.isReadReceiptsEnabled(context),
        typingIndicators,
        TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
        SignalStore.settings.isLinkPreviewsEnabled
      )
    )

    if (!typingIndicators) {
      AppDependencies.typingStatusRepository.clear()
    }
  }
}
