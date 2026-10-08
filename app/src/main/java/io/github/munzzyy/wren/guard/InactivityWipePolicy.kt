// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import java.util.concurrent.TimeUnit

object InactivityWipePolicy {

  const val OFF = 0

  val ALLOWED_DAYS: List<Int> = listOf(OFF, 3, 7, 14, 30)

  sealed interface Decision {
    data object Off : Decision
    data object Wipe : Decision
    data class Wait(val checkAt: Long) : Decision
    data class Restart(val baseline: Long, val checkAt: Long) : Decision
  }

  fun sanitizeDays(days: Int): Int = if (days in ALLOWED_DAYS) days else OFF

  fun limitMillis(days: Int): Long = TimeUnit.DAYS.toMillis(sanitizeDays(days).toLong())

  /**
   * Anything that leaves the countdown in doubt restarts it from now instead of
   * wiping: no lock to unlock, no stored baseline, or a clock that went backwards.
   */
  fun check(nowMillis: Long, lastUnlockMillis: Long, days: Int, passphraseLockEnabled: Boolean): Decision {
    val limit = limitMillis(days)
    if (limit == 0L) {
      return Decision.Off
    }

    if (!passphraseLockEnabled || lastUnlockMillis <= 0L || nowMillis < lastUnlockMillis) {
      return Decision.Restart(baseline = nowMillis, checkAt = nowMillis + limit)
    }

    return if (nowMillis - lastUnlockMillis >= limit) {
      Decision.Wipe
    } else {
      Decision.Wait(checkAt = lastUnlockMillis + limit)
    }
  }
}
