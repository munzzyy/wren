// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.days

class PatchBannerPolicyTest {

  private val today = LocalDate.of(2026, 10, 8)
  private val now = 1_791_000_000_000L
  private val stale = LocalDate.of(2026, 1, 5)
  private val day = 1.days.inWholeMilliseconds

  @Test
  fun `shows for a patch older than six months that was never dismissed`() {
    assertTrue(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = 0, nowMillis = now))
  }

  @Test
  fun `stays hidden for a recent, slightly old or unknown patch`() {
    assertFalse(PatchBannerPolicy.shouldShow(LocalDate.of(2026, 9, 5), today, 0, now))
    assertFalse(PatchBannerPolicy.shouldShow(LocalDate.of(2026, 4, 8), today, 0, now))
    assertFalse(PatchBannerPolicy.shouldShow(null, today, 0, now))
  }

  @Test
  fun `a dismissal hides it for thirty days and no longer`() {
    assertFalse(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = now, nowMillis = now))
    assertFalse(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = now - 29 * day, nowMillis = now))
    assertFalse(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = now - 30 * day + 1, nowMillis = now))
    assertTrue(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = now - 30 * day, nowMillis = now))
    assertTrue(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = now - 400 * day, nowMillis = now))
  }

  @Test
  fun `a dismissal from the future does not hide it`() {
    assertTrue(PatchBannerPolicy.shouldShow(stale, today, dismissedAtMillis = now + day, nowMillis = now))
  }
}
