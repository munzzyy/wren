// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

/**
 * Keeps the failed unlock count around a passphrase check: [onStarting] before the KDF runs,
 * then [onRejected] or [onSucceeded]. [wipe] is not expected to return.
 */
class UnlockAttempts(private val counter: Counter, private val wipe: () -> Unit) {

  /** Reads and writes throw when the stored count can't be read or saved. */
  interface Counter {
    val limit: Int
    var count: Int
    fun <T> locked(block: () -> T): T
  }

  /** False means the attempt must not run: the count could not be saved, or the limit was hit. */
  fun onStarting(): Boolean {
    val outcome = try {
      counter.locked {
        val outcome = FailedAttemptPolicy.onAttemptStarting(counter.count, counter.limit)
        if (!outcome.wipe) {
          counter.count = outcome.count
        }
        outcome
      }
    } catch (e: RuntimeException) {
      return false
    }

    if (outcome.wipe) {
      wipe()
      return false
    }
    return true
  }

  /** If the count can't be read here, the next [onStarting] still sees it. */
  fun onRejected() {
    val wipeNow = try {
      FailedAttemptPolicy.onAttemptRejected(counter.count, counter.limit)
    } catch (e: RuntimeException) {
      false
    }
    if (wipeNow) {
      wipe()
    }
  }

  /** Returns false when the reset could not be saved. */
  fun onSucceeded(): Boolean {
    return try {
      counter.locked {
        if (counter.count != 0) {
          counter.count = FailedAttemptPolicy.onSuccessfulAttempt()
        }
      }
      true
    } catch (e: RuntimeException) {
      false
    }
  }
}
