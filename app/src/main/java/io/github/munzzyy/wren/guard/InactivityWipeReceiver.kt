// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.signal.core.util.concurrent.SignalExecutors

/** Alarm, boot, app update and clock changes all land here; a clock change is checked right away. */
class InactivityWipeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    SignalExecutors.BOUNDED.execute {
      try {
        InactivityWipe.check(context)
      } finally {
        pending.finish()
      }
    }
  }
}
