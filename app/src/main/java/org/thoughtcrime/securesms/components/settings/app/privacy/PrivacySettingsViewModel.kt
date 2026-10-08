package org.thoughtcrime.securesms.components.settings.app.privacy

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.munzzyy.wren.duress.PanicAction
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class PrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: PrivacySettingsRepository
) : ViewModel() {

  private val application: Application = AppDependencies.application

  private val store = Store(getState())

  val state: LiveData<PrivacySettingsState> = store.stateLiveData

  fun refreshBlockedCount() {
    repository.getBlockedCount { count ->
      store.update { it.copy(blockedCount = count) }
      refresh()
    }
  }

  fun setBlockUnknownEnabled(enabled: Boolean) {
    TextSecurePreferences.setBlockUnknownEnabled(application, enabled)
    refresh()
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.READ_RECEIPTS_PREF, enabled).apply()
    repository.syncReadReceiptState()
    refresh()
  }

  fun setTypingIndicatorsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.TYPING_INDICATORS, enabled).apply()
    repository.syncTypingIndicatorsState()
    refresh()
  }

  fun setPassphraseLockEnabled(enabled: Boolean) {
    TextSecurePreferences.setPassphraseLockEnabled(application, enabled)
    if (!enabled) {
      repository.turnOffLockDependents()
      repository.clearDuressPassphrase { refresh() }
    }
    refresh()
  }

  fun setPassphraseLockTrigger(resultSet: Set<String>) {
    sharedPreferences.edit().putStringSet(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER, resultSet).apply()
    refresh()
  }

  fun setPassphraseLockTimeout(seconds: Long) {
    sharedPreferences.edit().putLong(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT, seconds).apply()
    refresh()
  }

  fun setFailedAttemptLimit(limit: Int) {
    repository.setFailedAttemptLimit(limit)
    refresh()
  }

  fun setInactivityWipeDays(days: Int) {
    repository.setInactivityWipeDays(days)
    refresh()
  }

  fun setUsbLockEnabled(enabled: Boolean) {
    repository.setUsbLockEnabled(enabled)
    refresh()
  }

  fun setPanicAction(action: PanicAction) {
    repository.setPanicAction(action)
    refresh()
  }

  fun disconnectPanicTrigger() {
    repository.disconnectPanicTrigger()
    refresh()
  }

  fun setBiometricScreenLock(enabled: Boolean) {
    TextSecurePreferences.setBiometricScreenLockEnabled(application, enabled)
    ScreenLockController.enableAutoLock(enabled)
    ScreenLockController.lockScreenAtStart = false
    refresh()
  }

  fun setScreenSecurityEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, enabled).apply()
    refresh()
  }

  fun setIncognitoKeyboard(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.INCOGNITO_KEYBOARD_PREF, enabled).apply()
    refresh()
  }

  fun refresh() {
    store.update(this::updateState)
  }

  private fun getState(): PrivacySettingsState {
    return PrivacySettingsState(
      blockedCount = 0,
      blockUnknown = TextSecurePreferences.isBlockUnknownEnabled(application),
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(application),
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(application),
      passphraseLock = TextSecurePreferences.isPassphraseLockEnabled(application),
      passphraseLockTriggerValues = TextSecurePreferences.getPassphraseLockTrigger(application).triggers,
      passphraseLockTimeout = TextSecurePreferences.getPassphraseLockTimeout(application),
      duressPassphrase = repository.isDuressPassphraseEnabled,
      failedAttemptLimit = repository.failedAttemptLimit,
      panicAction = repository.panicAction,
      panicTriggerPackage = repository.panicTriggerPackage,
      inactivityWipeDays = repository.inactivityWipeDays,
      usbLock = repository.isUsbLockEnabled,
      biometricScreenLock = TextSecurePreferences.isBiometricScreenLockEnabled(application),
      screenSecurity = TextSecurePreferences.isScreenSecurityEnabled(application),
      incognitoKeyboard = TextSecurePreferences.isIncognitoKeyboardEnabled(application),
      universalExpireTimer = SignalStore.settings.universalExpireTimer
    )
  }

  private fun updateState(state: PrivacySettingsState): PrivacySettingsState {
    return getState().copy(blockedCount = state.blockedCount)
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: PrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PrivacySettingsViewModel(sharedPreferences, repository)))
    }
  }
}
