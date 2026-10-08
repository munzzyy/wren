// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.crypto

import io.github.munzzyy.wren.export.TarWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.random.Random

class WrenxTest {

  companion object {
    val FAST = ScryptParams(n = 1 shl 10, r = 8, p = 1)
    const val PASSPHRASE = "plum orbit candle seven"
  }

  private fun encrypt(plaintext: ByteArray, passphrase: String = PASSPHRASE, params: ScryptParams = FAST): ByteArray {
    val out = ByteArrayOutputStream()
    WrenxOutputStream(out, passphrase.toCharArray(), params, SecureRandom()).apply {
      write(plaintext)
      finish()
    }
    return out.toByteArray()
  }

  private fun decrypt(file: ByteArray, passphrase: String = PASSPHRASE): ByteArray {
    val out = ByteArrayOutputStream()
    WrenxReference.decrypt(file, passphrase.toCharArray(), out)
    return out.toByteArray()
  }

  private fun smallTar(): ByteArray {
    val out = ByteArrayOutputStream()
    TarWriter(out, 1_780_000_000L).apply {
      addDirectory("Alice 2026-10-08 1403")
      val chat = "<!DOCTYPE html>\n<p>See you at six</p>\n".toByteArray()
      addFile("Alice 2026-10-08 1403/chat.html", chat.size.toLong()) { it.write(chat) }
      addDirectory("Alice 2026-10-08 1403/media")
      val photo = Random(7).nextBytes(3000)
      addFile("Alice 2026-10-08 1403/media/1234-1.jpg", photo.size.toLong()) { it.write(photo) }
      finish()
    }
    return out.toByteArray()
  }

  /** Where chunk [index] starts, counting from 0 after the header. */
  private fun chunkOffset(file: ByteArray, index: Int): Int {
    var offset = Wrenx.HEADER_SIZE
    repeat(index) { offset += 4 + ByteBuffer.wrap(file, offset, 4).int }
    return offset
  }

  private fun assertRejected(file: ByteArray, passphrase: String = PASSPHRASE) {
    val out = ByteArrayOutputStream()
    try {
      WrenxReference.decrypt(file, passphrase.toCharArray(), out)
      fail("Expected the file to be rejected")
    } catch (e: WrenxException) {
      assertEquals("No plaintext may come out of a rejected file", 0, out.size())
    }
  }

  @Test
  fun `a small tar round trips byte for byte`() {
    val tar = smallTar()
    assertArrayEquals(tar, decrypt(encrypt(tar)))
  }

  @Test
  fun `data over several chunks round trips`() {
    val data = Random(1).nextBytes(Wrenx.CHUNK_SIZE * 2 + 12345)
    val file = encrypt(data)
    assertArrayEquals(data, decrypt(file))

    val expected = Wrenx.HEADER_SIZE + 3 * (4 + Wrenx.TAG_SIZE) + data.size + (4 + Wrenx.TAG_SIZE)
    assertEquals(expected, file.size)
  }

  @Test
  fun `a chunk of exactly the chunk size is followed only by the closing chunk`() {
    val data = Random(2).nextBytes(Wrenx.CHUNK_SIZE)
    val file = encrypt(data)
    assertArrayEquals(data, decrypt(file))
    assertEquals(Wrenx.HEADER_SIZE + (4 + Wrenx.MAX_CHUNK_CIPHERTEXT) + (4 + Wrenx.TAG_SIZE), file.size)
  }

  @Test
  fun `an empty export still has a closing chunk`() {
    assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
  }

  @Test
  fun `the header is laid out as documented`() {
    val file = encrypt(ByteArray(10), params = FAST)
    val header = ByteBuffer.wrap(file)
    val magic = ByteArray(6).also { header.get(it) }
    assertArrayEquals("WRENX".toByteArray() + byteArrayOf(1), magic)
    assertEquals(1.toByte(), header.get())
    assertEquals(FAST.n, header.int)
    assertEquals(FAST.r, header.int)
    assertEquals(FAST.p, header.int)
    assertEquals(43, Wrenx.HEADER_SIZE)
  }

  @Test
  fun `a changed byte in the last data chunk is caught before any plaintext comes out`() {
    val file = encrypt(Random(3).nextBytes(Wrenx.CHUNK_SIZE + 500))
    val target = chunkOffset(file, 1) + 4 + 100
    file[target] = (file[target].toInt() xor 0x01).toByte()
    assertRejected(file)
  }

  @Test
  fun `a changed tag is caught`() {
    val file = encrypt(smallTar())
    val lastDataChunkEnd = chunkOffset(file, 1) - 1
    file[lastDataChunkEnd] = (file[lastDataChunkEnd].toInt() xor 0x80).toByte()
    assertRejected(file)
  }

  @Test
  fun `the wrong passphrase is rejected`() {
    assertRejected(encrypt(smallTar()), passphrase = "plum orbit candle eight")
  }

  @Test
  fun `a file cut off before the closing chunk is rejected`() {
    val file = encrypt(smallTar())
    assertRejected(file.copyOf(chunkOffset(file, 1)))
  }

  @Test
  fun `a file cut off in the middle of a chunk is rejected`() {
    val file = encrypt(smallTar())
    assertRejected(file.copyOf(file.size - 3))
  }

  @Test
  fun `bytes after the closing chunk are rejected`() {
    assertRejected(encrypt(smallTar()) + byteArrayOf(0))
  }

  @Test
  fun `swapped chunks are rejected`() {
    val file = encrypt(Random(4).nextBytes(Wrenx.CHUNK_SIZE * 2))
    val first = chunkOffset(file, 0)
    val second = chunkOffset(file, 1)
    val third = chunkOffset(file, 2)
    val swapped = file.copyOfRange(0, first) + file.copyOfRange(second, third) + file.copyOfRange(first, second) + file.copyOfRange(third, file.size)
    assertRejected(swapped)
  }

  @Test
  fun `a dropped middle chunk is rejected`() {
    val file = encrypt(Random(5).nextBytes(Wrenx.CHUNK_SIZE * 2 + 1))
    val dropped = file.copyOfRange(0, chunkOffset(file, 1)) + file.copyOfRange(chunkOffset(file, 2), file.size)
    assertRejected(dropped)
  }

  @Test
  fun `a changed header is rejected even when the key would still derive`() {
    val file = encrypt(smallTar())
    val nonceByte = Wrenx.HEADER_SIZE - 1
    file[nonceByte] = (file[nonceByte].toInt() xor 0x01).toByte()
    assertRejected(file)
  }

  @Test
  fun `scrypt settings beyond the limits are refused before deriving`() {
    val file = encrypt(ByteArray(1))
    ByteBuffer.wrap(file).putInt(7, 1 shl 22)
    assertRejected(file)
  }

  @Test
  fun `a stream closed without finish is not a valid file`() {
    val out = ByteArrayOutputStream()
    WrenxOutputStream(out, PASSPHRASE.toCharArray(), FAST, SecureRandom()).apply {
      write(smallTar())
      close()
    }
    assertRejected(out.toByteArray())
  }

  @Test
  fun `two exports with the same passphrase share no salt and no ciphertext`() {
    val tar = smallTar()
    val a = encrypt(tar)
    val b = encrypt(tar)
    assertFalse(a.copyOfRange(19, 35).contentEquals(b.copyOfRange(19, 35)))
    assertFalse(a.copyOfRange(Wrenx.HEADER_SIZE, Wrenx.HEADER_SIZE + 64).contentEquals(b.copyOfRange(Wrenx.HEADER_SIZE, Wrenx.HEADER_SIZE + 64)))
  }

  @Test
  fun `the passphrase is compared in NFC so a decomposed accent still opens the file`() {
    val composed = "café au lait"
    val decomposed = "café au lait"
    val tar = smallTar()
    assertArrayEquals(tar, decrypt(encrypt(tar, passphrase = composed), passphrase = decomposed))
  }
}
