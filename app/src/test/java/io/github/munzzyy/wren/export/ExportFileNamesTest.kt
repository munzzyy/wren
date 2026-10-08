// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ExportFileNamesTest {

  private val mimeTypes = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "audio/aac" to "aac",
    "application/x-weird" to "../etc",
    "application/x-long" to "verylongextension"
  )

  private fun extensionFor(mimeType: String): String? = mimeTypes[mimeType]

  @Test
  fun `plain names pass through`() {
    assertEquals("Alice", ExportFileNames.sanitizeChatName("Alice"))
    assertEquals("Book club 📚", ExportFileNames.sanitizeChatName("Book club 📚"))
  }

  @Test
  fun `path separators and reserved characters are removed`() {
    assertEquals("etcpasswd", ExportFileNames.sanitizeChatName("../etc/passwd"))
    assertEquals("ab", ExportFileNames.sanitizeChatName("a\\b"))
    assertEquals("What now", ExportFileNames.sanitizeChatName("What: now?*\"<>|"))
  }

  @Test
  fun `leading and trailing dots are removed`() {
    assertEquals("chat", ExportFileNames.sanitizeChatName(".."))
    assertEquals("chat", ExportFileNames.sanitizeChatName("."))
    assertEquals("hidden", ExportFileNames.sanitizeChatName(".hidden"))
    assertEquals("Mr. T", ExportFileNames.sanitizeChatName("Mr. T..."))
  }

  @Test
  fun `control and format characters are removed and whitespace collapses`() {
    assertEquals("ab", ExportFileNames.sanitizeChatName("a\u0000b"))
    assertEquals("a b c", ExportFileNames.sanitizeChatName("  a \t\n b\r\n   c  "))
    assertEquals("evilgpj.exe", ExportFileNames.sanitizeChatName("evil‮gpj.exe"))
    assertEquals("zero width", ExportFileNames.sanitizeChatName("zero​ width"))
    assertEquals("a b", ExportFileNames.sanitizeChatName("a b"))
  }

  @Test
  fun `empty and blank names fall back`() {
    assertEquals("chat", ExportFileNames.sanitizeChatName(null))
    assertEquals("chat", ExportFileNames.sanitizeChatName(""))
    assertEquals("chat", ExportFileNames.sanitizeChatName("   "))
    assertEquals("chat", ExportFileNames.sanitizeChatName("///"))
    assertEquals("chat", ExportFileNames.sanitizeChatName("\u0001\u0002"))
  }

  @Test
  fun `long names are capped without splitting surrogate pairs`() {
    val long = "x".repeat(200)
    assertEquals(ExportFileNames.MAX_NAME_LENGTH, ExportFileNames.sanitizeChatName(long).length)

    val emoji = "😀".repeat(100)
    val capped = ExportFileNames.sanitizeChatName(emoji)
    assertEquals(ExportFileNames.MAX_NAME_LENGTH, capped.codePointCount(0, capped.length))
    assertFalse(Character.isHighSurrogate(capped.last()))
  }

  @Test
  fun `cap does not leave a trailing space`() {
    val name = "a".repeat(ExportFileNames.MAX_NAME_LENGTH - 1) + " bcd"
    assertEquals("a".repeat(ExportFileNames.MAX_NAME_LENGTH - 1), ExportFileNames.sanitizeChatName(name))
  }

  @Test
  fun `folder name adds a timestamp without colons`() {
    val millis = Instant.parse("2026-10-08T14:03:00Z").toEpochMilli()
    assertEquals("Alice 2026-10-08 1403", ExportFileNames.folderName("Alice", millis, ZoneOffset.UTC))
    assertEquals("chat 2026-10-08 0903", ExportFileNames.folderName("/", millis, ZoneId.of("America/Chicago")))
  }

  @Test
  fun `media name uses a safe extension from the original file name`() {
    assertEquals("12-1.jpeg", ExportFileNames.mediaFileName(12, 1, "Holiday.JPEG", "image/png", ::extensionFor))
    assertEquals("12-2.pdf", ExportFileNames.mediaFileName(12, 2, "dir/report.final.pdf", null, ::extensionFor))
  }

  @Test
  fun `media name falls back to the content type when the original extension is unsafe`() {
    assertEquals("5-1.jpg", ExportFileNames.mediaFileName(5, 1, "photo", "image/jpeg", ::extensionFor))
    assertEquals("5-1.jpg", ExportFileNames.mediaFileName(5, 1, null, "image/jpeg", ::extensionFor))
    assertEquals("5-1.jpg", ExportFileNames.mediaFileName(5, 1, ".jpeg", "image/jpeg", ::extensionFor))
    assertEquals("5-1.jpg", ExportFileNames.mediaFileName(5, 1, "x.j/pg", "image/jpeg", ::extensionFor))
    assertEquals("5-1.png", ExportFileNames.mediaFileName(5, 1, "evil.p‮gn", "image/png", ::extensionFor))
    assertEquals("5-1.png", ExportFileNames.mediaFileName(5, 1, "trailing.", "IMAGE/PNG; charset=binary", ::extensionFor))
    assertEquals("5-1.aac", ExportFileNames.mediaFileName(5, 1, "x.waytoolongext", "audio/aac", ::extensionFor))
  }

  @Test
  fun `media name falls back to bin`() {
    assertEquals("5-1.bin", ExportFileNames.mediaFileName(5, 1, null, null, ::extensionFor))
    assertEquals("5-1.bin", ExportFileNames.mediaFileName(5, 1, null, "application/unknown", ::extensionFor))
    assertEquals("5-1.bin", ExportFileNames.mediaFileName(5, 1, null, "application/x-weird", ::extensionFor))
    assertEquals("5-1.bin", ExportFileNames.mediaFileName(5, 1, null, "application/x-long", ::extensionFor))
    assertEquals("5-1.bin", ExportFileNames.mediaFileName(5, 1, "", "", ::extensionFor))
  }

  @Test
  fun `media names never contain path separators`() {
    val names = listOf("../../x.sh", "a/b\\c.d", "..", "x.%2e%2e")
    for (name in names) {
      val result = ExportFileNames.mediaFileName(1, 1, name, null, ::extensionFor)
      assertFalse(result, result.contains('/'))
      assertFalse(result, result.contains('\\'))
      assertTrue(result, Regex("^1-1\\.[a-z0-9]{1,8}$").matches(result))
    }
  }

  @Test
  fun `all chats folder is named after the app and the time`() {
    val millis = Instant.parse("2026-10-08T14:03:59Z").toEpochMilli()
    assertEquals("Wren export 2026-10-08 1403", ExportFileNames.allChatsFolderName(millis, ZoneOffset.UTC))
    assertEquals("Wren export 2026-10-08 0903", ExportFileNames.allChatsFolderName(millis, ZoneId.of("America/Chicago")))
  }

  @Test
  fun `unique names count up from two`() {
    val taken = HashSet<String>()
    assertEquals("Alice", ExportFileNames.uniqueName("Alice", taken))
    assertEquals("Alice (2)", ExportFileNames.uniqueName("Alice", taken))
    assertEquals("Alice (3)", ExportFileNames.uniqueName("Alice", taken))
    assertEquals("Bob", ExportFileNames.uniqueName("Bob", taken))
  }

  @Test
  fun `unique names ignore case`() {
    val taken = HashSet<String>()
    assertEquals("alice", ExportFileNames.uniqueName("alice", taken))
    assertEquals("ALICE (2)", ExportFileNames.uniqueName("ALICE", taken))
  }

  @Test
  fun `unique names skip a suffix another chat already uses`() {
    val taken = HashSet<String>()
    assertEquals("Alice (2)", ExportFileNames.uniqueName("Alice (2)", taken))
    assertEquals("Alice", ExportFileNames.uniqueName("Alice", taken))
    assertEquals("Alice (3)", ExportFileNames.uniqueName("Alice", taken))
  }
}
