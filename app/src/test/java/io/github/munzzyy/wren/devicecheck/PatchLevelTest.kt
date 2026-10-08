// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PatchLevelTest {

  private val today = LocalDate.of(2026, 10, 8)

  @Test
  fun `parses the format Android reports`() {
    assertEquals(LocalDate.of(2026, 9, 5), PatchLevel.parse("2026-09-05"))
    assertEquals(LocalDate.of(2026, 9, 5), PatchLevel.parse(" 2026-09-05\n"))
  }

  @Test
  fun `rejects anything that is not a real yyyy-MM-dd date`() {
    for (raw in listOf(null, "", "   ", "2026-9-5", "2026/09/05", "05-09-2026", "2026-13-01", "2026-02-30", "2026-09-05T00:00", "unknown")) {
      assertNull("'$raw'", PatchLevel.parse(raw))
    }
  }

  @Test
  fun `months behind counts whole months and never goes negative`() {
    assertEquals(0, PatchLevel.monthsBehind(LocalDate.of(2026, 10, 1), today))
    assertEquals(0, PatchLevel.monthsBehind(LocalDate.of(2026, 9, 9), today))
    assertEquals(1, PatchLevel.monthsBehind(LocalDate.of(2026, 9, 8), today))
    assertEquals(6, PatchLevel.monthsBehind(LocalDate.of(2026, 4, 1), today))
    assertEquals(25, PatchLevel.monthsBehind(LocalDate.of(2024, 9, 5), today))
    assertEquals(0, PatchLevel.monthsBehind(LocalDate.of(2026, 12, 1), today))
  }

  @Test
  fun `status is good up to three months, warning past three, alert past six`() {
    assertEquals(CheckStatus.GOOD, PatchLevel.status(today, today))
    assertEquals(CheckStatus.GOOD, PatchLevel.status(LocalDate.of(2026, 7, 8), today))
    assertEquals(CheckStatus.WARNING, PatchLevel.status(LocalDate.of(2026, 7, 7), today))
    assertEquals(CheckStatus.WARNING, PatchLevel.status(LocalDate.of(2026, 4, 8), today))
    assertEquals(CheckStatus.ALERT, PatchLevel.status(LocalDate.of(2026, 4, 7), today))
    assertEquals(CheckStatus.ALERT, PatchLevel.status(LocalDate.of(2019, 1, 1), today))
  }

  @Test
  fun `a future patch date is good and a missing one is a warning`() {
    assertEquals(CheckStatus.GOOD, PatchLevel.status(LocalDate.of(2027, 1, 1), today))
    assertEquals(CheckStatus.WARNING, PatchLevel.status(null, today))
  }

  @Test
  fun `month arithmetic clamps at month ends`() {
    val endOfMonth = LocalDate.of(2026, 8, 31)
    assertEquals(CheckStatus.GOOD, PatchLevel.status(LocalDate.of(2026, 5, 31), endOfMonth))
    assertEquals(CheckStatus.WARNING, PatchLevel.status(LocalDate.of(2026, 5, 30), endOfMonth))
    assertEquals(CheckStatus.ALERT, PatchLevel.status(LocalDate.of(2026, 2, 27), endOfMonth))
  }
}
