// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import android.content.Context
import androidx.annotation.WorkerThread
import info.guardianproject.netcipher.proxy.OrbotHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.net.ProxyType
import org.thoughtcrime.securesms.util.TextSecurePreferences

/** Same steps as Molly's Network settings when Tor via Orbot is picked. It only ever selects Orbot. */
object OrbotRouting {

  private val TAG = Log.tag(OrbotRouting::class.java)

  fun isInstalled(context: Context): Boolean = OrbotHelper.isOrbotInstalled(context)

  fun isSelected(context: Context): Boolean = TextSecurePreferences.getProxyType(context) == ProxyType.ORBOT

  @WorkerThread
  fun select(context: Context): Boolean {
    val networkManager = AppDependencies.networkManager
    if (!isInstalled(context) || !networkManager.isOrbotAvailable) {
      Log.w(TAG, "Orbot is not available")
      return false
    }

    TextSecurePreferences.setStringPreference(context, TextSecurePreferences.PROXY_TYPE, ProxyType.ORBOT.code)
    networkManager.setProxyChoice(ProxyType.ORBOT)
    if (networkManager.applyProxyConfig()) {
      networkManager.setNetworkEnabled(true)
      AppDependencies.resetNetwork(true)
    }
    return true
  }
}
