// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.v2.data.MessageDataFetcher
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobs.UnableToStartException
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.NotificationController
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.ZoneId

/**
 * Writes one chat to a new folder under a user-picked Storage Access Framework tree, as HTML, text
 * or JSON, optionally with its media. The output is plaintext on purpose: it is meant to be read
 * outside the app.
 */
class ChatExportJob private constructor(
  private val threadId: Long,
  private val format: ChatExportFormat,
  private val includeMedia: Boolean,
  private val treeUri: String,
  private val releasePermission: Boolean,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY = "ChatExportJob"

    private val TAG = Log.tag(ChatExportJob::class.java)

    private const val QUEUE = "ChatExportJob"
    private const val PAGE_SIZE = 500L

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_FORMAT = "format"
    private const val KEY_INCLUDE_MEDIA = "include_media"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_RELEASE_PERMISSION = "release_permission"

    private const val PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private val permissionLock = Any()
    private val notStarted = HashMap<String, Int>()

    /**
     * Takes the persistable grant for [treeUri] and queues the export. If the app already held a
     * grant for that tree for something else (the backup folder, say), the job leaves it alone.
     */
    @WorkerThread
    @JvmStatic
    fun enqueue(context: Context, threadId: Long, format: ChatExportFormat, includeMedia: Boolean, treeUri: Uri) {
      val key = treeUri.toString()

      synchronized(permissionLock) {
        val resolver = context.contentResolver
        val alreadyHeld = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
        resolver.takePersistableUriPermission(treeUri, PERMISSION_FLAGS)

        val heldForAnotherExport = (notStarted[key] ?: 0) > 0 || findExports(key, excludingId = null).isNotEmpty()
        notStarted[key] = (notStarted[key] ?: 0) + 1

        AppDependencies.jobManager.add(
          ChatExportJob(
            threadId = threadId,
            format = format,
            includeMedia = includeMedia,
            treeUri = key,
            releasePermission = !alreadyHeld || heldForAnotherExport,
            parameters = Parameters.Builder()
              .setQueue(QUEUE)
              .setMaxAttempts(1)
              .setLifespan(Parameters.IMMORTAL)
              .build()
          )
        )
      }
    }

    private fun findExports(treeUri: String, excludingId: String?): List<String> {
      return AppDependencies.jobManager
        .find { it.factoryKey == KEY && it.id != excludingId }
        .filter { JsonJobData.deserialize(it.serializedData).getString(KEY_TREE_URI) == treeUri }
        .map { it.id }
    }
  }

  private val mimeTypes: MimeTypeMap = MimeTypeMap.getSingleton()
  private var mediaFolder: DocumentFile? = null
  private var exportFolder: DocumentFile? = null

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_THREAD_ID, threadId)
      .putString(KEY_FORMAT, format.name)
      .putBoolean(KEY_INCLUDE_MEDIA, includeMedia)
      .putString(KEY_TREE_URI, treeUri)
      .putBoolean(KEY_RELEASE_PERMISSION, releasePermission)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    synchronized(permissionLock) {
      val waiting = notStarted[treeUri] ?: 0
      if (waiting <= 1) {
        notStarted.remove(treeUri)
      } else {
        notStarted[treeUri] = waiting - 1
      }
    }

    var notification: NotificationController? = null
    var succeeded = false

    try {
      notification = try {
        GenericForegroundService.startForegroundTask(
          context,
          context.getString(R.string.ChatExportJob__exporting_chat),
          NotificationChannels.getInstance().BACKUPS,
          R.drawable.ic_notification_backup
        )
      } catch (e: UnableToStartException) {
        Log.w(TAG, "Unable to start foreground service, continuing without it")
        null
      }
      notification?.setIndeterminateProgress()

      val folder = export(notification)
      succeeded = true
      postFinishedNotification(folder)
      return Result.success()
    } catch (e: CanceledException) {
      Log.w(TAG, "Chat export canceled")
      return Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, "Chat export failed", e)
      postFailedNotification()
      return Result.failure()
    } catch (e: SecurityException) {
      Log.w(TAG, "Lost access to the export folder", e)
      postFailedNotification()
      return Result.failure()
    } finally {
      notification?.close()
      if (!succeeded) {
        exportFolder?.let { if (!it.delete()) Log.w(TAG, "Could not delete the partial export") }
      }
      releasePermissionIfUnused()
    }
  }

  override fun onFailure() = Unit

  private fun export(notification: NotificationController?): DocumentFile {
    val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
    if (root == null || !root.canWrite()) {
      throw IOException("Cannot write to the chosen folder")
    }

    val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: throw IOException("No recipient for thread")
    val chatName = if (threadRecipient.isSelf) context.getString(R.string.note_to_self) else threadRecipient.getDisplayName(context)
    val exportedAt = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val total = SignalDatabase.messages.getMessageCountForThread(threadId)

    val folder = root.createDirectory(ExportFileNames.folderName(chatName, exportedAt, zone)) ?: throw IOException("Could not create the export folder")
    exportFolder = folder

    val chatFile = folder.createFile(format.mimeType, format.fileName) ?: throw IOException("Could not create ${format.fileName}")
    val mapper = MessageRecordMapper(context, ::exportAttachment)

    openOutput(chatFile).use { stream ->
      BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { out ->
        val writer = ChatExportWriter.create(format, out, zone)
        writer.writeHeader(ExportChat(name = chatName, isGroup = threadRecipient.isGroup, exportedAtMillis = exportedAt, messageCount = total, includesMedia = includeMedia))

        var offset = 0L
        var written = 0L
        while (true) {
          throwIfCanceled()
          val page = readPage(offset)
          if (page.isEmpty()) break

          val data = MessageDataFetcher.fetch(page, threadRecipient)
          for (record in MessageDataFetcher.updateModelsWithData(page, data)) {
            throwIfCanceled()
            writer.writeMessage(mapper.map(record, data.mentionsById[record.id].orEmpty()))
            written++
          }

          notification?.setProgress(maxOf(total.toLong(), written), written)
          offset += page.size
          if (page.size < PAGE_SIZE) break
        }

        writer.writeFooter()
      }
    }

    return folder
  }

  private fun readPage(offset: Long): List<MessageRecord> {
    return MessageTable.MmsReader(SignalDatabase.messages.getConversation(threadId, offset, PAGE_SIZE, dateReceiveOrderBy = "ASC")).use { it.toList() }
  }

  private fun exportAttachment(messageId: Long, index: Int, attachment: DatabaseAttachment): ExportAttachment {
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

    val folder = mediaFolder ?: requireNotNull(exportFolder).let { parent ->
      (parent.createDirectory(ExportFileNames.MEDIA_FOLDER) ?: throw IOException("Could not create the media folder")).also { mediaFolder = it }
    }

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
    if (isCanceled) throw CanceledException()
  }

  private fun releasePermissionIfUnused() {
    if (!releasePermission) return

    synchronized(permissionLock) {
      if ((notStarted[treeUri] ?: 0) > 0 || findExports(treeUri, excludingId = id).isNotEmpty()) {
        return
      }

      try {
        context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), PERMISSION_FLAGS)
      } catch (e: SecurityException) {
        Log.w(TAG, "Export folder grant was already gone", e)
      }
    }
  }

  private fun postFinishedNotification(folder: DocumentFile) {
    val documentUri = DocumentsContract.buildDocumentUri(folder.uri.authority, DocumentsContract.getDocumentId(folder.uri))
    val view = Intent(Intent.ACTION_VIEW)
      .setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
    val chooser = Intent.createChooser(view, context.getString(R.string.ChatExportJob__open_folder))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    postNotification(
      channel = NotificationChannels.getInstance().BACKUPS,
      title = context.getString(R.string.ChatExportJob__export_finished),
      text = context.getString(R.string.ChatExportJob__tap_to_open_the_folder),
      contentIntent = PendingIntent.getActivity(context, 0, chooser, PendingIntentFlags.immutable())
    )
  }

  private fun postFailedNotification() {
    postNotification(
      channel = NotificationChannels.getInstance().FAILURES,
      title = context.getString(R.string.ChatExportJob__export_failed),
      text = context.getString(R.string.ChatExportJob__export_failed_body),
      contentIntent = null
    )
  }

  private fun postNotification(channel: String, title: String, text: String, contentIntent: PendingIntent?) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Notification permission is not granted")
      return
    }

    val notification = NotificationCompat.Builder(context, channel)
      .setSmallIcon(R.drawable.ic_notification_backup)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(contentIntent)
      .setAutoCancel(true)
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.CHAT_EXPORT, notification)
  }

  private class CanceledException : Exception()

  class Factory : Job.Factory<ChatExportJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ChatExportJob {
      val data = JsonJobData.deserialize(serializedData)
      return ChatExportJob(
        threadId = data.getLong(KEY_THREAD_ID),
        format = ChatExportFormat.valueOf(data.getString(KEY_FORMAT)),
        includeMedia = data.getBoolean(KEY_INCLUDE_MEDIA),
        treeUri = data.getString(KEY_TREE_URI),
        releasePermission = data.getBoolean(KEY_RELEASE_PERMISSION),
        parameters = parameters
      )
    }
  }
}
