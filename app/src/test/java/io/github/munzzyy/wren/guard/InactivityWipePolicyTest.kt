// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import io.github.munzzyy.wren.guard.InactivityWipePolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class InactivityWipePolicyTest {

  private val day = TimeUnit.DAYS.toMillis(1)
  private val enabledAt = 1_790_000_000_000L

  private fun check(now: Long, lastUnlock: Long = enabledAt, days: Int = 7, lock: Boolean = true): Decision {
    return InactivityWipePolicy.check(now, lastUnlock, days, lock)
  }

  @Test
  fun `off never wipes`() {
    assertEquals(Decision.Off, check(now = enabledAt + 1000 * day, days = InactivityWipePolicy.OFF))
  }

  @Test
  fun `never wipes within the first N days after enabling`() {
    for (days in InactivityWipePolicy.ALLOWED_DAYS - InactivityWipePolicy.OFF) {
      val limit = days * day
      for (now in listOf(enabledAt, enabledAt + 1, enabledAt + limit / 2, enabledAt + limit - 1)) {
        assertEquals("days $days at +${now - enabledAt}", Decision.Wait(enabledAt + limit), check(now = now, days = days))
      }
    }
  }

  @Test
  fun `wipes once the limit has passed`() {
    for (days in InactivityWipePolicy.ALLOWED_DAYS - InactivityWipePolicy.OFF) {
      assertEquals("days $days", Decision.Wipe, check(now = enabledAt + days * day, days = days))
      assertEquals("days $days", Decision.Wipe, check(now = enabledAt + days * day + 1, days = days))
    }
  }

  @Test
  fun `an unlock pushes the deadline out`() {
    val unlockedAt = enabledAt + 6 * day
    assertEquals(Decision.Wait(unlockedAt + 7 * day), check(now = enabledAt + 8 * day, lastUnlock = unlockedAt))
  }

  @Test
  fun `a clock moved backwards never wipes and restarts the countdown`() {
    val now = enabledAt - 365 * day
    assertEquals(Decision.Restart(baseline = now, checkAt = now + 7 * day), check(now = now))
    assertEquals(Decision.Restart(baseline = enabledAt - 1, checkAt = enabledAt - 1 + 7 * day), check(now = enabledAt - 1))
  }

  @Test
  fun `a clock moved forward past the limit wipes`() {
    assertEquals(Decision.Wipe, check(now = enabledAt + 400 * day))
  }

  @Test
  fun `backwards then forward counts from the restarted baseline`() {
    val skewed = enabledAt - 30 * day
    val restart = check(now = skewed) as Decision.Restart
    assertEquals(Decision.Wait(skewed + 7 * day), check(now = skewed + 6 * day, lastUnlock = restart.baseline))
    assertEquals(Decision.Wipe, check(now = skewed + 7 * day, lastUnlock = restart.baseline))
  }

  @Test
  fun `a missing baseline restarts instead of wiping`() {
    assertEquals(Decision.Restart(enabledAt, enabledAt + 7 * day), check(now = enabledAt, lastUnlock = 0L))
    assertEquals(Decision.Restart(enabledAt, enabledAt + 7 * day), check(now = enabledAt, lastUnlock = -5L))
  }

  @Test
  fun `without the passphrase lock it never wipes`() {
    val decision = check(now = enabledAt + 100 * day, lock = false)
    assertTrue(decision is Decision.Restart)
    assertNotEquals(Decision.Wipe, decision)
  }

  @Test
  fun `unsupported day counts are treated as off`() {
    assertEquals(InactivityWipePolicy.OFF, InactivityWipePolicy.sanitizeDays(1))
    assertEquals(InactivityWipePolicy.OFF, InactivityWipePolicy.sanitizeDays(-7))
    assertEquals(Decision.Off, check(now = enabledAt + 100 * day, days = 1))
    assertEquals(14, InactivityWipePolicy.sanitizeDays(14))
  }

  @Test
  fun `a far future baseline does not overflow into a wipe`() {
    assertEquals(Decision.Restart(enabledAt, enabledAt + 7 * day), check(now = enabledAt, lastUnlock = Long.MAX_VALUE))
  }
}
