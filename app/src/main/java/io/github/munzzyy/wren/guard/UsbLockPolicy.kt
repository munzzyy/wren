// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

object UsbLockPolicy {

  /** USB functions that move data. Charging, MIDI, audio and webcam modes are left out. */
  val DATA_FUNCTIONS: List<String> = listOf("mtp", "ptp", "adb", "rndis", "ncm", "accessory")

  fun isDataConnection(connected: Boolean, activeFunctions: Set<String>): Boolean {
    return connected && DATA_FUNCTIONS.any { it in activeFunctions }
  }

  /**
   * Locks only when a data connection starts. A connection that already existed
   * when the option was switched on, or when Wren started, does not count.
   */
  fun shouldLock(wasDataConnection: Boolean, isDataConnection: Boolean, isInitialState: Boolean): Boolean {
    return isDataConnection && !wasDataConnection && !isInitialState
  }
}
