// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanicDecisionTest {

  private val ripple = "info.guardianproject.ripple"
  private val other = "com.example.other"
  private val own = "io.github.munzzyy.wren"

  @Test
  fun `wipe needs the connected trigger as caller`() {
    assertEquals(PanicResponse.WIPE, PanicDecision.decide(PanicAction.WIPE, ripple, ripple, passphraseLockEnabled = true))
    assertEquals(PanicResponse.WIPE, PanicDecision.decide(PanicAction.WIPE, ripple, ripple, passphraseLockEnabled = false))
  }

  @Test
  fun `wipe from an unverified caller only locks`() {
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.WIPE, ripple, other, passphraseLockEnabled = true))
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.WIPE, ripple, null, passphraseLockEnabled = true))
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.WIPE, null, ripple, passphraseLockEnabled = true))
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.WIPE, null, null, passphraseLockEnabled = true))
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.WIPE, "", "", passphraseLockEnabled = true))
  }

  @Test
  fun `unverified caller without passphrase lock does nothing`() {
    assertEquals(PanicResponse.NOTHING, PanicDecision.decide(PanicAction.WIPE, ripple, other, passphraseLockEnabled = false))
    assertEquals(PanicResponse.NOTHING, PanicDecision.decide(PanicAction.WIPE, null, null, passphraseLockEnabled = false))
  }

  @Test
  fun `lock action never wipes`() {
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.LOCK, ripple, ripple, passphraseLockEnabled = true))
    assertEquals(PanicResponse.LOCK, PanicDecision.decide(PanicAction.LOCK, ripple, other, passphraseLockEnabled = true))
    assertEquals(PanicResponse.NOTHING, PanicDecision.decide(PanicAction.LOCK, ripple, ripple, passphraseLockEnabled = false))
  }

  @Test
  fun `first connect waits for the user`() {
    assertEquals(PanicConnectResult.ASK_USER, PanicDecision.connect(null, ripple, own))
    assertEquals(PanicConnectResult.ASK_USER, PanicDecision.connect("", ripple, own))
  }

  @Test
  fun `allowing a pending connect stores it`() {
    assertEquals(PanicConfirmResult.CONNECT, PanicDecision.confirm(null, ripple, own, allowed = true))
    assertEquals(PanicConfirmResult.CONNECT, PanicDecision.confirm("", ripple, own, allowed = true))
  }

  @Test
  fun `denying a pending connect refuses it`() {
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, ripple, own, allowed = false))
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(ripple, ripple, own, allowed = false))
  }

  @Test
  fun `a trigger connected while the prompt was open is not replaced`() {
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(other, ripple, own, allowed = true))
    assertEquals(PanicConfirmResult.ALREADY_CONNECTED, PanicDecision.confirm(ripple, ripple, own, allowed = true))
  }

  @Test
  fun `allowing a pending connect without a real app is refused`() {
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, null, own, allowed = true))
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, "", own, allowed = true))
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, own, own, allowed = true))
  }

  @Test
  fun `reconnect from the same app is a no-op`() {
    assertEquals(PanicConnectResult.ALREADY_CONNECTED, PanicDecision.connect(ripple, ripple, own))
  }

  @Test
  fun `another app cannot replace the connected trigger`() {
    assertEquals(PanicConnectResult.REFUSE, PanicDecision.connect(ripple, other, own))
  }

  @Test
  fun `connect without a known caller or from ourselves is refused`() {
    assertEquals(PanicConnectResult.REFUSE, PanicDecision.connect(null, null, own))
    assertEquals(PanicConnectResult.REFUSE, PanicDecision.connect(null, "", own))
    assertEquals(PanicConnectResult.REFUSE, PanicDecision.connect(null, own, own))
  }

  @Test
  fun `only the connected app can disconnect`() {
    assertTrue(PanicDecision.isConnectedCaller(ripple, ripple))
    assertFalse(PanicDecision.isConnectedCaller(ripple, other))
    assertFalse(PanicDecision.isConnectedCaller(ripple, null))
    assertFalse(PanicDecision.isConnectedCaller(null, null))
  }

  @Test
  fun `unknown stored action falls back to lock`() {
    assertEquals(PanicAction.LOCK, PanicAction.fromKey(null))
    assertEquals(PanicAction.LOCK, PanicAction.fromKey("explode"))
    assertEquals(PanicAction.WIPE, PanicAction.fromKey("wipe"))
  }
}
