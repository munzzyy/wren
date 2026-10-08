// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExportSpoolTest {

  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `what goes in comes back out, and the file on disk is not the plaintext`() {
    val text = "Alice: see you at six\n".repeat(5000).toByteArray()

    ExportSpool(temp.root).use { spool ->
      spool.openOutput().use { it.write(text) }
      assertEquals(text.size.toLong(), spool.length)

      val onDisk = temp.root.listFiles()!!.single().readBytes()
      assertEquals(text.size, onDisk.size)
      assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("see you at six"))

      assertArrayEquals(text, spool.openInput().use { it.readBytes() })
    }

    assertTrue(temp.root.listFiles()!!.isEmpty())
  }

  @Test
  fun `leftover spool files are deleted and nothing else is`() {
    File(temp.root, "wren-export-123.spool").writeText("x")
    File(temp.root, "other.spool").writeText("x")

    ExportSpool.deleteStale(temp.root)

    assertEquals(listOf("other.spool"), temp.root.list()!!.toList())
  }
}
