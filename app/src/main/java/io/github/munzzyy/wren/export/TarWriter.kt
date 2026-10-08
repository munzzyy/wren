// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * A minimal ustar writer for regular files and folders. Paths that are not plain ASCII or longer
 * than 100 bytes, and files of 8 GiB or more, get a POSIX pax header so any modern tar, Python's
 * tarfile and 7-Zip read them back exactly.
 */
class TarWriter(private val out: OutputStream, private val modifiedSeconds: Long) {

  companion object {
    private const val BLOCK = 512
    private const val NAME_SIZE = 100
    private const val MAX_OCTAL_SIZE = 0x1_FFFF_FFFFL

    private const val TYPE_FILE = '0'
    private const val TYPE_DIRECTORY = '5'
    private const val TYPE_PAX = 'x'
  }

  private val zeros = ByteArray(BLOCK)

  fun addDirectory(path: String) {
    val name = path.trimEnd('/') + "/"
    writeEntryHeader(name, 0, TYPE_DIRECTORY, "0000755")
  }

  /**
   * Adds a file of exactly [size] bytes. [writeContent] must write that many bytes; fewer or more
   * leaves the archive broken, so both throw.
   */
  fun addFile(path: String, size: Long, writeContent: (OutputStream) -> Unit) {
    require(size >= 0)
    writeEntryHeader(path, size, TYPE_FILE, "0000644")

    val counting = BoundedOutputStream(out, size)
    writeContent(counting)
    if (counting.written != size) {
      throw IOException("Wrote ${counting.written} of $size bytes for a tar entry")
    }
    pad(size)
  }

  /** Writes the two empty blocks that end a tar archive. Does not close the stream. */
  fun finish() {
    out.write(zeros)
    out.write(zeros)
    out.flush()
  }

  private fun writeEntryHeader(path: String, size: Long, type: Char, mode: String) {
    val pathBytes = path.toByteArray(Charsets.UTF_8)
    val fitsUstar = pathBytes.size <= NAME_SIZE && path.all { it.code in 0x20..0x7e }
    val sizeFits = size <= MAX_OCTAL_SIZE

    if (!fitsUstar || !sizeFits) {
      val records = StringBuilder()
      if (!fitsUstar) records.append(paxRecord("path", path))
      if (!sizeFits) records.append(paxRecord("size", size.toString()))
      val data = records.toString().toByteArray(Charsets.UTF_8)
      out.write(header(asciiFallback("PaxHeaders/" + path.trimEnd('/').substringAfterLast('/')), data.size.toLong(), TYPE_PAX, "0000644"))
      out.write(data)
      pad(data.size.toLong())
    }

    val name = if (fitsUstar) path else asciiFallback(path)
    out.write(header(name, if (sizeFits) size else 0, type, mode))
  }

  private fun header(name: String, size: Long, type: Char, mode: String): ByteArray {
    val h = ByteArray(BLOCK)
    putAscii(h, 0, name, NAME_SIZE)
    putAscii(h, 100, mode, 8)
    putAscii(h, 108, "0000000", 8)
    putAscii(h, 116, "0000000", 8)
    putAscii(h, 124, size.toString(8).padStart(11, '0'), 12)
    putAscii(h, 136, modifiedSeconds.coerceIn(0, MAX_OCTAL_SIZE).toString(8).padStart(11, '0'), 12)
    for (i in 148 until 156) h[i] = ' '.code.toByte()
    h[156] = type.code.toByte()
    putAscii(h, 257, "ustar", 6)
    putAscii(h, 263, "00", 2)

    var sum = 0
    for (b in h) sum += b.toInt() and 0xff
    putAscii(h, 148, sum.toString(8).padStart(6, '0'), 6)
    h[154] = 0
    h[155] = ' '.code.toByte()
    return h
  }

  private fun paxRecord(key: String, value: String): String {
    val body = " $key=$value\n"
    val bodyLength = body.toByteArray(Charsets.UTF_8).size
    var length = bodyLength + 1
    while (length != bodyLength + length.toString().length) {
      length = bodyLength + length.toString().length
    }
    return "$length$body"
  }

  private fun asciiFallback(path: String): String {
    val ascii = buildString {
      for (c in path) append(if (c.code in 0x20..0x7e) c else '_')
    }
    return if (ascii.length <= NAME_SIZE) ascii else ascii.substring(ascii.length - NAME_SIZE)
  }

  private fun putAscii(target: ByteArray, offset: Int, value: String, field: Int) {
    val bytes = value.toByteArray(Charsets.US_ASCII)
    System.arraycopy(bytes, 0, target, offset, minOf(bytes.size, field))
  }

  private fun pad(size: Long) {
    val remainder = (size % BLOCK).toInt()
    if (remainder != 0) out.write(zeros, 0, BLOCK - remainder)
  }

  private class BoundedOutputStream(out: OutputStream, private val limit: Long) : FilterOutputStream(out) {
    var written = 0L
      private set

    override fun write(b: Int) {
      if (written + 1 > limit) throw IOException("Tar entry is larger than its header says")
      out.write(b)
      written++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      if (written + len > limit) throw IOException("Tar entry is larger than its header says")
      out.write(b, off, len)
      written += len
    }

    override fun close() = Unit
  }
}
