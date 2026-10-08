// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.v2.data.MessageDataFetcher
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.BufferedWriter
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.time.ZoneId

internal class ExportCanceledException : Exception()

/**
 * Writes one chat into a destination: a new folder, or a folder inside an encrypted archive. Used
 * by both the single chat and the all chats export. A plain folder is removed again if anything
 * goes wrong partway.
 */
internal class ChatExporter(
  private val context: Context,
  private val format: ChatExportFormat,
  private val includeMedia: Boolean,
  private val isCanceled: () -> Boolean
) {

  companion object {
    private const val PAGE_SIZE = 500L
  }

  private val mimeTypes: MimeTypeMap = MimeTypeMap.getSingleton()

  fun messageCount(threadId: Long): Int = SignalDatabase.messages.getMessageCountForThread(threadId)

  /**
   * Exports [threadId] into whatever [destination] returns for the chat's display name. Throws
   * [ExportCanceledException] when [isCanceled] turns true.
   */
  @WorkerThread
  fun export(threadId: Long, destination: (chatName: String) -> ChatDestination, onProgress: (written: Long, total: Long) -> Unit) {
    val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: throw IOException("No recipient for thread")
    val chatName = if (threadRecipient.isSelf) context.getString(R.string.note_to_self) else threadRecipient.getDisplayName(context)
    val exportedAt = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val total = messageCount(threadId)

    val target = destination(chatName)
    var finished = false

    try {
      val mapper = MessageRecordMapper(context) { messageId, index, attachment -> exportMedia(target, messageId, index, attachment) }

      target.writeChatFile(format) { stream ->
        val out = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))
        val writer = ChatExportWriter.create(format, out, zone)
        writer.writeHeader(ExportChat(name = chatName, isGroup = threadRecipient.isGroup, exportedAtMillis = exportedAt, messageCount = total, includesMedia = includeMedia))

        var offset = 0L
        var written = 0L
        while (true) {
          throwIfCanceled()
          val page = readPage(threadId, offset)
          if (page.isEmpty()) break

          val data = MessageDataFetcher.fetch(page, threadRecipient)
          for (record in MessageDataFetcher.updateModelsWithData(page, data)) {
            throwIfCanceled()
            writer.writeMessage(mapper.map(record, data.mentionsById[record.id].orEmpty()))
            written++
          }

          onProgress(written, maxOf(total.toLong(), written))
          offset += page.size
          if (page.size < PAGE_SIZE) break
        }

        writer.writeFooter()
        out.flush()
      }

      finished = true
    } finally {
      if (!finished) target.discard()
    }
  }

  private fun readPage(threadId: Long, offset: Long): List<MessageRecord> {
    return MessageTable.MmsReader(SignalDatabase.messages.getConversation(threadId, offset, PAGE_SIZE, dateReceiveOrderBy = "ASC")).use { it.toList() }
  }

  private fun exportMedia(target: ChatDestination, messageId: Long, index: Int, attachment: DatabaseAttachment): ExportAttachment {
    val contentType = attachment.contentType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    val name = ExportFileNames.mediaFileName(messageId, index, attachment.fileName, contentType) { mimeTypes.getExtensionFromMimeType(it) }
    val uri = attachment.uri
    val available = attachment.hasData && uri != null

    val result = ExportAttachment(
      fileName = name,
      contentType = contentType,
      sizeBytes = attachment.size,
      voiceNote = attachment.voiceNote,
      sticker = attachment.isSticker,
      missing = !available
    )

    if (!includeMedia || uri == null || !available) {
      return result
    }

    val savedAs = target.writeMedia(name, contentType) { CancelableInputStream(PartAuthority.getAttachmentStream(context, uri)) }
    return if (savedAs == null) result.copy(missing = true) else result.copy(fileName = savedAs)
  }

  private fun throwIfCanceled() {
    if (isCanceled()) throw ExportCanceledException()
  }

  private inner class CancelableInputStream(input: InputStream) : FilterInputStream(input) {
    override fun read(): Int {
      throwIfCanceled()
      return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      throwIfCanceled()
      return super.read(b, off, len)
    }
  }
}
