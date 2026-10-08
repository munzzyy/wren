// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Streams a chat out one message at a time: [writeHeader], then [writeMessage] for each message
 * in order, then [writeFooter]. Nothing is buffered beyond what [out] buffers itself.
 */
abstract class ChatExportWriter(protected val out: Appendable) {

  abstract fun writeHeader(chat: ExportChat)

  abstract fun writeMessage(message: ExportMessage)

  abstract fun writeFooter()

  companion object {
    @JvmStatic
    fun create(format: ChatExportFormat, out: Appendable, zone: ZoneId): ChatExportWriter {
      return when (format) {
        ChatExportFormat.HTML -> HtmlChatExportWriter(out, zone)
        ChatExportFormat.TEXT -> TextChatExportWriter(out, zone)
        ChatExportFormat.JSON -> JsonChatExportWriter(out)
      }
    }

    private val LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    internal fun localTime(millis: Long, zone: ZoneId): String {
      return LOCAL_TIME.format(Instant.ofEpochMilli(millis).atZone(zone))
    }

    internal fun zoneLabel(zone: ZoneId): String {
      return if (zone.normalized() == ZoneOffset.UTC) "UTC" else zone.id
    }

    internal fun isoTime(millis: Long): String {
      return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
    }

    internal fun timerLabel(millis: Long): String {
      val seconds = millis / 1000
      val (amount, unit) = when {
        seconds >= WEEK && seconds % WEEK == 0L -> seconds / WEEK to "week"
        seconds >= DAY && seconds % DAY == 0L -> seconds / DAY to "day"
        seconds >= HOUR && seconds % HOUR == 0L -> seconds / HOUR to "hour"
        seconds >= MINUTE && seconds % MINUTE == 0L -> seconds / MINUTE to "minute"
        else -> seconds to "second"
      }
      return if (amount == 1L) "1 $unit" else "$amount ${unit}s"
    }

    internal fun sizeLabel(bytes: Long): String {
      return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
      }
    }

    internal fun isWebUrl(url: String): Boolean {
      val lower = url.lowercase(Locale.US)
      return lower.startsWith("http://") || lower.startsWith("https://")
    }

    internal fun mediaPath(attachment: ExportAttachment): String {
      return "${ExportFileNames.MEDIA_FOLDER}/${attachment.fileName}"
    }

    /**
     * C0 controls other than tab and newline, DEL, C1, and the bidi marks, embeddings, overrides
     * and isolates. In a name they can reorder or hide what is shown around them.
     */
    internal fun isUnsafeControl(c: Char): Boolean {
      val code = c.code
      return (code < 0x20 && c != '\t' && c != '\n') ||
        code in 0x7F..0x9F ||
        code == 0x061C ||
        code == 0x200E ||
        code == 0x200F ||
        code in 0x202A..0x202E ||
        code in 0x2066..0x2069
    }

    internal fun replaceControls(value: String): String {
      if (value.none(::isUnsafeControl)) return value
      return buildString(value.length) {
        for (c in value) append(if (isUnsafeControl(c)) '\uFFFD' else c)
      }
    }

    internal fun stripControls(value: String): String {
      if (value.none(::isUnsafeControl)) return value
      return value.filterNot(::isUnsafeControl)
    }

    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
  }
}

class HtmlChatExportWriter(out: Appendable, private val zone: ZoneId) : ChatExportWriter(out) {

  private var includesMedia = true

  override fun writeHeader(chat: ExportChat) {
    includesMedia = chat.includesMedia
    out.append("<!DOCTYPE html>\n")
    out.append("<html lang=\"en\">\n<head>\n")
    out.append("<meta charset=\"utf-8\">\n")
    out.append("<meta http-equiv=\"Content-Security-Policy\" content=\"script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'\">\n")
    out.append("<meta name=\"referrer\" content=\"no-referrer\">\n")
    out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
    out.append("<title>").append(name(chat.name)).append("</title>\n")
    out.append("<style>\n").append(CSS).append("</style>\n")
    out.append("</head>\n<body>\n")
    out.append("<header>\n<h1>").append(name(chat.name)).append("</h1>\n")
    out.append("<p class=\"meta\">Exported ").append(escape(localTime(chat.exportedAtMillis, zone)))
      .append(" &middot; ").append(chat.messageCount.toString()).append(if (chat.messageCount == 1) " message" else " messages")
    if (!chat.includesMedia) {
      out.append(" &middot; media not included")
    }
    out.append("</p>\n</header>\n<main>\n")
  }

  override fun writeMessage(message: ExportMessage) {
    val time = escape(localTime(message.sentAtMillis, zone))

    if (message.system) {
      out.append("<div class=\"system\" id=\"m").append(message.id.toString()).append("\">")
        .append(escape(message.body))
        .append(" <span class=\"time\">").append(time).append("</span></div>\n")
      return
    }

    out.append("<div class=\"msg ").append(if (message.outgoing) "out" else "in").append("\" id=\"m").append(message.id.toString()).append("\">\n")
    out.append("<div class=\"bubble\">\n")
    out.append("<div class=\"sender\">").append(name(message.sender)).append("</div>\n")

    when {
      message.remoteDeleted -> out.append("<div class=\"placeholder\">This message was deleted.</div>\n")
      message.viewOnce -> out.append("<div class=\"placeholder\">View-once media</div>\n")
      else -> writeContent(message)
    }

    if (message.reactions.isNotEmpty()) {
      out.append("<div class=\"reactions\">")
      for (reaction in message.reactions) {
        out.append("<span title=\"").append(name(reaction.author)).append("\">").append(escape(reaction.emoji)).append("</span>")
      }
      out.append("</div>\n")
    }

    out.append("<div class=\"time\">").append(time)
    if (message.expiresInMillis > 0) {
      out.append(" <span class=\"timer\">&#9201; disappears after ").append(escape(timerLabel(message.expiresInMillis))).append("</span>")
    }
    out.append("</div>\n</div>\n</div>\n")
  }

  private fun writeContent(message: ExportMessage) {
    message.quote?.let { quote ->
      out.append("<blockquote class=\"quote\"><div class=\"sender\">").append(name(quote.author)).append("</div>")
        .append("<div>").append(escape(quote.text)).append("</div></blockquote>\n")
    }

    for (attachment in message.attachments) {
      writeAttachment(attachment)
    }

    for (link in message.linkPreviews) {
      out.append("<div class=\"link\">")
      if (isWebUrl(link.url)) {
        out.append("<a href=\"").append(escape(link.url)).append("\" rel=\"noopener noreferrer\">")
          .append(escape(link.title.ifEmpty { link.url })).append("</a>")
      } else {
        out.append("<span>").append(escape(link.title.ifEmpty { link.url })).append("</span>")
      }
      out.append("<div class=\"url\">").append(escape(link.url)).append("</div></div>\n")
    }

    if (message.body.isNotEmpty()) {
      out.append("<div class=\"body\">").append(escape(message.body)).append("</div>\n")
    }
  }

  private fun writeAttachment(attachment: ExportAttachment) {
    val label = escape("${stripControls(attachment.fileName)} (${stripControls(attachment.contentType)}, ${sizeLabel(attachment.sizeBytes)})")

    if (attachment.missing || !includesMedia) {
      val reason = if (includesMedia) "not downloaded" else "not included"
      out.append("<div class=\"attachment missing\">").append(label).append(" &middot; ").append(reason).append("</div>\n")
      return
    }

    val src = escape(mediaPath(attachment))
    val type = attachment.contentType.lowercase(Locale.US)
    out.append("<div class=\"attachment\">")
    when {
      type.startsWith("image/") -> {
        out.append("<a href=\"").append(src).append("\"><img class=\"").append(if (attachment.sticker) "sticker" else "image")
          .append("\" src=\"").append(src).append("\" alt=\"").append(name(attachment.fileName)).append("\" loading=\"lazy\"></a>")
      }
      type.startsWith("audio/") -> {
        out.append("<audio controls preload=\"none\" src=\"").append(src).append("\"></audio>")
        out.append("<div><a href=\"").append(src).append("\">").append(label).append("</a></div>")
      }
      type.startsWith("video/") -> {
        out.append("<video controls preload=\"none\" src=\"").append(src).append("\"></video>")
        out.append("<div><a href=\"").append(src).append("\">").append(label).append("</a></div>")
      }
      else -> out.append("<a href=\"").append(src).append("\">").append(label).append("</a>")
    }
    out.append("</div>\n")
  }

  override fun writeFooter() {
    out.append("</main>\n</body>\n</html>\n")
  }

  private fun name(value: String): String = escape(stripControls(value))

  companion object {
    @JvmStatic
    fun escape(value: String): String {
      val builder = StringBuilder(value.length + 16)
      for (c in value) {
        when (c) {
          '&' -> builder.append("&amp;")
          '<' -> builder.append("&lt;")
          '>' -> builder.append("&gt;")
          '"' -> builder.append("&quot;")
          '\'' -> builder.append("&#39;")
          else -> builder.append(c)
        }
      }
      return builder.toString()
    }

    private val CSS = """
      :root { color-scheme: light dark; --bg: #ffffff; --fg: #1b1b1d; --muted: #6b6b70; --in: #e9e9eb; --out: #2c6bed; --out-fg: #ffffff; --line: #c6c6cb; }
      @media (prefers-color-scheme: dark) { :root { --bg: #121212; --fg: #e9e9eb; --muted: #9a9aa0; --in: #2a2a2d; --out: #2c58c3; --line: #4a4a4f; } }
      body { margin: 0; background: var(--bg); color: var(--fg); font: 15px/1.4 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
      header { padding: 16px; border-bottom: 1px solid var(--line); }
      h1 { margin: 0 0 4px; font-size: 20px; }
      main { max-width: 760px; margin: 0 auto; padding: 12px 16px 32px; }
      .meta, .time, .url, .system { color: var(--muted); font-size: 12px; }
      .msg { display: flex; margin: 6px 0; }
      .msg.out { justify-content: flex-end; }
      .bubble { max-width: 75%; padding: 8px 12px; border-radius: 16px; background: var(--in); overflow-wrap: anywhere; }
      .msg.out .bubble { background: var(--out); color: var(--out-fg); }
      .msg.out .time, .msg.out .url, .msg.out .sender { color: inherit; opacity: 0.8; }
      .sender { font-weight: 600; font-size: 13px; margin-bottom: 2px; }
      .body { white-space: pre-wrap; }
      .system { text-align: center; margin: 12px 0; white-space: pre-wrap; }
      .placeholder { font-style: italic; opacity: 0.8; }
      .quote { margin: 4px 0 6px; padding: 4px 8px; border-left: 3px solid currentColor; border-radius: 4px; background: rgba(127, 127, 127, 0.15); white-space: pre-wrap; }
      .reactions { font-size: 13px; margin-top: 4px; }
      .reactions span { margin-right: 4px; }
      .attachment { margin: 4px 0; }
      .attachment.missing { font-style: italic; opacity: 0.8; }
      .image { max-width: 100%; max-height: 360px; border-radius: 8px; display: block; }
      .sticker { width: 128px; height: 128px; object-fit: contain; display: block; }
      audio, video { max-width: 100%; display: block; }
      a { color: inherit; }
      .link { margin: 4px 0; padding-left: 8px; border-left: 3px solid var(--line); }
      .timer { white-space: nowrap; }

    """.trimIndent()
  }
}

class TextChatExportWriter(out: Appendable, private val zone: ZoneId) : ChatExportWriter(out) {

  private var includesMedia = true

  override fun writeHeader(chat: ExportChat) {
    includesMedia = chat.includesMedia
    out.append("Chat: ").append(oneLine(chat.name)).append('\n')
    out.append("Type: ").append(if (chat.isGroup) "group" else "direct").append('\n')
    out.append("Exported: ").append(localTime(chat.exportedAtMillis, zone)).append(" (").append(zoneLabel(zone)).append(")\n")
    out.append("Messages: ").append(chat.messageCount.toString()).append('\n')
    if (!chat.includesMedia) {
      out.append("Media: not included\n")
    }
    out.append('\n')
  }

  override fun writeMessage(message: ExportMessage) {
    out.append('[').append(localTime(message.sentAtMillis, zone)).append("] ")

    if (message.system) {
      out.append("* ")
      appendIndented(message.body)
      out.append("\n\n")
      return
    }

    out.append(oneLine(message.sender)).append(':')
    when {
      message.remoteDeleted -> out.append(" [deleted]")
      message.viewOnce -> out.append(" [view-once media]")
      message.body.isNotEmpty() -> {
        out.append(' ')
        appendIndented(message.body)
      }
    }
    out.append('\n')

    if (!message.remoteDeleted && !message.viewOnce) {
      message.quote?.let { quote ->
        out.append("  > ").append(oneLine(quote.author)).append(": ")
        appendIndented(quote.text, "  > ")
        out.append('\n')
      }

      for (attachment in message.attachments) {
        out.append("  Attachment: ")
        if (attachment.missing || !includesMedia) {
          out.append(oneLine(attachment.fileName)).append(" (").append(if (includesMedia) "not downloaded" else "not included").append(")")
        } else {
          out.append(oneLine(mediaPath(attachment)))
        }
        out.append(" [").append(oneLine(attachment.contentType)).append(", ").append(sizeLabel(attachment.sizeBytes)).append("]\n")
      }

      for (link in message.linkPreviews) {
        out.append("  Link: ")
        if (link.title.isNotEmpty()) {
          out.append(oneLine(link.title)).append(' ')
        }
        out.append('<').append(oneLine(link.url)).append(">\n")
      }
    }

    if (message.reactions.isNotEmpty()) {
      out.append("  Reactions: ")
      message.reactions.forEachIndexed { index, reaction ->
        if (index > 0) out.append(", ")
        out.append(oneLine(reaction.emoji)).append(' ').append(oneLine(reaction.author))
      }
      out.append('\n')
    }

    if (message.expiresInMillis > 0) {
      out.append("  Disappears after ").append(timerLabel(message.expiresInMillis)).append('\n')
    }

    out.append('\n')
  }

  override fun writeFooter() = Unit

  private fun appendIndented(text: String, prefix: String = "  ") {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    lines.forEachIndexed { index, line ->
      if (index > 0) out.append('\n').append(prefix)
      out.append(replaceControls(line))
    }
  }

  private fun oneLine(value: String): String {
    return replaceControls(value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' '))
  }
}

class JsonChatExportWriter(out: Appendable) : ChatExportWriter(out) {

  private var first = true
  private var includesMedia = true

  override fun writeHeader(chat: ExportChat) {
    includesMedia = chat.includesMedia
    out.append("{\n\"format\":\"wren-chat-export\",\n\"version\":1,\n\"chat\":{")
    field("name", stripControls(chat.name), first = true)
    field("isGroup", chat.isGroup)
    field("messageCount", chat.messageCount.toLong())
    field("includesMedia", chat.includesMedia)
    timeFields("exportedAt", chat.exportedAtMillis)
    out.append("},\n\"messages\":[")
  }

  override fun writeMessage(message: ExportMessage) {
    out.append(if (first) "\n" else ",\n")
    first = false

    out.append('{')
    field("id", message.id, first = true)
    timeFields("sentAt", message.sentAtMillis)
    timeFields("receivedAt", message.receivedAtMillis)
    field("sender", stripControls(message.sender))
    field("outgoing", message.outgoing)
    field("system", message.system)
    field("remoteDeleted", message.remoteDeleted)
    field("viewOnce", message.viewOnce)
    field("expiresInMillis", message.expiresInMillis)
    field("body", message.body)

    out.append(",\"quote\":")
    val quote = message.quote
    if (quote == null) {
      out.append("null")
    } else {
      out.append('{')
      field("author", stripControls(quote.author), first = true)
      field("text", quote.text)
      out.append('}')
    }

    array("reactions", message.reactions) {
      field("emoji", it.emoji, first = true)
      field("author", stripControls(it.author))
    }

    array("attachments", message.attachments) {
      field("fileName", stripControls(it.fileName), first = true)
      field("path", if (it.missing || !includesMedia) null else mediaPath(it))
      field("contentType", it.contentType)
      field("sizeBytes", it.sizeBytes)
      field("voiceNote", it.voiceNote)
      field("sticker", it.sticker)
      field("missing", it.missing)
    }

    array("linkPreviews", message.linkPreviews) {
      field("title", it.title, first = true)
      field("url", it.url)
    }

    out.append('}')
  }

  override fun writeFooter() {
    out.append(if (first) "]\n}\n" else "\n]\n}\n")
  }

  private fun <T> array(name: String, items: List<T>, writeItem: (T) -> Unit) {
    out.append(",\"").append(name).append("\":[")
    items.forEachIndexed { index, item ->
      if (index > 0) out.append(',')
      out.append('{')
      writeItem(item)
      out.append('}')
    }
    out.append(']')
  }

  private fun timeFields(name: String, millis: Long) {
    field(name, millis)
    field("${name}Iso", isoTime(millis))
  }

  private fun key(name: String, first: Boolean) {
    if (!first) out.append(',')
    out.append('"').append(name).append("\":")
  }

  private fun field(name: String, value: String?, first: Boolean = false) {
    key(name, first)
    out.append(if (value == null) "null" else JsonPrimitive(value).toString())
  }

  private fun field(name: String, value: Long, first: Boolean = false) {
    key(name, first)
    out.append(value.toString())
  }

  private fun field(name: String, value: Boolean, first: Boolean = false) {
    key(name, first)
    out.append(value.toString())
  }
}
