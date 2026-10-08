// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class DuressVerifierTest {

  private val derivedKeys = mutableListOf<ByteArray>()
  private var kdfCalls = 0

  private val fakeKdf: (CharArray, ByteArray) -> ByteArray = { passphrase, salt ->
    kdfCalls++
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(String(passphrase).toByteArray(Charsets.UTF_8))
    digest.update(salt)
    digest.digest().also { derivedKeys += it }
  }

  private val salt = ByteArray(DuressVerifier.SALT_LENGTH) { it.toByte() }
  private val otherSalt = ByteArray(DuressVerifier.SALT_LENGTH) { (it + 100).toByte() }

  @Test
  fun `same passphrase and salt matches`() {
    val verifier = DuressVerifier(fakeKdf)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)

    assertEquals(DuressVerifier.VERIFIER_LENGTH, stored.size)
    assertTrue(verifier.matches("let me go".toCharArray(), salt, stored))
  }

  @Test
  fun `different passphrase does not match`() {
    val verifier = DuressVerifier(fakeKdf)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)

    assertFalse(verifier.matches("let me in".toCharArray(), salt, stored))
    assertFalse(verifier.matches("let me go ".toCharArray(), salt, stored))
  }

  @Test
  fun `empty passphrase never matches and skips the kdf`() {
    val verifier = DuressVerifier(fakeKdf)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)
    kdfCalls = 0

    assertFalse(verifier.matches(CharArray(0), salt, stored))
    assertEquals(0, kdfCalls)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `empty passphrase cannot be stored`() {
    DuressVerifier(fakeKdf).createVerifier(CharArray(0), salt)
  }

  @Test
  fun `different salt does not match`() {
    val verifier = DuressVerifier(fakeKdf)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)

    assertFalse(verifier.matches("let me go".toCharArray(), otherSalt, stored))
  }

  @Test
  fun `malformed stored values never match`() {
    val verifier = DuressVerifier(fakeKdf)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)

    assertFalse(verifier.matches("let me go".toCharArray(), salt, stored.copyOf(16)))
    assertFalse(verifier.matches("let me go".toCharArray(), ByteArray(0), stored))
    assertFalse(verifier.matches("let me go".toCharArray(), salt, ByteArray(0)))
  }

  @Test
  fun `comparison goes through the constant time comparator`() {
    val compared = mutableListOf<Pair<ByteArray, ByteArray>>()
    val recordingCompare: (ByteArray, ByteArray) -> Boolean = { a, b ->
      compared += a.copyOf() to b.copyOf()
      MessageDigest.isEqual(a, b)
    }
    val verifier = DuressVerifier(fakeKdf, recordingCompare)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)

    assertTrue(verifier.matches("let me go".toCharArray(), salt, stored))
    assertFalse(verifier.matches("nope".toCharArray(), salt, stored))

    assertEquals(2, compared.size)
    compared.forEach { (_, expected) -> assertArrayEquals(stored, expected) }
  }

  @Test
  fun `derived key material is zeroed after use`() {
    val verifier = DuressVerifier(fakeKdf)
    val stored = verifier.createVerifier("let me go".toCharArray(), salt)
    verifier.matches("let me go".toCharArray(), salt, stored)
    verifier.matches("wrong".toCharArray(), salt, stored)

    assertEquals(3, derivedKeys.size)
    derivedKeys.forEach { key -> assertTrue(key.all { it == 0.toByte() }) }
  }
}
