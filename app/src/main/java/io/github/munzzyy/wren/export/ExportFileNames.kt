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
  const val ENCRYPTED_CHAT_PREFIX = "Wren chat"

  /** Types a browser or file manager only displays or plays. The first extension is the default. */
  val PASSIVE_TYPES: Map<String, List<String>> = mapOf(
    "image/jpeg" to listOf("jpg", "jpeg"),
    "image/jpg" to listOf("jpg", "jpeg"),
    "image/png" to listOf("png"),
    "image/gif" to listOf("gif"),
    "image/webp" to listOf("webp"),
    "image/heic" to listOf("heic"),
    "image/heif" to listOf("heif", "heic"),
    "image/avif" to listOf("avif"),
    "image/bmp" to listOf("bmp"),
    "video/mp4" to listOf("mp4", "m4v"),
    "video/3gpp" to listOf("3gp"),
    "video/webm" to listOf("webm"),
    "video/quicktime" to listOf("mov"),
    "audio/mp4" to listOf("m4a", "mp4"),
    "audio/x-m4a" to listOf("m4a"),
    "audio/aac" to listOf("aac", "m4a"),
    "audio/mpeg" to listOf("mp3"),
    "audio/ogg" to listOf("ogg", "oga", "opus"),
    "audio/opus" to listOf("opus"),
    "audio/wav" to listOf("wav"),
    "audio/x-wav" to listOf("wav"),
    "audio/flac" to listOf("flac"),
    "audio/amr" to listOf("amr"),
    "application/pdf" to listOf("pdf"),
    "text/plain" to listOf("txt"),
    "text/x-signal-plain" to listOf("txt"),
    "text/vcard" to listOf("vcf"),
    "text/x-vcard" to listOf("vcf")
  )

  private const val FALLBACK_EXTENSION = "bin"
  private const val PARTIAL_SUFFIX = ".partial"
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
   * The name of an encrypted single chat export, without the extension. It leaves the chat's name
   * out, since the file name is the one part of the export anyone can read.
   */
  fun encryptedChatFileName(exportedAtMillis: Long, zone: ZoneId): String {
    return "$ENCRYPTED_CHAT_PREFIX ${FOLDER_TIMESTAMP.format(Instant.ofEpochMilli(exportedAtMillis).atZone(zone))}"
  }

  /**
   * What a folder or archive is called while it is being written. Renamed to [name] at the end,
   * so whatever a kill or a wipe leaves behind is easy to spot as unfinished.
   */
  fun partialName(name: String): String = ".$name$PARTIAL_SUFFIX"

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

  /**
   * Names one attachment in media/. The extension always comes from [PASSIVE_TYPES], so a file
   * sent as an image can't land next to chat.html as .html, .svg or anything a browser would run.
   */
  fun mediaFileName(messageId: Long, index: Int, originalFileName: String?, contentType: String?): String {
    return "$messageId-$index.${extension(originalFileName, contentType)}"
  }

  /**
   * The original extension is kept only when it belongs to the same passive type. Any other type
   * becomes .bin, whatever the sender called it.
   */
  fun extension(originalFileName: String?, contentType: String?): String {
    val mimeType = contentType
      ?.substringBefore(';')
      ?.trim()
      ?.lowercase(Locale.US)
      ?.takeIf { it.isNotEmpty() }
      ?: return FALLBACK_EXTENSION

    val allowed = PASSIVE_TYPES[mimeType] ?: return FALLBACK_EXTENSION

    val fromName = originalFileName
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.takeIf { it.lastIndexOf('.') > 0 }
      ?.substringAfterLast('.')
      ?.lowercase(Locale.US)

    return if (fromName != null && fromName in allowed) fromName else allowed.first()
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
