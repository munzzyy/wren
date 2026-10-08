// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedAttemptPolicyTest {

  private fun failRepeatedly(times: Int, limit: Int): List<FailedAttemptPolicy.Outcome> {
    var count = 0
    return (1..times).map {
      FailedAttemptPolicy.onFailedAttempt(count, limit).also { count = it.count }
    }
  }

  @Test
  fun `limit off never wipes`() {
    val outcomes = failRepeatedly(times = 1000, limit = FailedAttemptPolicy.OFF)

    assertTrue(outcomes.none { it.wipe })
    assertEquals(1000, outcomes.last().count)
  }

  @Test
  fun `wipes exactly at the limit`() {
    for (limit in listOf(5, 10, 20)) {
      val outcomes = failRepeatedly(times = limit, limit = limit)

      assertTrue("limit $limit", outcomes.dropLast(1).none { it.wipe })
      assertTrue("limit $limit", outcomes.last().wipe)
      assertEquals(limit, outcomes.last().count)
    }
  }

  @Test
  fun `success resets the count`() {
    val beforeSuccess = failRepeatedly(times = 4, limit = 5)
    assertFalse(beforeSuccess.last().wipe)

    var count = FailedAttemptPolicy.onSuccessfulAttempt()
    assertEquals(0, count)

    repeat(4) {
      val outcome = FailedAttemptPolicy.onFailedAttempt(count, 5)
      assertFalse(outcome.wipe)
      count = outcome.count
    }
    assertTrue(FailedAttemptPolicy.onFailedAttempt(count, 5).wipe)
  }

  @Test
  fun `count already past the limit still wipes`() {
    assertTrue(FailedAttemptPolicy.onFailedAttempt(previousCount = 30, limit = 10).wipe)
  }

  @Test
  fun `unsupported limits are treated as off`() {
    assertEquals(FailedAttemptPolicy.OFF, FailedAttemptPolicy.sanitizeLimit(1))
    assertEquals(FailedAttemptPolicy.OFF, FailedAttemptPolicy.sanitizeLimit(-5))
    assertFalse(FailedAttemptPolicy.onFailedAttempt(previousCount = 0, limit = 1).wipe)
    assertEquals(10, FailedAttemptPolicy.sanitizeLimit(10))
  }

  @Test
  fun `negative stored count is treated as zero`() {
    assertEquals(1, FailedAttemptPolicy.onFailedAttempt(previousCount = -7, limit = 5).count)
  }
}
