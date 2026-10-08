// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

/**
 * Every attempt is counted before the passphrase is checked, so an attempt cut short by killing
 * the app still counts. A count that already reached the limit wipes at the next attempt.
 */
object FailedAttemptPolicy {

  const val OFF = 0

  val ALLOWED_LIMITS: List<Int> = listOf(OFF, 5, 10, 20)

  data class Outcome(val count: Int, val wipe: Boolean)

  fun sanitizeLimit(limit: Int): Int = if (limit in ALLOWED_LIMITS) limit else OFF

  /** [Outcome.count] is what to store before the check runs. */
  fun onAttemptStarting(previousCount: Int, limit: Int): Outcome {
    val previous = previousCount.coerceAtLeast(0)
    return Outcome(count = previous + 1, wipe = reached(previous, limit))
  }

  /** [count] already includes the rejected attempt. */
  fun onAttemptRejected(count: Int, limit: Int): Boolean = reached(count.coerceAtLeast(0), limit)

  fun onSuccessfulAttempt(): Int = 0

  private fun reached(count: Int, limit: Int): Boolean {
    val effectiveLimit = sanitizeLimit(limit)
    return effectiveLimit != OFF && count >= effectiveLimit
  }
}
