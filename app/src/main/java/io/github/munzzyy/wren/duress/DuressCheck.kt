// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

/**
 * Which second derivation a rejected passphrase gets. With the lock on, every rejection pays for
 * one, real or decoy, so the time it takes does not show whether a duress passphrase is set.
 */
enum class DuressCheck {
  REAL,
  DECOY,
  NONE;

  /** The decoy's result is thrown away: it can never trigger a wipe. */
  fun matches(real: () -> Boolean, decoy: () -> Unit): Boolean {
    return when (this) {
      REAL -> real()
      DECOY -> {
        decoy()
        false
      }
      NONE -> false
    }
  }

  companion object {
    fun plan(duressEnabled: Boolean, passphraseLockEnabled: Boolean): DuressCheck {
      return when {
        duressEnabled -> REAL
        passphraseLockEnabled -> DECOY
        else -> NONE
      }
    }
  }
}
