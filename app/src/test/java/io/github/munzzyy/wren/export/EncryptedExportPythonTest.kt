// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import io.github.munzzyy.wren.crypto.ScryptParams
import io.github.munzzyy.wren.crypto.Wrenx
import io.github.munzzyy.wren.crypto.WrenxOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Runs tools/wren-export-decrypt.py against files the app's code writes. Skipped when python3 or
 * its cryptography package is missing on the machine running the tests.
 */
class EncryptedExportPythonTest {

  @get:Rule
  val temp = TemporaryFolder()

  private lateinit var tool: File

  private val passphrase = "plum orbit candle seven"
  private val chat = "<!DOCTYPE html>\n<p>Café at six? 😀</p>\n".toByteArray()
  private val photo = Random(11).nextBytes(Wrenx.CHUNK_SIZE + 4096)

  @Before
  fun setUp() {
    assumeTrue("python3 with the cryptography package is not available", run(listOf("python3", "-c", "import cryptography"), null).first == 0)
    tool = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .map { File(it, "tools/wren-export-decrypt.py") }
      .firstOrNull { it.isFile } ?: error("tools/wren-export-decrypt.py not found")
  }

  private fun run(command: List<String>, stdin: String?): Pair<Int, String> {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.outputStream.use { if (stdin != null) it.write(stdin.toByteArray()) }
    val output = process.inputStream.bufferedReader().readText()
    assertTrue("$command timed out", process.waitFor(120, TimeUnit.SECONDS))
    return process.exitValue() to output
  }

  private fun writeExport(params: ScryptParams = Wrenx.DEFAULT_PARAMS): File {
    val file = temp.newFile("Wren chat 2026-10-08 1403.wrenx")
    file.outputStream().use { stream ->
      val wrenx = WrenxOutputStream(stream, passphrase.toCharArray(), params, SecureRandom())
      TarWriter(wrenx, 1_780_000_000L).apply {
        addDirectory("Book club 📚 2026-10-08 1403")
        addFile("Book club 📚 2026-10-08 1403/chat.html", chat.size.toLong()) { it.write(chat) }
        addDirectory("Book club 📚 2026-10-08 1403/media")
        addFile("Book club 📚 2026-10-08 1403/media/1234-1.jpg", photo.size.toLong()) { it.write(photo) }
        finish()
      }
      wrenx.finish()
    }
    return file
  }

  private fun decrypt(file: File, output: File, secret: String = passphrase): Pair<Int, String> {
    return run(listOf("python3", "-I", tool.path, "--passphrase-stdin", file.path, output.path), "$secret\n")
  }

  @Test
  fun `the tool unpacks what the app writes`() {
    val output = File(temp.root, "out")
    val (code, log) = decrypt(writeExport(), output)
    assertEquals(log, 0, code)

    val folder = "Book club 📚 2026-10-08 1403"
    val expected = setOf(
      "${hex("$folder/chat.html".toByteArray())} ${sha256(chat)}",
      "${hex("$folder/media/1234-1.jpg".toByteArray())} ${sha256(photo)}"
    )
    assertEquals(expected, listFiles(output))
  }

  private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

  private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

  /**
   * Lists the unpacked files through Python, because the Gradle test JVM may not use UTF-8 for
   * file names and then cannot open the emoji folder itself.
   */
  private fun listFiles(root: File): Set<String> {
    val script = "import hashlib, os, sys\n" +
      "root = sys.argv[1]\n" +
      "for d, _, files in os.walk(root):\n" +
      "    for f in files:\n" +
      "        p = os.path.join(d, f)\n" +
      "        print(os.fsencode(os.path.relpath(p, root)).hex(), hashlib.sha256(open(p, 'rb').read()).hexdigest())\n"
    val (code, output) = run(listOf("python3", "-I", "-c", script, root.path), null)
    assertEquals(output, 0, code)
    return output.lines().filter { it.isNotBlank() }.toSet()
  }

  @Test
  fun `a wrong passphrase leaves no output folder`() {
    val output = File(temp.root, "out")
    val (code, log) = decrypt(writeExport(), output, "plum orbit candle eight")
    assertNotEquals(0, code)
    assertTrue(log, log.contains("Wrong passphrase"))
    assertFalse(output.exists())
  }

  @Test
  fun `a changed byte near the end leaves no output folder`() {
    val file = writeExport()
    val bytes = file.readBytes()
    bytes[bytes.size - 30] = (bytes[bytes.size - 30].toInt() xor 0x01).toByte()
    file.writeBytes(bytes)

    val output = File(temp.root, "out")
    val (code, log) = decrypt(file, output)
    assertNotEquals(0, code)
    assertTrue(log, log.contains("damaged"))
    assertFalse(output.exists())
  }

  @Test
  fun `a file without its closing chunk leaves no output folder`() {
    val file = writeExport()
    file.writeBytes(file.readBytes().let { it.copyOf(it.size - (4 + Wrenx.TAG_SIZE)) })

    val output = File(temp.root, "out")
    val (code, log) = decrypt(file, output)
    assertNotEquals(0, code)
    assertTrue(log, log.contains("ends early"))
    assertFalse(output.exists())
  }

  @Test
  fun `an archive entry that climbs out of the output folder is refused`() {
    val file = temp.newFile("evil.wrenx")
    file.outputStream().use { stream ->
      val wrenx = WrenxOutputStream(stream, passphrase.toCharArray(), ScryptParams(1 shl 10, 8, 1), SecureRandom())
      TarWriter(wrenx, 0).apply {
        addFile("../escaped.txt", 2) { it.write("hi".toByteArray()) }
        finish()
      }
      wrenx.finish()
    }

    val output = File(temp.root, "out")
    val (code, log) = decrypt(file, output)
    assertNotEquals(0, code)
    assertTrue(log, log.contains("unsafe path"))
    assertFalse(File(temp.root, "escaped.txt").exists())
  }
}
