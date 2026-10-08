// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * scrypt as specified in RFC 7914, so a computer can derive the same key with Python's
 * hashlib.scrypt. Needs 128 * r * n bytes of memory while it runs.
 */
object Scrypt {

  private const val HMAC = "HmacSHA256"
  private const val HMAC_BLOCK_SIZE = 64
  private const val HMAC_SIZE = 32

  fun derive(passphrase: ByteArray, salt: ByteArray, n: Int, r: Int, p: Int, keyLength: Int): ByteArray {
    require(n > 1 && n and (n - 1) == 0) { "n must be a power of two above 1" }
    require(r >= 1 && p >= 1) { "r and p must be positive" }
    require(keyLength >= 1) { "keyLength must be positive" }
    require(r.toLong() * p < (1L shl 30)) { "r * p is too large" }
    require(32L * r * n <= Int.MAX_VALUE - 8) { "n and r need more memory than one array can hold" }

    val blockInts = 32 * r
    val b = pbkdf2(passphrase, salt, p * 128 * r)
    val x = IntArray(blockInts)
    val y = IntArray(blockInts)
    val v = IntArray(blockInts * n)
    val scratch = IntArray(16)

    try {
      for (i in 0 until p) {
        val offset = i * 128 * r
        for (k in 0 until blockInts) {
          x[k] = littleEndianInt(b, offset + k * 4)
        }
        roMix(x, y, v, scratch, r, n)
        for (k in 0 until blockInts) {
          putLittleEndianInt(b, offset + k * 4, x[k])
        }
      }
      return pbkdf2(passphrase, b, keyLength)
    } finally {
      b.fill(0)
      x.fill(0)
      y.fill(0)
      v.fill(0)
      scratch.fill(0)
    }
  }

  private fun roMix(x: IntArray, y: IntArray, v: IntArray, scratch: IntArray, r: Int, n: Int) {
    val blockInts = 32 * r

    for (i in 0 until n) {
      System.arraycopy(x, 0, v, i * blockInts, blockInts)
      blockMix(x, y, scratch, r)
    }

    val last = (2 * r - 1) * 16
    for (i in 0 until n) {
      val base = (x[last] and (n - 1)) * blockInts
      for (k in 0 until blockInts) {
        x[k] = x[k] xor v[base + k]
      }
      blockMix(x, y, scratch, r)
    }
  }

  /** BlockMix with Salsa20/8 on [b] in place, with [y] as the output buffer. */
  private fun blockMix(b: IntArray, y: IntArray, x: IntArray, r: Int) {
    System.arraycopy(b, (2 * r - 1) * 16, x, 0, 16)

    for (i in 0 until 2 * r) {
      for (k in 0 until 16) {
        x[k] = x[k] xor b[i * 16 + k]
      }
      salsa208(x)
      val target = if (i % 2 == 0) (i / 2) * 16 else (r + i / 2) * 16
      System.arraycopy(x, 0, y, target, 16)
    }

    System.arraycopy(y, 0, b, 0, 32 * r)
  }

  private fun salsa208(b: IntArray) {
    var x0 = b[0]; var x1 = b[1]; var x2 = b[2]; var x3 = b[3]
    var x4 = b[4]; var x5 = b[5]; var x6 = b[6]; var x7 = b[7]
    var x8 = b[8]; var x9 = b[9]; var x10 = b[10]; var x11 = b[11]
    var x12 = b[12]; var x13 = b[13]; var x14 = b[14]; var x15 = b[15]

    repeat(4) {
      x4 = x4 xor (x0 + x12).rotateLeft(7); x8 = x8 xor (x4 + x0).rotateLeft(9)
      x12 = x12 xor (x8 + x4).rotateLeft(13); x0 = x0 xor (x12 + x8).rotateLeft(18)
      x9 = x9 xor (x5 + x1).rotateLeft(7); x13 = x13 xor (x9 + x5).rotateLeft(9)
      x1 = x1 xor (x13 + x9).rotateLeft(13); x5 = x5 xor (x1 + x13).rotateLeft(18)
      x14 = x14 xor (x10 + x6).rotateLeft(7); x2 = x2 xor (x14 + x10).rotateLeft(9)
      x6 = x6 xor (x2 + x14).rotateLeft(13); x10 = x10 xor (x6 + x2).rotateLeft(18)
      x3 = x3 xor (x15 + x11).rotateLeft(7); x7 = x7 xor (x3 + x15).rotateLeft(9)
      x11 = x11 xor (x7 + x3).rotateLeft(13); x15 = x15 xor (x11 + x7).rotateLeft(18)

      x1 = x1 xor (x0 + x3).rotateLeft(7); x2 = x2 xor (x1 + x0).rotateLeft(9)
      x3 = x3 xor (x2 + x1).rotateLeft(13); x0 = x0 xor (x3 + x2).rotateLeft(18)
      x6 = x6 xor (x5 + x4).rotateLeft(7); x7 = x7 xor (x6 + x5).rotateLeft(9)
      x4 = x4 xor (x7 + x6).rotateLeft(13); x5 = x5 xor (x4 + x7).rotateLeft(18)
      x11 = x11 xor (x10 + x9).rotateLeft(7); x8 = x8 xor (x11 + x10).rotateLeft(9)
      x9 = x9 xor (x8 + x11).rotateLeft(13); x10 = x10 xor (x9 + x8).rotateLeft(18)
      x12 = x12 xor (x15 + x14).rotateLeft(7); x13 = x13 xor (x12 + x15).rotateLeft(9)
      x14 = x14 xor (x13 + x12).rotateLeft(13); x15 = x15 xor (x14 + x13).rotateLeft(18)
    }

    b[0] += x0; b[1] += x1; b[2] += x2; b[3] += x3
    b[4] += x4; b[5] += x5; b[6] += x6; b[7] += x7
    b[8] += x8; b[9] += x9; b[10] += x10; b[11] += x11
    b[12] += x12; b[13] += x13; b[14] += x14; b[15] += x15
  }

  /**
   * PBKDF2-HMAC-SHA256 with one iteration, which is all scrypt asks of it. An empty passphrase is
   * passed as a block of zeros: HMAC pads short keys with zeros anyway, and the JCE refuses an
   * empty key.
   */
  internal fun pbkdf2(passphrase: ByteArray, salt: ByteArray, length: Int): ByteArray {
    val mac = Mac.getInstance(HMAC)
    mac.init(SecretKeySpec(if (passphrase.isEmpty()) ByteArray(HMAC_BLOCK_SIZE) else passphrase, HMAC))

    val out = ByteArray(length)
    val counter = ByteArray(4)
    var block = 1
    var written = 0
    while (written < length) {
      counter[0] = (block ushr 24).toByte()
      counter[1] = (block ushr 16).toByte()
      counter[2] = (block ushr 8).toByte()
      counter[3] = block.toByte()
      mac.update(salt)
      mac.update(counter)
      val t = mac.doFinal()
      val take = minOf(HMAC_SIZE, length - written)
      System.arraycopy(t, 0, out, written, take)
      t.fill(0)
      written += take
      block++
    }
    return out
  }

  private fun littleEndianInt(b: ByteArray, offset: Int): Int {
    return (b[offset].toInt() and 0xff) or
      ((b[offset + 1].toInt() and 0xff) shl 8) or
      ((b[offset + 2].toInt() and 0xff) shl 16) or
      ((b[offset + 3].toInt() and 0xff) shl 24)
  }

  private fun putLittleEndianInt(b: ByteArray, offset: Int, value: Int) {
    b[offset] = value.toByte()
    b[offset + 1] = (value ushr 8).toByte()
    b[offset + 2] = (value ushr 16).toByte()
    b[offset + 3] = (value ushr 24).toByte()
  }
}
