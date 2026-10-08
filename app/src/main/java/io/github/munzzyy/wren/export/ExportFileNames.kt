// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ExportFileNames {

  const val MEDIA_FOLDER = "media"
  const val FALLBACK_NAME = "chat"
  const val MAX_NAME_LENGTH = 64
  const val ALL_CHATS_PREFIX = "Wren export"

  private const val FALLBACK_EXTENSION = "bin"
  private val SAFE_EXTENSION = Regex("^[a-z0-9]{1,8}$")
  private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
  private val FOLDER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm", Locale.US)

  fun sanitizeChatName(name: String?): String {
    if (name == null) return FALLBACK_NAME

    val cleaned = StringBuilder()
    var pendingSpace = false
    var i = 0
    while (i < name.length) {
      val codePoint = name.codePointAt(i)
      i += Character.charCount(codePoint)

      when {
        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> pendingSpace = cleaned.isNotEmpty()
        isDropped(codePoint) -> Unit
        else -> {
          if (pendingSpace) cleaned.append(' ')
          pendingSpace = false
          cleaned.appendCodePoint(codePoint)
        }
      }
    }

    val trimmed = cleaned.toString().trimStart('.', ' ')
    val capped = capCodePoints(trimmed, MAX_NAME_LENGTH).trimEnd('.', ' ')

    return capped.ifEmpty { FALLBACK_NAME }
  }

  fun folderName(chatName: String?, exportedAtMillis: Long, zone: ZoneId): String {
    val timestamp = FOLDER_TIMESTAMP.format(Instant.ofEpochMilli(exportedAtMillis).atZone(zone))
    return "${sanitizeChatName(chatName)} $timestamp"
  }

  fun allChatsFolderName(exportedAtMillis: Long, zone: ZoneId): String {
    return "$ALL_CHATS_PREFIX ${FOLDER_TIMESTAMP.format(Instant.ofEpochMilli(exportedAtMillis).atZone(zone))}"
  }

  /**
   * Returns [name], or [name] with " (2)", " (3)" and so on appended, whichever is not in [taken]
   * yet, and adds it to [taken]. Compared without case, since most storage providers ignore it.
   */
  fun uniqueName(name: String, taken: MutableSet<String>): String {
    var candidate = name
    var n = 2
    while (!taken.add(candidate.lowercase(Locale.ROOT))) {
      candidate = "$name ($n)"
      n++
    }
    return candidate
  }

  fun mediaFileName(
    messageId: Long,
    index: Int,
    originalFileName: String?,
    contentType: String?,
    extensionForMimeType: (String) -> String?
  ): String {
    return "$messageId-$index.${extension(originalFileName, contentType, extensionForMimeType)}"
  }

  fun extension(originalFileName: String?, contentType: String?, extensionForMimeType: (String) -> String?): String {
    val fromName = originalFileName
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.takeIf { it.lastIndexOf('.') > 0 }
      ?.substringAfterLast('.')
      ?.lowercase(Locale.US)

    if (fromName != null && SAFE_EXTENSION.matches(fromName)) {
      return fromName
    }

    val mimeType = contentType
      ?.substringBefore(';')
      ?.trim()
      ?.lowercase(Locale.US)
      ?.takeIf { it.isNotEmpty() }
      ?: return FALLBACK_EXTENSION

    val fromType = extensionForMimeType(mimeType)?.lowercase(Locale.US)
    return if (fromType != null && SAFE_EXTENSION.matches(fromType)) fromType else FALLBACK_EXTENSION
  }

  private fun isDropped(codePoint: Int): Boolean {
    if (codePoint < 0x80 && codePoint.toChar() in FORBIDDEN) return true

    return when (Character.getType(codePoint).toByte()) {
      Character.CONTROL,
      Character.FORMAT,
      Character.SURROGATE,
      Character.PRIVATE_USE,
      Character.UNASSIGNED -> true
      else -> false
    }
  }

  private fun capCodePoints(value: String, max: Int): String {
    if (value.codePointCount(0, value.length) <= max) return value
    return value.substring(0, value.offsetByCodePoints(0, max))
  }
}
