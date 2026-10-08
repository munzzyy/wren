// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.crypto

import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts everything written to it into a .wrenx stream on [out]. The file only becomes valid
 * once [finish] writes the closing chunk; [close] on its own leaves a file every reader rejects,
 * which is what a canceled or failed export should leave behind if deleting it fails.
 *
 * The key is derived in the constructor, which takes as long as scrypt does.
 */
class WrenxOutputStream(
  private val out: OutputStream,
  passphrase: CharArray,
  params: ScryptParams = Wrenx.DEFAULT_PARAMS,
  random: SecureRandom = SecureRandom()
) : OutputStream() {

  private val header: ByteArray
  private val noncePrefix = ByteArray(Wrenx.NONCE_PREFIX_SIZE)
  private val key: SecretKeySpec
  private val cipher = Cipher.getInstance(Wrenx.CIPHER)

  private val plaintext = ByteArray(Wrenx.CHUNK_SIZE)
  private val ciphertext = ByteArray(Wrenx.MAX_CHUNK_CIPHERTEXT)
  private var buffered = 0
  private var counter = 0L
  private var finished = false
  private var closed = false

  init {
    val salt = ByteArray(Wrenx.SALT_SIZE)
    random.nextBytes(salt)
    random.nextBytes(noncePrefix)

    val keyBytes = Wrenx.deriveKey(passphrase, salt, params)
    try {
      key = SecretKeySpec(keyBytes, "AES")
    } finally {
      keyBytes.fill(0)
    }

    header = Wrenx.header(params, salt, noncePrefix)
    out.write(header)
  }

  override fun write(b: Int) {
    checkOpen()
    if (buffered == plaintext.size) writeChunk()
    plaintext[buffered++] = b.toByte()
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    checkOpen()
    if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()

    var offset = off
    var remaining = len
    while (remaining > 0) {
      if (buffered == plaintext.size) writeChunk()
      val take = minOf(remaining, plaintext.size - buffered)
      System.arraycopy(b, offset, plaintext, buffered, take)
      buffered += take
      offset += take
      remaining -= take
    }
  }

  /** Writes what is buffered and the closing chunk, then closes [out]. */
  fun finish() {
    checkOpen()
    if (buffered > 0) writeChunk()
    writeChunk()
    finished = true
    close()
  }

  override fun flush() {
    out.flush()
  }

  override fun close() {
    if (closed) return
    closed = true
    plaintext.fill(0)
    out.close()
  }

  private fun writeChunk() {
    if (counter > 0xFFFF_FFFFL) throw IOException("Too many chunks for one file")

    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(Wrenx.TAG_SIZE * 8, Wrenx.nonce(noncePrefix, counter.toInt())))
    cipher.updateAAD(header)
    val length = cipher.doFinal(plaintext, 0, buffered, ciphertext, 0)

    out.write(byteArrayOf((length ushr 24).toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte()))
    out.write(ciphertext, 0, length)

    plaintext.fill(0, 0, buffered)
    buffered = 0
    counter++
  }

  private fun checkOpen() {
    if (closed || finished) throw IOException("Stream is closed")
  }
}
