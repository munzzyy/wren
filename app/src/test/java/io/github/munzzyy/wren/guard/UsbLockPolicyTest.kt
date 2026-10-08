// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbLockPolicyTest {

  @Test
  fun `each data function counts as a data connection`() {
    for (function in listOf("mtp", "ptp", "adb", "rndis", "ncm", "accessory")) {
      assertTrue(function, UsbLockPolicy.isDataConnection(connected = true, activeFunctions = setOf(function)))
    }
  }

  @Test
  fun `charging alone is not a data connection`() {
    assertFalse(UsbLockPolicy.isDataConnection(connected = true, activeFunctions = emptySet()))
    assertFalse(UsbLockPolicy.isDataConnection(connected = true, activeFunctions = setOf("none", "charging")))
    assertFalse(UsbLockPolicy.isDataConnection(connected = true, activeFunctions = setOf("midi", "audio_source", "uvc")))
  }

  @Test
  fun `functions without a connection do not count`() {
    assertFalse(UsbLockPolicy.isDataConnection(connected = false, activeFunctions = setOf("mtp", "adb")))
  }

  @Test
  fun `locks when a data connection starts`() {
    assertTrue(UsbLockPolicy.shouldLock(wasDataConnection = false, isDataConnection = true, isInitialState = false))
  }

  @Test
  fun `does not lock again while the same connection stays up`() {
    assertFalse(UsbLockPolicy.shouldLock(wasDataConnection = true, isDataConnection = true, isInitialState = false))
  }

  @Test
  fun `unplugging never locks`() {
    assertFalse(UsbLockPolicy.shouldLock(wasDataConnection = true, isDataConnection = false, isInitialState = false))
    assertFalse(UsbLockPolicy.shouldLock(wasDataConnection = false, isDataConnection = false, isInitialState = false))
  }

  @Test
  fun `a connection that existed at registration does not lock`() {
    assertFalse(UsbLockPolicy.shouldLock(wasDataConnection = false, isDataConnection = true, isInitialState = true))
  }
}
