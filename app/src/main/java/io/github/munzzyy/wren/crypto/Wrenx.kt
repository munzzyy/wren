// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.text.Normalizer

/**
 * The .wrenx container, described in docs/EXPORT.md under "File format":
 *
 * header: "WRENX" 0x01, KDF id (1 byte), n, r, p (4 bytes each, big endian), salt (16 bytes),
 * nonce prefix (8 bytes). Then chunks: ciphertext length (4 bytes, big endian) and the AES-256-GCM
 * ciphertext with its 16-byte tag. Chunk i uses the nonce prefix followed by i as 4 big-endian
 * bytes, and the whole header as associated data. The last chunk has no plaintext.
 */
object Wrenx {

  val MAGIC = byteArrayOf('W'.code.toByte(), 'R'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'X'.code.toByte(), 1)

  const val KDF_SCRYPT: Byte = 1

  const val SALT_SIZE = 16
  const val NONCE_PREFIX_SIZE = 8
  const val KEY_SIZE = 32
  const val TAG_SIZE = 16
  const val HEADER_SIZE = 6 + 1 + 4 + 4 + 4 + SALT_SIZE + NONCE_PREFIX_SIZE

  const val CHUNK_SIZE = 1024 * 1024
  const val MAX_CHUNK_CIPHERTEXT = CHUNK_SIZE + TAG_SIZE

  const val CIPHER = "AES/GCM/NoPadding"

  const val FILE_EXTENSION = "wrenx"

  /** About 32 MiB and a fraction of a second on a recent phone. */
  val DEFAULT_PARAMS = ScryptParams(n = 1 shl 15, r = 8, p = 1)

  /** The most a reader accepts from a header: 1 GiB of memory, so a crafted file cannot ask for more. */
  val MAX_PARAMS = ScryptParams(n = 1 shl 20, r = 8, p = 4)

  fun header(params: ScryptParams, salt: ByteArray, noncePrefix: ByteArray): ByteArray {
    require(salt.size == SALT_SIZE && noncePrefix.size == NONCE_PREFIX_SIZE)
    return ByteBuffer.allocate(HEADER_SIZE)
      .put(MAGIC)
      .put(KDF_SCRYPT)
      .putInt(params.n)
      .putInt(params.r)
      .putInt(params.p)
      .put(salt)
      .put(noncePrefix)
      .array()
  }

  fun nonce(noncePrefix: ByteArray, counter: Int): ByteArray {
    return ByteBuffer.allocate(NONCE_PREFIX_SIZE + 4).put(noncePrefix).putInt(counter).array()
  }

  /**
   * The bytes scrypt sees: the passphrase in Unicode NFC, as UTF-8. NFC keeps a passphrase typed
   * with a different keyboard on a computer from turning into different bytes.
   */
  fun passphraseBytes(passphrase: CharArray): ByteArray {
    val chars = CharBuffer.wrap(passphrase)
    val normalized = if (Normalizer.isNormalized(chars, Normalizer.Form.NFC)) chars else CharBuffer.wrap(Normalizer.normalize(chars, Normalizer.Form.NFC))
    val encoded = Charsets.UTF_8.newEncoder().encode(normalized)
    val bytes = ByteArray(encoded.remaining())
    encoded.get(bytes)
    if (encoded.hasArray()) encoded.array().fill(0)
    return bytes
  }

  fun deriveKey(passphrase: CharArray, salt: ByteArray, params: ScryptParams): ByteArray {
    val bytes = passphraseBytes(passphrase)
    try {
      return Scrypt.derive(bytes, salt, params.n, params.r, params.p, KEY_SIZE)
    } finally {
      bytes.fill(0)
    }
  }
}

data class ScryptParams(val n: Int, val r: Int, val p: Int) {
  fun fitsWithin(max: ScryptParams): Boolean {
    return n > 1 && n and (n - 1) == 0 && n <= max.n && r in 1..max.r && p in 1..max.p
  }
}
