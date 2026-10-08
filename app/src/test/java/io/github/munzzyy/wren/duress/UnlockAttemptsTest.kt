// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockAttemptsTest {

  private class FakeCounter(override val limit: Int, var stored: Int = 0) : UnlockAttempts.Counter {
    var failWrites = false
    var failReads = false
    var writes = 0

    override var count: Int
      get() {
        if (failReads) throw IllegalStateException("read failed")
        return stored
      }
      set(value) {
        if (failWrites) throw IllegalStateException("write failed")
        stored = value
        writes++
      }

    override fun <T> locked(block: () -> T): T = block()
  }

  private class Wipe : () -> Unit {
    var calls = 0
    override fun invoke() {
      calls++
    }
  }

  @Test
  fun `the attempt is counted before the check runs`() {
    val counter = FakeCounter(limit = 5)
    val attempts = UnlockAttempts(counter, Wipe())

    assertTrue(attempts.onStarting())
    assertEquals(1, counter.stored)
  }

  @Test
  fun `killing the app during the check does not take the guess back`() {
    val counter = FakeCounter(limit = 5)
    val wipe = Wipe()

    repeat(5) {
      assertTrue(UnlockAttempts(counter, wipe).onStarting())
    }
    assertEquals(5, counter.stored)
    assertEquals(0, wipe.calls)

    assertFalse(UnlockAttempts(counter, wipe).onStarting())
    assertEquals(1, wipe.calls)
  }

  @Test
  fun `the wrong guess that reaches the limit wipes at once`() {
    val counter = FakeCounter(limit = 5)
    val wipe = Wipe()
    val attempts = UnlockAttempts(counter, wipe)

    repeat(4) {
      assertTrue(attempts.onStarting())
      attempts.onRejected()
    }
    assertEquals(0, wipe.calls)

    assertTrue(attempts.onStarting())
    attempts.onRejected()
    assertEquals(1, wipe.calls)
  }

  @Test
  fun `a count that cannot be saved refuses the attempt`() {
    val counter = FakeCounter(limit = 5).apply { failWrites = true }
    val wipe = Wipe()

    assertFalse(UnlockAttempts(counter, wipe).onStarting())
    assertEquals(0, counter.stored)
    assertEquals(0, wipe.calls)
  }

  @Test
  fun `a count that cannot be read refuses the attempt`() {
    val counter = FakeCounter(limit = 5).apply { failReads = true }

    assertFalse(UnlockAttempts(counter, Wipe()).onStarting())
  }

  @Test
  fun `the count is saved even with the limit off`() {
    val counter = FakeCounter(limit = FailedAttemptPolicy.OFF, stored = 41)
    val wipe = Wipe()

    assertTrue(UnlockAttempts(counter, wipe).onStarting())
    UnlockAttempts(counter, wipe).onRejected()
    assertEquals(42, counter.stored)
    assertEquals(0, wipe.calls)
  }

  @Test
  fun `success resets the count`() {
    val counter = FakeCounter(limit = 5, stored = 3)
    val attempts = UnlockAttempts(counter, Wipe())

    assertTrue(attempts.onStarting())
    assertTrue(attempts.onSucceeded())
    assertEquals(0, counter.stored)
  }

  @Test
  fun `success without earlier failures writes nothing more`() {
    val counter = FakeCounter(limit = 5)
    val attempts = UnlockAttempts(counter, Wipe())

    assertTrue(attempts.onStarting())
    assertTrue(attempts.onSucceeded())
    assertEquals(2, counter.writes)
    assertTrue(attempts.onSucceeded())
    assertEquals(2, counter.writes)
  }

  @Test
  fun `a reset that cannot be saved is reported`() {
    val counter = FakeCounter(limit = 5, stored = 2).apply { failWrites = true }

    assertFalse(UnlockAttempts(counter, Wipe()).onSucceeded())
    assertEquals(2, counter.stored)
  }

  @Test
  fun `an unreadable count after a rejection is caught at the next start`() {
    val counter = FakeCounter(limit = 5, stored = 4)
    val wipe = Wipe()
    val attempts = UnlockAttempts(counter, wipe)

    assertTrue(attempts.onStarting())
    counter.failReads = true
    attempts.onRejected()
    assertEquals(0, wipe.calls)

    counter.failReads = false
    assertFalse(attempts.onStarting())
    assertEquals(1, wipe.calls)
  }
}
