// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import java.time.LocalDate
import kotlin.time.Duration.Companion.days

object PatchBannerPolicy {

  val SNOOZE_MILLIS: Long = 30.days.inWholeMilliseconds

  fun shouldShow(patch: LocalDate?, today: LocalDate, dismissedAtMillis: Long, nowMillis: Long): Boolean {
    if (PatchLevel.status(patch, today) != CheckStatus.ALERT) {
      return false
    }

    // A dismissal stamped in the future means the clock moved; it must not hide the banner for good.
    val snoozed = dismissedAtMillis > 0 && nowMillis >= dismissedAtMillis && nowMillis - dismissedAtMillis < SNOOZE_MILLIS
    return !snoozed
  }
}
