// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuressCheckTest {

  private var realRuns = 0
  private var decoyRuns = 0

  private fun run(check: DuressCheck, realResult: Boolean): Boolean {
    return check.matches(
      real = {
        realRuns++
        realResult
      },
      decoy = { decoyRuns++ }
    )
  }

  @Test
  fun `lock on without duress runs the decoy`() {
    assertEquals(DuressCheck.DECOY, DuressCheck.plan(duressEnabled = false, passphraseLockEnabled = true))
  }

  @Test
  fun `duress set runs the real check`() {
    assertEquals(DuressCheck.REAL, DuressCheck.plan(duressEnabled = true, passphraseLockEnabled = true))
  }

  @Test
  fun `lock off runs nothing`() {
    assertEquals(DuressCheck.NONE, DuressCheck.plan(duressEnabled = false, passphraseLockEnabled = false))
  }

  @Test
  fun `every rejection with the lock on pays for exactly one derivation`() {
    for (duress in listOf(true, false)) {
      realRuns = 0
      decoyRuns = 0
      run(DuressCheck.plan(duress, passphraseLockEnabled = true), realResult = false)
      assertEquals("duress=$duress", 1, realRuns + decoyRuns)
    }
  }

  @Test
  fun `the decoy never matches`() {
    assertFalse(run(DuressCheck.DECOY, realResult = true))
    assertEquals(0, realRuns)
    assertEquals(1, decoyRuns)
  }

  @Test
  fun `the real check decides when duress is set`() {
    assertTrue(run(DuressCheck.REAL, realResult = true))
    assertFalse(run(DuressCheck.REAL, realResult = false))
    assertEquals(2, realRuns)
    assertEquals(0, decoyRuns)
  }

  @Test
  fun `none runs neither`() {
    assertFalse(run(DuressCheck.NONE, realResult = true))
    assertEquals(0, realRuns + decoyRuns)
  }
}
