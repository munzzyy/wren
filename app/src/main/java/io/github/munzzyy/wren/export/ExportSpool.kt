// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A temporary file for a chat file whose size has to be known before it can go into a tar
 * archive. It is encrypted with a key that only lives in this object, so the plaintext of an
 * encrypted export never touches the disk, and a file left over after a crash is unreadable.
 */
internal class ExportSpool(directory: File, random: SecureRandom = SecureRandom()) : Closeable {

  companion object {
    private const val PREFIX = "wren-export-"
    private const val SUFFIX = ".spool"
    private const val CIPHER = "AES/CTR/NoPadding"

    /** Removes spool files a killed export left behind. Their keys died with that process. */
    fun deleteStale(directory: File) {
      directory.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }?.forEach { it.delete() }
    }
  }

  private val file: File = File.createTempFile(PREFIX, SUFFIX, directory)
  private val key: SecretKeySpec
  private val iv = ByteArray(16)

  var length = 0L
    private set

  init {
    val keyBytes = ByteArray(32)
    random.nextBytes(keyBytes)
    random.nextBytes(iv)
    key = SecretKeySpec(keyBytes, "AES")
    keyBytes.fill(0)
  }

  fun openOutput(): OutputStream {
    length = 0
    val encrypting = CipherOutputStream(BufferedOutputStream(FileOutputStream(file)), cipher(Cipher.ENCRYPT_MODE))
    return object : FilterOutputStream(encrypting) {
      override fun write(b: Int) {
        out.write(b)
        length++
      }

      override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        length += len
      }
    }
  }

  fun openInput(): InputStream {
    return CipherInputStream(BufferedInputStream(FileInputStream(file)), cipher(Cipher.DECRYPT_MODE))
  }

  override fun close() {
    file.delete()
  }

  private fun cipher(mode: Int): Cipher {
    val cipher = Cipher.getInstance(CIPHER)
    cipher.init(mode, key, IvParameterSpec(iv))
    return cipher
  }
}
