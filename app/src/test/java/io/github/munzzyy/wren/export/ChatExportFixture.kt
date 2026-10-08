// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import java.time.Instant
import java.time.ZoneOffset

object ChatExportFixture {

  val zone: ZoneOffset = ZoneOffset.UTC

  private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

  val chat = ExportChat(
    name = "Alice",
    isGroup = false,
    exportedAtMillis = at("2026-10-08T14:03:00Z"),
    messageCount = 7
  )

  val messages = listOf(
    ExportMessage(
      id = 1,
      sentAtMillis = at("2026-10-08T13:00:00Z"),
      receivedAtMillis = at("2026-10-08T13:00:00Z"),
      sender = "You",
      outgoing = true,
      body = "Hi there\nsecond line",
      expiresInMillis = 86_400_000
    ),
    ExportMessage(
      id = 2,
      sentAtMillis = at("2026-10-08T13:01:00Z"),
      receivedAtMillis = at("2026-10-08T13:01:05Z"),
      sender = "Alice",
      outgoing = false,
      body = "Look at this",
      quote = ExportQuote(author = "You", text = "Hi there"),
      reactions = listOf(ExportReaction("👍", "You"), ExportReaction("❤️", "Alice")),
      attachments = listOf(ExportAttachment(fileName = "2-1.jpg", contentType = "image/jpeg", sizeBytes = 2048))
    ),
    ExportMessage(
      id = 3,
      sentAtMillis = at("2026-10-08T13:02:00Z"),
      receivedAtMillis = at("2026-10-08T13:02:00Z"),
      sender = "Alice",
      outgoing = false,
      body = "Alice set the disappearing message timer to 1 day.",
      system = true
    ),
    ExportMessage(
      id = 4,
      sentAtMillis = at("2026-10-08T13:03:00Z"),
      receivedAtMillis = at("2026-10-08T13:03:00Z"),
      sender = "Alice",
      outgoing = false,
      body = "",
      remoteDeleted = true
    ),
    ExportMessage(
      id = 5,
      sentAtMillis = at("2026-10-08T13:04:00Z"),
      receivedAtMillis = at("2026-10-08T13:04:00Z"),
      sender = "Alice",
      outgoing = false,
      body = "",
      viewOnce = true
    ),
    ExportMessage(
      id = 6,
      sentAtMillis = at("2026-10-08T13:05:00Z"),
      receivedAtMillis = at("2026-10-08T13:05:00Z"),
      sender = "You",
      outgoing = true,
      body = "https://example.org/post",
      linkPreviews = listOf(ExportLink(title = "Example post", url = "https://example.org/post"))
    ),
    ExportMessage(
      id = 7,
      sentAtMillis = at("2026-10-08T13:06:00Z"),
      receivedAtMillis = at("2026-10-08T13:06:00Z"),
      sender = "Alice",
      outgoing = false,
      body = "",
      attachments = listOf(ExportAttachment(fileName = "7-1.m4a", contentType = "audio/mp4", sizeBytes = 5_000_000, voiceNote = true, missing = true))
    )
  )

  fun render(format: ChatExportFormat, chat: ExportChat = this.chat, messages: List<ExportMessage> = this.messages): String {
    val out = StringBuilder()
    val writer = ChatExportWriter.create(format, out, zone)
    writer.writeHeader(chat)
    messages.forEach(writer::writeMessage)
    writer.writeFooter()
    return out.toString()
  }
}
