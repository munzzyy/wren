// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * PanicKit responder reached through startActivityForResult, which is the only
 * way to learn which app sent the trigger. Molly's broadcast receiver stays for
 * triggers that only broadcast; it can lock but never wipe.
 *
 * Nothing here is logged: the log outlives a wipe and would say what caused it.
 */
class PanicResponderActivity : Activity() {

  companion object {
    const val ACTION_TRIGGER = "info.guardianproject.panic.action.TRIGGER"
    const val ACTION_CONNECT = "info.guardianproject.panic.action.CONNECT"
    const val ACTION_DISCONNECT = "info.guardianproject.panic.action.DISCONNECT"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
      when (intent?.action) {
        ACTION_CONNECT -> handleConnect()
        ACTION_DISCONNECT -> handleDisconnect()
        ACTION_TRIGGER -> handleTrigger()
      }
    } catch (e: RuntimeException) {
      setResult(RESULT_CANCELED)
    }

    finish()
  }

  private fun callerDigest(): String? = callingPackage?.let { SigningCertificates.sha256(packageManager, it) }

  private fun handleConnect() {
    val store = DuressStore(this)
    val caller = callingPackage
    val outcome = PanicDecision.onConnect(store.panicTriggerPackage, store.panicTriggerCertificate, caller, callerDigest(), packageName)
    if (outcome.disconnect) {
      store.disconnectPanicTrigger()
    }
    when (outcome.result) {
      PanicConnectResult.ASK_USER -> {
        startActivity(PanicConnectActivity.createIntent(this, requireNotNull(caller)).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT))
      }
      PanicConnectResult.ALREADY_CONNECTED -> setResult(RESULT_OK)
      PanicConnectResult.REFUSE -> setResult(RESULT_CANCELED)
    }
  }

  private fun handleDisconnect() {
    val store = DuressStore(this)
    if (PanicDecision.shouldDisconnect(store.panicTriggerPackage, store.panicTriggerCertificate, callingPackage, callerDigest())) {
      store.disconnectPanicTrigger()
    }
    setResult(RESULT_OK)
  }

  private fun handleTrigger() {
    val store = DuressStore(this)
    val outcome = PanicDecision.onTrigger(
      action = store.panicAction,
      connectedPackage = store.panicTriggerPackage,
      connectedDigest = store.panicTriggerCertificate,
      callingPackage = callingPackage,
      callingDigest = callerDigest(),
      passphraseLockEnabled = TextSecurePreferences.isPassphraseLockEnabled(this)
    )

    if (outcome.disconnect) {
      store.disconnectPanicTrigger()
    }

    when (outcome.response) {
      PanicResponse.WIPE -> AppWipe.wipeInBackground(this)
      PanicResponse.LOCK -> lock()
      PanicResponse.NOTHING -> Unit
    }
  }

  private fun lock() {
    val lockIntent = Intent(this, KeyCachingService::class.java)
    lockIntent.action = KeyCachingService.CLEAR_KEY_ACTION
    startService(lockIntent)
  }
}
