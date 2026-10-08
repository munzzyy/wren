package org.thoughtcrime.securesms.components.settings.app.privacy

import android.content.Context
import io.github.munzzyy.wren.duress.DuressManager
import io.github.munzzyy.wren.duress.DuressStore
import io.github.munzzyy.wren.duress.PanicAction
import io.github.munzzyy.wren.guard.InactivityWipe
import io.github.munzzyy.wren.guard.InactivityWipePolicy
import io.github.munzzyy.wren.guard.UsbLock
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.TextSecurePreferences

class PrivacySettingsRepository {

  private val context: Context = AppDependencies.application

  private val duressStore = DuressStore(context)

  val isDuressPassphraseEnabled: Boolean
    get() = duressStore.duressEnabled

  val failedAttemptLimit: Int
    get() = duressStore.failedAttemptLimit

  val panicAction: PanicAction
    get() = duressStore.panicAction

  val panicTriggerPackage: String?
    get() = duressStore.panicTriggerPackage

  val panicTriggerCertificate: String?
    get() = duressStore.panicTriggerCertificate

  val inactivityWipeDays: Int
    get() = duressStore.inactivityWipeDays

  val isUsbLockEnabled: Boolean
    get() = duressStore.usbLockEnabled

  fun setInactivityWipeDays(days: Int) {
    duressStore.setInactivityWipe(days, System.currentTimeMillis())
    InactivityWipe.checkInBackground(context)
  }

  fun setUsbLockEnabled(enabled: Boolean) {
    duressStore.usbLockEnabled = enabled
    UsbLock.sync(context)
  }

  fun turnOffLockDependents() {
    setInactivityWipeDays(InactivityWipePolicy.OFF)
    setUsbLockEnabled(false)
  }

  fun setFailedAttemptLimit(limit: Int) {
    duressStore.failedAttemptLimit = limit
  }

  fun setPanicAction(action: PanicAction) {
    duressStore.panicAction = action
  }

  fun disconnectPanicTrigger() {
    duressStore.disconnectPanicTrigger()
  }

  fun clearDuressPassphrase(onComplete: () -> Unit) {
    SignalExecutors.BOUNDED.execute {
      DuressManager.clearDuressPassphrase(context)
      onComplete()
    }
  }

  fun getBlockedCount(consumer: (Int) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipientDatabase = SignalDatabase.recipients

      consumer(recipientDatabase.getBlocked().size)
    }
  }

  fun syncReadReceiptState() {
    SignalExecutors.BOUNDED.execute {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          TextSecurePreferences.isReadReceiptsEnabled(context),
          TextSecurePreferences.isTypingIndicatorsEnabled(context),
          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
          SignalStore.settings.isLinkPreviewsEnabled
        )
      )
    }
  }

  fun syncTypingIndicatorsState() {
    val enabled = TextSecurePreferences.isTypingIndicatorsEnabled(context)

    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
    AppDependencies.jobManager.add(
      MultiDeviceConfigurationUpdateJob(
        TextSecurePreferences.isReadReceiptsEnabled(context),
        enabled,
        TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
        SignalStore.settings.isLinkPreviewsEnabled
      )
    )

    if (!enabled) {
      AppDependencies.typingStatusRepository.clear()
    }
  }
}
