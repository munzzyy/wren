// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.Context
import android.content.SharedPreferences
import io.github.munzzyy.wren.guard.InactivityWipePolicy
import org.signal.core.util.Base64
import org.signal.core.util.Util
import java.io.File
import java.io.IOException

class DuressStore(context: Context) {

  companion object {
    private const val PREFERENCES_NAME = "wren-duress"
    private const val GUARD_STATE_FILE = "wren-guard-state"

    private const val DURESS_ENABLED = "duress_enabled"
    private const val DURESS_SALT = "duress_salt"
    private const val DURESS_VERIFIER = "duress_verifier"
    private const val DURESS_KDF_PARAMS = "duress_kdf_params"
    private const val DURESS_KEYSTORE_ALIAS = "duress_keystore_alias"
    private const val DECOY_SALT = "decoy_salt"
    private const val DECOY_VERIFIER = "decoy_verifier"
    private const val FAILED_ATTEMPT_LIMIT = "failed_attempt_limit"
    private const val FAILED_ATTEMPT_COUNT = "failed_attempt_count"
    private const val PANIC_ACTION = "panic_action"
    private const val PANIC_TRIGGER_PACKAGE = "panic_trigger_package"
    private const val PANIC_TRIGGER_CERTIFICATE = "panic_trigger_certificate"
    private const val INACTIVITY_WIPE_DAYS = "inactivity_wipe_days"
    private const val LAST_UNLOCK_AT = "last_unlock_at"
    private const val USB_LOCK = "usb_lock"

    /** Every store instance shares the one guard state file. */
    private val GUARD_STATE_LOCK = Any()
  }

  private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val guardFile = GuardStateFile(File(context.applicationContext.noBackupFilesDir, GUARD_STATE_FILE))

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

  /** A random salt and verifier that no passphrase matches, created the first time it is needed. */
  fun decoy(): Pair<ByteArray, ByteArray> {
    val salt = getBytes(DECOY_SALT)
    val verifier = getBytes(DECOY_VERIFIER)
    if (salt != null && salt.size == DuressVerifier.SALT_LENGTH && verifier != null && verifier.size == DuressVerifier.VERIFIER_LENGTH) {
      return salt to verifier
    }

    val newSalt = Util.getSecretBytes(DuressVerifier.SALT_LENGTH)
    val newVerifier = Util.getSecretBytes(DuressVerifier.VERIFIER_LENGTH)
    commit(
      prefs.edit()
        .putString(DECOY_SALT, Base64.encodeWithPadding(newSalt))
        .putString(DECOY_VERIFIER, Base64.encodeWithPadding(newVerifier))
    )
    return newSalt to newVerifier
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
      synchronized(GUARD_STATE_LOCK) {
        commit(prefs.edit().putInt(FAILED_ATTEMPT_LIMIT, FailedAttemptPolicy.sanitizeLimit(value)))
        writeGuardState(readGuardState().copy(failedAttempts = 0))
      }
    }

  /** Both throw when the file can't be read or saved. */
  var failedAttemptCount: Int
    get() = readGuardState().failedAttempts
    set(value) {
      synchronized(GUARD_STATE_LOCK) {
        writeGuardState(readGuardState().copy(failedAttempts = value.coerceAtLeast(0)))
      }
    }

  /** Runs [block] with the guard state file to itself. */
  fun <T> withGuardStateLocked(block: () -> T): T = synchronized(GUARD_STATE_LOCK) { block() }

  var panicAction: PanicAction
    get() = PanicAction.fromKey(prefs.getString(PANIC_ACTION, null))
    set(value) {
      val allowed = if (panicTriggerPackage == null) PanicAction.LOCK else value
      commit(prefs.edit().putString(PANIC_ACTION, allowed.key))
    }

  /** A trigger connected before certificates were recorded counts as not connected. */
  val panicTriggerPackage: String?
    get() = prefs.getString(PANIC_TRIGGER_PACKAGE, null)?.takeIf { it.isNotEmpty() && panicTriggerCertificate != null }

  /** SHA-256 of the trigger's signing certificate, from [SigningCertificates.sha256]. */
  val panicTriggerCertificate: String?
    get() = prefs.getString(PANIC_TRIGGER_CERTIFICATE, null)?.takeIf { it.isNotEmpty() }

  /** A new trigger always starts on LOCK; the user picks WIPE after seeing which app is connected. */
  fun connectPanicTrigger(packageName: String, certificateDigest: String) {
    commit(
      prefs.edit()
        .putString(PANIC_TRIGGER_PACKAGE, packageName)
        .putString(PANIC_TRIGGER_CERTIFICATE, certificateDigest)
        .putString(PANIC_ACTION, PanicAction.LOCK.key)
    )
  }

  fun disconnectPanicTrigger() {
    commit(
      prefs.edit()
        .remove(PANIC_TRIGGER_PACKAGE)
        .remove(PANIC_TRIGGER_CERTIFICATE)
        .putString(PANIC_ACTION, PanicAction.LOCK.key)
    )
  }

  val inactivityWipeDays: Int
    get() = InactivityWipePolicy.sanitizeDays(prefs.getInt(INACTIVITY_WIPE_DAYS, InactivityWipePolicy.OFF))

  /** Every change restarts the countdown, and turning it off forgets when Wren was last unlocked. */
  fun setInactivityWipe(days: Int, nowMillis: Long) {
    val sanitized = InactivityWipePolicy.sanitizeDays(days)
    synchronized(GUARD_STATE_LOCK) {
      commit(prefs.edit().putInt(INACTIVITY_WIPE_DAYS, sanitized))
      writeGuardState(readGuardState().copy(lastUnlockAt = if (sanitized == InactivityWipePolicy.OFF) 0L else nowMillis))
    }
  }

  var lastUnlockAt: Long
    get() = readGuardState().lastUnlockAt
    set(value) {
      synchronized(GUARD_STATE_LOCK) {
        writeGuardState(readGuardState().copy(lastUnlockAt = value))
      }
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

  private fun readGuardState(): GuardStateFile.State {
    synchronized(GUARD_STATE_LOCK) {
      return try {
        guardFile.readOrMigrate(
          legacy = {
            GuardStateFile.State(
              failedAttempts = prefs.getInt(FAILED_ATTEMPT_COUNT, 0).coerceAtLeast(0),
              lastUnlockAt = prefs.getLong(LAST_UNLOCK_AT, 0L).coerceAtLeast(0L)
            )
          },
          clearLegacy = { commit(prefs.edit().remove(FAILED_ATTEMPT_COUNT).remove(LAST_UNLOCK_AT)) }
        )
      } catch (e: IOException) {
        throw IllegalStateException("Failed to read the guard state", e)
      }
    }
  }

  private fun writeGuardState(state: GuardStateFile.State) {
    try {
      guardFile.write(state)
    } catch (e: IOException) {
      throw IllegalStateException("Failed to save the guard state", e)
    }
  }

  private fun commit(editor: SharedPreferences.Editor) {
    check(editor.commit()) { "Failed to save $PREFERENCES_NAME" }
  }
}
