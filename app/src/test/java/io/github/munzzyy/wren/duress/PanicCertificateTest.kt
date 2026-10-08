// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanicCertificateTest {

  private val ripple = "info.guardianproject.ripple"
  private val other = "com.example.other"
  private val own = "io.github.munzzyy.wren"
  private val rippleKey = SigningCertificates.digestOf(listOf("ripple release key".toByteArray()))!!
  private val otherKey = SigningCertificates.digestOf(listOf("someone else's key".toByteArray()))!!

  @Test
  fun `the connected trigger with its own key can wipe`() {
    val outcome = PanicDecision.onTrigger(PanicAction.WIPE, ripple, rippleKey, ripple, rippleKey, passphraseLockEnabled = true)
    assertEquals(TriggerOutcome(PanicResponse.WIPE, disconnect = false), outcome)
  }

  @Test
  fun `a reinstall under the same name with another key cannot wipe and is disconnected`() {
    val outcome = PanicDecision.onTrigger(PanicAction.WIPE, ripple, rippleKey, ripple, otherKey, passphraseLockEnabled = true)
    assertEquals(TriggerOutcome(PanicResponse.LOCK, disconnect = true), outcome)

    val withoutLock = PanicDecision.onTrigger(PanicAction.WIPE, ripple, rippleKey, ripple, otherKey, passphraseLockEnabled = false)
    assertEquals(TriggerOutcome(PanicResponse.NOTHING, disconnect = true), withoutLock)
  }

  @Test
  fun `a caller whose key cannot be read is a mismatch`() {
    assertEquals(CallerCheck.MISMATCH, PanicDecision.checkCaller(ripple, rippleKey, ripple, null))
    assertTrue(PanicDecision.onTrigger(PanicAction.WIPE, ripple, rippleKey, ripple, null, passphraseLockEnabled = true).disconnect)
  }

  @Test
  fun `a connection stored without a key never wipes`() {
    assertEquals(CallerCheck.MISMATCH, PanicDecision.checkCaller(ripple, null, ripple, rippleKey))
    assertEquals(CallerCheck.MISMATCH, PanicDecision.checkCaller(ripple, "", ripple, ""))
    assertEquals(PanicResponse.LOCK, PanicDecision.onTrigger(PanicAction.WIPE, ripple, null, ripple, rippleKey, passphraseLockEnabled = true).response)
  }

  @Test
  fun `another package is neither connected nor disconnected`() {
    assertEquals(CallerCheck.OTHER, PanicDecision.checkCaller(ripple, rippleKey, other, rippleKey))
    assertEquals(TriggerOutcome(PanicResponse.LOCK, disconnect = false), PanicDecision.onTrigger(PanicAction.WIPE, ripple, rippleKey, other, rippleKey, passphraseLockEnabled = true))
    assertFalse(PanicDecision.shouldDisconnect(ripple, rippleKey, other, rippleKey))
  }

  @Test
  fun `disconnect from the trigger package goes through with either key`() {
    assertTrue(PanicDecision.shouldDisconnect(ripple, rippleKey, ripple, rippleKey))
    assertTrue(PanicDecision.shouldDisconnect(ripple, rippleKey, ripple, otherKey))
    assertFalse(PanicDecision.shouldDisconnect(null, null, ripple, rippleKey))
  }

  @Test
  fun `connect with another key drops the trigger and is refused`() {
    assertEquals(ConnectOutcome(PanicConnectResult.REFUSE, disconnect = true), PanicDecision.onConnect(ripple, rippleKey, ripple, otherKey, own))
    assertEquals(ConnectOutcome(PanicConnectResult.ALREADY_CONNECTED, disconnect = false), PanicDecision.onConnect(ripple, rippleKey, ripple, rippleKey, own))
    assertEquals(ConnectOutcome(PanicConnectResult.ASK_USER, disconnect = false), PanicDecision.onConnect(null, null, ripple, rippleKey, own))
    assertEquals(ConnectOutcome(PanicConnectResult.REFUSE, disconnect = false), PanicDecision.onConnect(ripple, rippleKey, other, otherKey, own))
  }

  @Test
  fun `confirm refuses when the key changed while the prompt was open`() {
    assertEquals(PanicConfirmResult.CONNECT, PanicDecision.confirm(null, ripple, rippleKey, rippleKey, own, allowed = true))
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, ripple, rippleKey, otherKey, own, allowed = true))
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, ripple, rippleKey, null, own, allowed = true))
    assertEquals(PanicConfirmResult.REFUSE, PanicDecision.confirm(null, ripple, null, null, own, allowed = true))
  }

  @Test
  fun `digest is sha256 hex and ignores signer order`() {
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      SigningCertificates.digestOf(listOf("abc".toByteArray()))
    )
    val a = "first".toByteArray()
    val b = "second".toByteArray()
    assertEquals(SigningCertificates.digestOf(listOf(a, b)), SigningCertificates.digestOf(listOf(b, a)))
    assertNull(SigningCertificates.digestOf(emptyList()))
  }

  @Test
  fun `format prints colon pairs one signer per line`() {
    assertEquals("BA:78:16", SigningCertificates.format("ba7816"))
    assertEquals("AB:CD\n01:EF", SigningCertificates.format("abcd,01ef"))
  }
}
