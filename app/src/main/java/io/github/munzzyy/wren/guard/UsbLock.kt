// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.munzzyy.wren.duress.DuressStore
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences

/** The receiver is registered only while the option is on, and only in a running process. */
object UsbLock {

  private val TAG = Log.tag(UsbLock::class.java)

  private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
  private const val EXTRA_CONNECTED = "connected"

  private var receiver: Receiver? = null

  @JvmStatic
  @Synchronized
  fun sync(context: Context) {
    val app = context.applicationContext
    val enabled = try {
      DuressStore(app).usbLockEnabled
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not read USB lock setting", e)
      false
    }

    val current = receiver
    if (enabled && current == null) {
      val newReceiver = Receiver()
      ContextCompat.registerReceiver(app, newReceiver, IntentFilter(ACTION_USB_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
      receiver = newReceiver
    } else if (!enabled && current != null) {
      runCatching { app.unregisterReceiver(current) }
      receiver = null
    }
  }

  private fun lock(context: Context) {
    if (!TextSecurePreferences.isPassphraseLockEnabled(context) || KeyCachingService.isLocked()) {
      return
    }
    runCatching {
      val lockIntent = Intent(context, KeyCachingService::class.java)
      lockIntent.action = KeyCachingService.CLEAR_KEY_ACTION
      context.startService(lockIntent)
    }
  }

  private class Receiver : BroadcastReceiver() {
    private var wasDataConnection = false

    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != ACTION_USB_STATE) {
        return
      }

      val active = UsbLockPolicy.DATA_FUNCTIONS.filter { intent.getBooleanExtra(it, false) }.toSet()
      val isData = UsbLockPolicy.isDataConnection(intent.getBooleanExtra(EXTRA_CONNECTED, false), active)

      if (UsbLockPolicy.shouldLock(wasDataConnection, isData, isInitialStickyBroadcast)) {
        lock(context)
      }
      wasDataConnection = isData
    }
  }
}
