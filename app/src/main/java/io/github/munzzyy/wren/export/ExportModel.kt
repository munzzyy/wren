// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

data class ExportChat(
  val name: String,
  val isGroup: Boolean,
  val exportedAtMillis: Long,
  val messageCount: Int,
  val includesMedia: Boolean = true
)

data class ExportMessage(
  val id: Long,
  val sentAtMillis: Long,
  val receivedAtMillis: Long,
  val sender: String,
  val outgoing: Boolean,
  val body: String,
  val system: Boolean = false,
  val remoteDeleted: Boolean = false,
  val viewOnce: Boolean = false,
  val expiresInMillis: Long = 0,
  val quote: ExportQuote? = null,
  val reactions: List<ExportReaction> = emptyList(),
  val attachments: List<ExportAttachment> = emptyList(),
  val linkPreviews: List<ExportLink> = emptyList()
)

data class ExportQuote(
  val author: String,
  val text: String
)

data class ExportReaction(
  val emoji: String,
  val author: String
)

/**
 * [fileName] is the name inside the export's media/ folder. [missing] means no file was written
 * there, because the attachment was never downloaded or could not be copied.
 */
data class ExportAttachment(
  val fileName: String,
  val contentType: String,
  val sizeBytes: Long,
  val voiceNote: Boolean = false,
  val sticker: Boolean = false,
  val missing: Boolean = false
)

data class ExportLink(
  val title: String,
  val url: String
)
