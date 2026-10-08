// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import java.security.MessageDigest

/**
 * Checks a passphrase against the stored duress verifier, SHA-256 of the key the
 * [kdf] derives. The KDF output is zeroed after every use.
 */
class DuressVerifier(
  private val kdf: (CharArray, ByteArray) -> ByteArray,
  private val constantTimeEquals: (ByteArray, ByteArray) -> Boolean = MessageDigest::isEqual
) {

  companion object {
    const val SALT_LENGTH = 16
    const val VERIFIER_LENGTH = 32
  }

  fun createVerifier(passphrase: CharArray, salt: ByteArray): ByteArray {
    require(passphrase.isNotEmpty()) { "Empty passphrase" }
    require(salt.size == SALT_LENGTH) { "Bad salt length" }
    return deriveVerifier(passphrase, salt)
  }

  fun matches(passphrase: CharArray, salt: ByteArray, expected: ByteArray): Boolean {
    if (passphrase.isEmpty() || salt.size != SALT_LENGTH || expected.size != VERIFIER_LENGTH) {
      return false
    }

    val actual = deriveVerifier(passphrase, salt)
    return try {
      constantTimeEquals(actual, expected)
    } finally {
      actual.fill(0)
    }
  }

  private fun deriveVerifier(passphrase: CharArray, salt: ByteArray): ByteArray {
    val key = kdf(passphrase, salt)
    return try {
      MessageDigest.getInstance("SHA-256").digest(key)
    } finally {
      key.fill(0)
    }
  }
}
