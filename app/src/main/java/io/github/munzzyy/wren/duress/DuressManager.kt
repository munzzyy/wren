// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.util.Util
import org.signal.core.util.crypto.KeyStoreHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.PassphraseBasedKdf
import java.util.UUID

object DuressManager {

  private val TAG = Log.tag(DuressManager::class.java)

  private const val KEYSTORE_ALIAS_PREFIX = "WrenDuress-"

  /** Runs after the real passphrase was rejected. Wipes and never returns if the policy says so. */
  @JvmStatic
  @WorkerThread
  fun onWrongPassphrase(context: Context, passphrase: CharArray) {
    val store = DuressStore(context)

    if (isDuressPassphrase(store, passphrase)) {
      Log.w(TAG, "Duress passphrase entered")
      AppWipe.wipeNow(context)
      return
    }

    try {
      val outcome = FailedAttemptPolicy.onFailedAttempt(store.failedAttemptCount, store.failedAttemptLimit)
      store.failedAttemptCount = outcome.count
      if (outcome.wipe) {
        Log.w(TAG, "Failed unlock limit reached")
        AppWipe.wipeNow(context)
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not record failed unlock", e)
    }
  }

  @JvmStatic
  @WorkerThread
  fun onUnlocked(context: Context) {
    try {
      val store = DuressStore(context)
      if (store.failedAttemptCount != 0) {
        store.failedAttemptCount = FailedAttemptPolicy.onSuccessfulAttempt()
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not reset failed unlock count", e)
    }
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
      Log.w(TAG, "Duress check failed", e)
      false
    }
  }

  private fun keyStoreKdf(serializedParams: String, keyStoreAlias: String): (CharArray, ByteArray) -> ByteArray {
    val kdf = PassphraseBasedKdf()
    kdf.setParameters(serializedParams)
    kdf.setHmacKey(requireNotNull(KeyStoreHelper.getKeyStoreEntryHmac(keyStoreAlias)))

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
