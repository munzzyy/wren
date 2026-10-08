// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.Context
import android.content.SharedPreferences
import io.github.munzzyy.wren.guard.InactivityWipePolicy
import org.signal.core.util.Base64
import java.io.IOException

class DuressStore(context: Context) {

  companion object {
    private const val PREFERENCES_NAME = "wren-duress"

    private const val DURESS_ENABLED = "duress_enabled"
    private const val DURESS_SALT = "duress_salt"
    private const val DURESS_VERIFIER = "duress_verifier"
    private const val DURESS_KDF_PARAMS = "duress_kdf_params"
    private const val DURESS_KEYSTORE_ALIAS = "duress_keystore_alias"
    private const val FAILED_ATTEMPT_LIMIT = "failed_attempt_limit"
    private const val FAILED_ATTEMPT_COUNT = "failed_attempt_count"
    private const val PANIC_ACTION = "panic_action"
    private const val PANIC_TRIGGER_PACKAGE = "panic_trigger_package"
    private const val INACTIVITY_WIPE_DAYS = "inactivity_wipe_days"
    private const val LAST_UNLOCK_AT = "last_unlock_at"
    private const val USB_LOCK = "usb_lock"
  }

  private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  val duressEnabled: Boolean
    get() = prefs.getBoolean(DURESS_ENABLED, false) &&
      duressSalt != null &&
      duressVerifier != null &&
      duressKdfParams != null &&
      duressKeyStoreAlias != null

  val duressSalt: ByteArray?
    get() = getBytes(DURESS_SALT)

  val duressVerifier: ByteArray?
    get() = getBytes(DURESS_VERIFIER)

  val duressKdfParams: String?
    get() = prefs.getString(DURESS_KDF_PARAMS, null)

  val duressKeyStoreAlias: String?
    get() = prefs.getString(DURESS_KEYSTORE_ALIAS, null)

  fun setDuress(salt: ByteArray, verifier: ByteArray, kdfParams: String, keyStoreAlias: String) {
    commit(
      prefs.edit()
        .putString(DURESS_SALT, Base64.encodeWithPadding(salt))
        .putString(DURESS_VERIFIER, Base64.encodeWithPadding(verifier))
        .putString(DURESS_KDF_PARAMS, kdfParams)
        .putString(DURESS_KEYSTORE_ALIAS, keyStoreAlias)
        .putBoolean(DURESS_ENABLED, true)
    )
  }

  fun clearDuress() {
    commit(
      prefs.edit()
        .putBoolean(DURESS_ENABLED, false)
        .remove(DURESS_SALT)
        .remove(DURESS_VERIFIER)
        .remove(DURESS_KDF_PARAMS)
        .remove(DURESS_KEYSTORE_ALIAS)
    )
  }

  var failedAttemptLimit: Int
    get() = FailedAttemptPolicy.sanitizeLimit(prefs.getInt(FAILED_ATTEMPT_LIMIT, FailedAttemptPolicy.OFF))
    set(value) {
      commit(
        prefs.edit()
          .putInt(FAILED_ATTEMPT_LIMIT, FailedAttemptPolicy.sanitizeLimit(value))
          .putInt(FAILED_ATTEMPT_COUNT, 0)
      )
    }

  var failedAttemptCount: Int
    get() = prefs.getInt(FAILED_ATTEMPT_COUNT, 0).coerceAtLeast(0)
    set(value) {
      commit(prefs.edit().putInt(FAILED_ATTEMPT_COUNT, value))
    }

  var panicAction: PanicAction
    get() = PanicAction.fromKey(prefs.getString(PANIC_ACTION, null))
    set(value) {
      val allowed = if (panicTriggerPackage == null) PanicAction.LOCK else value
      commit(prefs.edit().putString(PANIC_ACTION, allowed.key))
    }

  val panicTriggerPackage: String?
    get() = prefs.getString(PANIC_TRIGGER_PACKAGE, null)?.takeIf { it.isNotEmpty() }

  /** A new trigger always starts on LOCK; the user picks WIPE after seeing which app is connected. */
  fun connectPanicTrigger(packageName: String) {
    commit(
      prefs.edit()
        .putString(PANIC_TRIGGER_PACKAGE, packageName)
        .putString(PANIC_ACTION, PanicAction.LOCK.key)
    )
  }

  fun disconnectPanicTrigger() {
    commit(
      prefs.edit()
        .remove(PANIC_TRIGGER_PACKAGE)
        .putString(PANIC_ACTION, PanicAction.LOCK.key)
    )
  }

  val inactivityWipeDays: Int
    get() = InactivityWipePolicy.sanitizeDays(prefs.getInt(INACTIVITY_WIPE_DAYS, InactivityWipePolicy.OFF))

  /** Every change restarts the countdown, and turning it off forgets when Wren was last unlocked. */
  fun setInactivityWipe(days: Int, nowMillis: Long) {
    val sanitized = InactivityWipePolicy.sanitizeDays(days)
    val editor = prefs.edit().putInt(INACTIVITY_WIPE_DAYS, sanitized)
    if (sanitized == InactivityWipePolicy.OFF) {
      editor.remove(LAST_UNLOCK_AT)
    } else {
      editor.putLong(LAST_UNLOCK_AT, nowMillis)
    }
    commit(editor)
  }

  var lastUnlockAt: Long
    get() = prefs.getLong(LAST_UNLOCK_AT, 0L)
    set(value) {
      commit(prefs.edit().putLong(LAST_UNLOCK_AT, value))
    }

  var usbLockEnabled: Boolean
    get() = prefs.getBoolean(USB_LOCK, false)
    set(value) {
      commit(prefs.edit().putBoolean(USB_LOCK, value))
    }

  private fun getBytes(key: String): ByteArray? {
    val encoded = prefs.getString(key, null) ?: return null
    return try {
      Base64.decode(encoded).takeIf { it.isNotEmpty() }
    } catch (e: IOException) {
      null
    }
  }

  private fun commit(editor: SharedPreferences.Editor) {
    check(editor.commit()) { "Failed to save $PREFERENCES_NAME" }
  }
}
