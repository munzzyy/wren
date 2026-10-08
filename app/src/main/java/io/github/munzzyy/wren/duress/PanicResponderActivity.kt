// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * PanicKit responder reached through startActivityForResult, which is the only
 * way to learn which app sent the trigger. Molly's broadcast receiver stays for
 * triggers that only broadcast; it can lock but never wipe.
 */
class PanicResponderActivity : Activity() {

  companion object {
    private val TAG = Log.tag(PanicResponderActivity::class.java)

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
      Log.w(TAG, "Panic request failed", e)
    }

    finish()
  }

  private fun handleConnect() {
    val store = DuressStore(this)
    val caller = callingPackage
    when (PanicDecision.connect(store.panicTriggerPackage, caller, packageName)) {
      PanicConnectResult.CONNECT -> {
        store.connectPanicTrigger(requireNotNull(caller))
        Log.i(TAG, "Panic trigger connected")
        setResult(RESULT_OK)
      }
      PanicConnectResult.ALREADY_CONNECTED -> setResult(RESULT_OK)
      PanicConnectResult.REFUSE -> {
        Log.w(TAG, "Panic trigger connect refused")
        setResult(RESULT_CANCELED)
      }
    }
  }

  private fun handleDisconnect() {
    val store = DuressStore(this)
    if (PanicDecision.isConnectedCaller(store.panicTriggerPackage, callingPackage)) {
      store.disconnectPanicTrigger()
      Log.i(TAG, "Panic trigger disconnected")
    }
    setResult(RESULT_OK)
  }

  private fun handleTrigger() {
    val store = DuressStore(this)
    val response = PanicDecision.decide(
      action = store.panicAction,
      connectedPackage = store.panicTriggerPackage,
      callingPackage = callingPackage,
      passphraseLockEnabled = TextSecurePreferences.isPassphraseLockEnabled(this)
    )

    Log.i(TAG, "Panic trigger: $response")

    when (response) {
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
