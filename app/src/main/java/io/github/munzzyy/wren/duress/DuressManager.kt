// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.Context
import androidx.annotation.WorkerThread
import io.github.munzzyy.wren.guard.InactivityWipePolicy
import org.signal.core.util.Util
import org.signal.core.util.crypto.KeyStoreHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.PassphraseBasedKdf
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.UUID

object DuressManager {

  private val TAG = Log.tag(DuressManager::class.java)

  private const val KEYSTORE_ALIAS_PREFIX = "WrenDuress-"

  /**
   * Runs before every passphrase check. Counts the attempt first, so killing the app during the
   * KDF does not take the guess back. False means the attempt must not go ahead. Wipes and never
   * returns if an earlier count already reached the limit.
   */
  @JvmStatic
  @WorkerThread
  fun onAttemptStarting(context: Context): Boolean {
    return unlockAttempts(context, DuressStore(context)).onStarting()
  }

  /** Runs after the real passphrase was rejected. Wipes and never returns if the policy says so. */
  @JvmStatic
  @WorkerThread
  fun onWrongPassphrase(context: Context, passphrase: CharArray) {
    val store = DuressStore(context)
    val check = DuressCheck.plan(store.duressEnabled, TextSecurePreferences.isPassphraseLockEnabled(context))

    if (check.matches(real = { isDuressPassphrase(store, passphrase) }, decoy = { runDecoy(context, store, passphrase) })) {
      AppWipe.wipeNow(context)
      return
    }

    unlockAttempts(context, store).onRejected()
  }

  /** Same KDF parameters and KeyStore HMAC as the lock itself, against a verifier nothing matches. */
  private fun runDecoy(context: Context, store: DuressStore, passphrase: CharArray) {
    runCatching {
      val params = MasterSecretUtil.getKdfParameters(context).takeIf { it.isNotEmpty() } ?: return
      val (salt, verifier) = store.decoy()
      DuressVerifier(keyStoreKdf(params, MasterSecretUtil.getKeyStoreAlias(context))).matches(passphrase, salt, verifier)
    }
  }

  @JvmStatic
  @WorkerThread
  fun onUnlocked(context: Context) {
    val store = DuressStore(context)
    if (!unlockAttempts(context, store).onSucceeded()) {
      Log.w(TAG, "Could not record unlock")
    }
    try {
      if (store.inactivityWipeDays != InactivityWipePolicy.OFF) {
        store.lastUnlockAt = System.currentTimeMillis()
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not record unlock", e)
    }
  }

  private fun unlockAttempts(context: Context, store: DuressStore): UnlockAttempts {
    val counter = object : UnlockAttempts.Counter {
      override val limit: Int
        get() = store.failedAttemptLimit

      override var count: Int
        get() = store.failedAttemptCount
        set(value) {
          store.failedAttemptCount = value
        }

      override fun <T> locked(block: () -> T): T = store.withGuardStateLocked(block)
    }
    return UnlockAttempts(counter) { AppWipe.wipeNow(context) }
  }

  @WorkerThread
  fun setDuressPassphrase(context: Context, passphrase: CharArray) {
    val kdfParams = MasterSecretUtil.getKdfParameters(context)
    check(kdfParams.isNotEmpty()) { "Passphrase lock is not set up" }

    val store = DuressStore(context)
    val previousAlias = store.duressKeyStoreAlias
    val alias = KEYSTORE_ALIAS_PREFIX + UUID.randomUUID()
    val salt = Util.getSecretBytes(DuressVerifier.SALT_LENGTH)

    KeyStoreHelper.createKeyStoreEntryHmac(alias, MasterSecretUtil.hasStrongBoxKeyStore(context))

    try {
      val verifier = DuressVerifier(keyStoreKdf(kdfParams, alias)).createVerifier(passphrase, salt)
      store.setDuress(salt, verifier, kdfParams, alias)
    } catch (e: RuntimeException) {
      deleteKeyStoreEntry(alias)
      throw e
    }

    if (previousAlias != null && previousAlias != alias) {
      deleteKeyStoreEntry(previousAlias)
    }
  }

  @WorkerThread
  fun clearDuressPassphrase(context: Context) {
    val store = DuressStore(context)
    val alias = store.duressKeyStoreAlias
    store.clearDuress()
    if (alias != null) {
      deleteKeyStoreEntry(alias)
    }
  }

  @JvmStatic
  @WorkerThread
  fun isDuressPassphrase(context: Context, passphrase: CharArray): Boolean {
    return isDuressPassphrase(DuressStore(context), passphrase)
  }

  private fun isDuressPassphrase(store: DuressStore, passphrase: CharArray): Boolean {
    return try {
      if (!store.duressEnabled) {
        return false
      }
      val salt = store.duressSalt ?: return false
      val verifier = store.duressVerifier ?: return false
      val params = store.duressKdfParams ?: return false
      val alias = store.duressKeyStoreAlias ?: return false

      DuressVerifier(keyStoreKdf(params, alias)).matches(passphrase, salt, verifier)
    } catch (e: Throwable) {
      false
    }
  }

  private fun keyStoreKdf(serializedParams: String, keyStoreAlias: String?): (CharArray, ByteArray) -> ByteArray {
    val kdf = PassphraseBasedKdf()
    kdf.setParameters(serializedParams)
    if (keyStoreAlias != null) {
      kdf.setHmacKey(requireNotNull(KeyStoreHelper.getKeyStoreEntryHmac(keyStoreAlias)))
    }

    return { passphrase, salt ->
      val key = kdf.deriveKey(passphrase, salt)
      try {
        key.encoded.copyOf()
      } finally {
        key.destroy()
      }
    }
  }

  private fun deleteKeyStoreEntry(alias: String) {
    try {
      KeyStoreHelper.deleteKeyStoreEntry(alias)
    } catch (e: Throwable) {
      Log.w(TAG, "Could not delete KeyStore entry", e)
    }
  }
}
