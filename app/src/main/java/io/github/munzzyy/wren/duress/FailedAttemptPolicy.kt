// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

object FailedAttemptPolicy {

  const val OFF = 0

  val ALLOWED_LIMITS: List<Int> = listOf(OFF, 5, 10, 20)

  data class Outcome(val count: Int, val wipe: Boolean)

  fun sanitizeLimit(limit: Int): Int = if (limit in ALLOWED_LIMITS) limit else OFF

  fun onFailedAttempt(previousCount: Int, limit: Int): Outcome {
    val count = previousCount.coerceAtLeast(0) + 1
    val effectiveLimit = sanitizeLimit(limit)
    return Outcome(count = count, wipe = effectiveLimit != OFF && count >= effectiveLimit)
  }

  fun onSuccessfulAttempt(): Int = 0
}
