// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class TarWriterTest {

  private data class Entry(val name: String, val type: Char, val size: Long, val data: ByteArray, val pax: Map<String, String>)

  /** Reads back what [TarWriter] writes, checking each header's checksum on the way. */
  private fun read(tar: ByteArray): List<Entry> {
    assertEquals(0, tar.size % 512)
    val entries = mutableListOf<Entry>()
    var pax = emptyMap<String, String>()
    var offset = 0

    while (true) {
      val header = tar.copyOfRange(offset, offset + 512)
      if (header.all { it == 0.toByte() }) {
        assertTrue("Archive must end with two zero blocks", tar.copyOfRange(offset, offset + 1024).all { it == 0.toByte() })
        assertEquals(tar.size, offset + 1024)
        return entries
      }

      val stored = String(header, 148, 6, Charsets.US_ASCII).toInt(8)
      val sum = header.mapIndexed { i, b -> if (i in 148 until 156) 32 else b.toInt() and 0xff }.sum()
      assertEquals("Checksum of entry at $offset", sum, stored)
      assertEquals("ustar", String(header, 257, 5, Charsets.US_ASCII))

      val name = String(header, 0, 100, Charsets.US_ASCII).trimEnd('\u0000')
      val size = String(header, 124, 11, Charsets.US_ASCII).toLong(8)
      val type = header[156].toInt().toChar()
      offset += 512
      val data = tar.copyOfRange(offset, offset + size.toInt())
      offset += ((size + 511) / 512 * 512).toInt()

      if (type == 'x') {
        pax = parsePax(data)
        continue
      }

      entries += Entry(pax["path"] ?: name, type, pax["size"]?.toLong() ?: size, data, pax)
      pax = emptyMap()
    }
  }

  private fun parsePax(data: ByteArray): Map<String, String> {
    val records = mutableMapOf<String, String>()
    var offset = 0
    while (offset < data.size) {
      val space = data.indexOfFirst(offset) { it == ' '.code.toByte() }
      val length = String(data, offset, space - offset, Charsets.US_ASCII).toInt()
      val record = String(data, space + 1, length - (space - offset) - 2, Charsets.UTF_8)
      records[record.substringBefore('=')] = record.substringAfter('=')
      offset += length
    }
    return records
  }

  private fun ByteArray.indexOfFirst(from: Int, predicate: (Byte) -> Boolean): Int {
    for (i in from until size) if (predicate(this[i])) return i
    return -1
  }

  private fun write(block: TarWriter.() -> Unit): ByteArray {
    val out = ByteArrayOutputStream()
    TarWriter(out, 1_780_000_000L).apply(block).finish()
    return out.toByteArray()
  }

  @Test
  fun `folders and files come back with their names, sizes and bytes`() {
    val body = "hello\n".toByteArray()
    val entries = read(
      write {
        addDirectory("Alice 2026-10-08 1403")
        addFile("Alice 2026-10-08 1403/chat.txt", body.size.toLong()) { it.write(body) }
        addFile("Alice 2026-10-08 1403/empty.json", 0) { }
      }
    )

    assertEquals(listOf("Alice 2026-10-08 1403/", "Alice 2026-10-08 1403/chat.txt", "Alice 2026-10-08 1403/empty.json"), entries.map { it.name })
    assertEquals(listOf('5', '0', '0'), entries.map { it.type })
    assertArrayEquals(body, entries[1].data)
    assertEquals(0L, entries[2].size)
  }

  @Test
  fun `a file of exactly one block needs no padding`() {
    val block = ByteArray(512) { 7 }
    val tar = write { addFile("a.bin", 512) { it.write(block) } }
    assertEquals(512 + 512 + 1024, tar.size)
  }

  @Test
  fun `non ASCII names go into a pax header`() {
    val name = "Book club 📚/chat.html"
    val entry = read(write { addFile(name, 1) { it.write(1) } }).single()
    assertEquals(name, entry.name)
    assertEquals(name, entry.pax["path"])
  }

  @Test
  fun `names longer than 100 bytes go into a pax header`() {
    val name = "Wren export 2026-10-08 1403/" + "x".repeat(64) + "/media/1234567890-12.jpeg"
    assertTrue(name.length > 100)
    assertEquals(name, read(write { addFile(name, 0) { } }).single().name)
  }

  @Test
  fun `pax record lengths count their own digits across the 99 to 100 step`() {
    val name = "é".repeat(45) + "a"
    val entry = read(write { addFile(name, 0) { } }).single()
    assertEquals(name, entry.name)
  }

  @Test(expected = IOException::class)
  fun `writing fewer bytes than declared throws`() {
    write { addFile("short", 10) { it.write(ByteArray(9)) } }
  }

  @Test(expected = IOException::class)
  fun `writing more bytes than declared throws`() {
    write { addFile("long", 10) { it.write(ByteArray(11)) } }
  }
}
