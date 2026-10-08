// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.WorkerThread
import io.github.munzzyy.wren.duress.AppWipe
import io.github.munzzyy.wren.duress.DuressStore
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Erases Wren when the passphrase has not been entered for the chosen number of
 * days. Runs from a plain alarm because the job system only starts after unlock.
 */
object InactivityWipe {

  private val TAG = Log.tag(InactivityWipe::class.java)

  @JvmStatic
  fun checkInBackground(context: Context) {
    val app = context.applicationContext
    SignalExecutors.BOUNDED.execute { check(app) }
  }

  @WorkerThread
  fun check(context: Context) {
    val app = context.applicationContext
    try {
      val store = DuressStore(app)
      val decision = InactivityWipePolicy.check(
        nowMillis = System.currentTimeMillis(),
        lastUnlockMillis = store.lastUnlockAt,
        days = store.inactivityWipeDays,
        passphraseLockEnabled = TextSecurePreferences.isPassphraseLockEnabled(app)
      )

      when (decision) {
        InactivityWipePolicy.Decision.Off -> cancel(app)
        InactivityWipePolicy.Decision.Wipe -> {
          Log.w(TAG, "No unlock within the limit")
          AppWipe.wipeNow(app)
        }
        is InactivityWipePolicy.Decision.Wait -> schedule(app, decision.checkAt)
        is InactivityWipePolicy.Decision.Restart -> {
          store.lastUnlockAt = decision.baseline
          schedule(app, decision.checkAt)
        }
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Inactivity check failed", e)
    }
  }

  private fun schedule(context: Context, checkAt: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val pendingIntent = pendingIntent(context)
    alarmManager.cancel(pendingIntent)

    val exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
    try {
      if (exactAllowed) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkAt, pendingIntent)
        return
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "Exact alarm refused", e)
    }
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkAt, pendingIntent)
  }

  private fun cancel(context: Context) {
    context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
  }

  private fun pendingIntent(context: Context): PendingIntent {
    return PendingIntent.getBroadcast(context, 0, Intent(context, InactivityWipeReceiver::class.java), PendingIntentFlags.immutable())
  }
}
