// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.v2.data.MessageDataFetcher
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.ZoneId

internal class ExportCanceledException : Exception()

/**
 * Writes one chat into a new folder under a parent folder. Used by both the single chat and the
 * all chats export, and it removes its own folder again if anything goes wrong partway.
 */
internal class ChatExporter(
  private val context: Context,
  private val format: ChatExportFormat,
  private val includeMedia: Boolean,
  private val isCanceled: () -> Boolean
) {

  companion object {
    private val TAG = Log.tag(ChatExporter::class.java)
    private const val PAGE_SIZE = 500L
  }

  private val mimeTypes: MimeTypeMap = MimeTypeMap.getSingleton()

  fun messageCount(threadId: Long): Int = SignalDatabase.messages.getMessageCountForThread(threadId)

  /**
   * Exports [threadId] into a folder under [parent] named by [folderName], which gets the chat's
   * display name. Throws [ExportCanceledException] when [isCanceled] turns true.
   */
  @WorkerThread
  fun export(threadId: Long, parent: DocumentFile, folderName: (chatName: String) -> String, onProgress: (written: Long, total: Long) -> Unit): DocumentFile {
    val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: throw IOException("No recipient for thread")
    val chatName = if (threadRecipient.isSelf) context.getString(R.string.note_to_self) else threadRecipient.getDisplayName(context)
    val exportedAt = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val total = messageCount(threadId)

    val folder = parent.createDirectory(folderName(chatName)) ?: throw IOException("Could not create the export folder")
    var finished = false

    try {
      val chatFile = folder.createFile(format.mimeType, format.fileName) ?: throw IOException("Could not create ${format.fileName}")
      val media = MediaWriter(folder)
      val mapper = MessageRecordMapper(context, media::export)

      openOutput(chatFile).use { stream ->
        BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { out ->
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
        }
      }

      finished = true
      return folder
    } finally {
      if (!finished && !folder.delete()) {
        Log.w(TAG, "Could not delete the partial chat folder")
      }
    }
  }

  private fun readPage(threadId: Long, offset: Long): List<MessageRecord> {
    return MessageTable.MmsReader(SignalDatabase.messages.getConversation(threadId, offset, PAGE_SIZE, dateReceiveOrderBy = "ASC")).use { it.toList() }
  }

  private inner class MediaWriter(private val chatFolder: DocumentFile) {
    private var mediaFolder: DocumentFile? = null

    fun export(messageId: Long, index: Int, attachment: DatabaseAttachment): ExportAttachment {
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

      val folder = mediaFolder ?: (chatFolder.createDirectory(ExportFileNames.MEDIA_FOLDER) ?: throw IOException("Could not create the media folder")).also { mediaFolder = it }

      val target = folder.createFile(contentType, name)
      if (target == null) {
        Log.w(TAG, "Could not create a media file, marking it missing")
        return result.copy(missing = true)
      }

      return try {
        PartAuthority.getAttachmentStream(context, uri).use { input ->
          openOutput(target).use { output -> copy(input, output) }
        }
        result.copy(fileName = target.name ?: name)
      } catch (e: IOException) {
        Log.w(TAG, "Could not copy an attachment, marking it missing", e)
        target.delete()
        result.copy(missing = true)
      }
    }
  }

  private fun copy(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
      throwIfCanceled()
      val read = input.read(buffer)
      if (read < 0) break
      output.write(buffer, 0, read)
    }
  }

  private fun openOutput(file: DocumentFile): OutputStream {
    return context.contentResolver.openOutputStream(file.uri) ?: throw IOException("Could not open ${file.name}")
  }

  private fun throwIfCanceled() {
    if (isCanceled()) throw ExportCanceledException()
  }
}
