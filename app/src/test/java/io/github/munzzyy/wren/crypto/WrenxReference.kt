// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.crypto

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrenxException(message: String) : Exception(message)

/**
 * A reader written from docs/EXPORT.md rather than from [WrenxOutputStream], so the tests check
 * the format and not just that the writer agrees with itself. Like the Python tool, it checks
 * every chunk before it hands out any plaintext.
 */
object WrenxReference {

  fun decrypt(file: ByteArray, passphrase: CharArray, out: OutputStream) {
    val buffer = ByteBuffer.wrap(file)
    if (file.size < Wrenx.HEADER_SIZE) throw WrenxException("too short")

    val header = file.copyOfRange(0, Wrenx.HEADER_SIZE)
    val magic = ByteArray(6).also { buffer.get(it) }
    if (!magic.contentEquals(Wrenx.MAGIC)) throw WrenxException("bad magic")
    if (buffer.get() != Wrenx.KDF_SCRYPT) throw WrenxException("unknown kdf")
    val params = ScryptParams(buffer.int, buffer.int, buffer.int)
    if (!params.fitsWithin(Wrenx.MAX_PARAMS)) throw WrenxException("params out of range")
    val salt = ByteArray(Wrenx.SALT_SIZE).also { buffer.get(it) }
    val prefix = ByteArray(Wrenx.NONCE_PREFIX_SIZE).also { buffer.get(it) }

    val key = SecretKeySpec(Wrenx.deriveKey(passphrase, salt, params), "AES")

    val checked = ByteArrayOutputStream()
    var counter = 0
    while (true) {
      if (buffer.remaining() < 4) throw WrenxException("ends early")
      val length = buffer.int
      if (length < Wrenx.TAG_SIZE || length > Wrenx.MAX_CHUNK_CIPHERTEXT) throw WrenxException("bad chunk length")
      if (buffer.remaining() < length) throw WrenxException("ends early")
      val ciphertext = ByteArray(length).also { buffer.get(it) }

      val plaintext = try {
        Cipher.getInstance("AES/GCM/NoPadding").run {
          init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, prefix + ByteBuffer.allocate(4).putInt(counter).array()))
          updateAAD(header)
          doFinal(ciphertext)
        }
      } catch (e: GeneralSecurityException) {
        throw WrenxException("chunk $counter fails its tag")
      }
      counter++

      if (plaintext.isEmpty()) {
        if (buffer.hasRemaining()) throw WrenxException("data after the end")
        break
      }
      checked.write(plaintext)
    }

    out.write(checked.toByteArray())
  }
}
