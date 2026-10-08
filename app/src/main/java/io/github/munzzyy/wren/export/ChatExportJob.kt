// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.service.NotificationController
import java.io.IOException
import java.time.ZoneId

/**
 * Writes one chat to a new folder under a user-picked Storage Access Framework tree, as HTML, text
 * or JSON, optionally with its media. The folder is plaintext on purpose: it is meant to be read
 * outside the app. With a passphrase the same folder goes into one encrypted .wrenx file instead.
 */
class ChatExportJob private constructor(
  private val threadId: Long,
  private val format: ChatExportFormat,
  private val includeMedia: Boolean,
  private val encrypted: Boolean,
  private val treeUri: String,
  private val releasePermission: Boolean,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY = "ChatExportJob"

    /** Shared with [AllChatsExportJob] so exports never write at the same time. */
    const val QUEUE = "ChatExportJob"

    private val TAG = Log.tag(ChatExportJob::class.java)

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_FORMAT = "format"
    private const val KEY_INCLUDE_MEDIA = "include_media"
    private const val KEY_ENCRYPTED = "encrypted"

    /**
     * Takes the persistable grant for [treeUri] and queues the export. If the app already held a
     * grant for that tree for something else (the backup folder, say), the job leaves it alone.
     *
     * [passphraseToken] comes from [ChatExportDialog] when the person asked for encryption. If the
     * passphrase behind it is gone, nothing is queued.
     */
    @WorkerThread
    @JvmStatic
    fun enqueue(context: Context, threadId: Long, format: ChatExportFormat, includeMedia: Boolean, treeUri: Uri, passphraseToken: String?) {
      ExportPassphraseHandoff.enqueue(context, passphraseToken) { passphrase ->
        ExportTreeGrants.take(context, treeUri) { releasePermission ->
          val job = ChatExportJob(
            threadId = threadId,
            format = format,
            includeMedia = includeMedia,
            encrypted = passphrase != null,
            treeUri = treeUri.toString(),
            releasePermission = releasePermission,
            parameters = Parameters.Builder()
              .setQueue(QUEUE)
              .setMaxAttempts(1)
              .setLifespan(Parameters.IMMORTAL)
              .build()
          )
          passphrase?.handTo(job.id)
          AppDependencies.jobManager.add(job)
        }
      }
    }
  }

  private var started = false

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_THREAD_ID, threadId)
      .putString(KEY_FORMAT, format.name)
      .putBoolean(KEY_INCLUDE_MEDIA, includeMedia)
      .putBoolean(KEY_ENCRYPTED, encrypted)
      .putString(ExportTreeGrants.KEY_TREE_URI, treeUri)
      .putBoolean(ExportTreeGrants.KEY_RELEASE_PERMISSION, releasePermission)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    started = true
    ExportTreeGrants.onStarted(treeUri)

    var notification: NotificationController? = null
    val passphrase = if (encrypted) ExportPassphrases.take(id) else null
    var archive: EncryptedArchive? = null
    var keepArchive = false

    try {
      if (encrypted && passphrase == null) {
        Log.w(TAG, "The passphrase for this encrypted export is gone, not exporting")
        ExportNotifications.postPassphraseLost(context)
        return Result.failure()
      }

      notification = ExportNotifications.startProgress(context, context.getString(R.string.ChatExportJob__exporting_chat), id)
      notification?.setIndeterminateProgress()

      val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
      if (root == null || !root.canWrite()) {
        throw IOException("Cannot write to the chosen folder")
      }

      val exportedAt = System.currentTimeMillis()
      val zone = ZoneId.systemDefault()
      val exporter = ChatExporter(context, format, includeMedia) { isCanceled }
      var folder: DocumentFile? = null

      if (passphrase != null) ExportSpool.deleteStale(context.cacheDir)

      exporter.export(
        threadId = threadId,
        destination = { chatName ->
          val folderName = ExportFileNames.folderName(chatName, exportedAt, zone)
          if (passphrase == null) {
            FolderDestination(context, root.createDirectory(folderName) ?: throw IOException("Could not create the export folder")).also { folder = it.folder }
          } else {
            val created = EncryptedArchive.create(context, root, ExportFileNames.encryptedChatFileName(exportedAt, zone), passphrase, exportedAt)
            archive = created
            passphrase.fill(0.toChar())
            created.chatFolder(folderName)
          }
        },
        onProgress = { written, total -> notification?.setProgress(total, written) }
      )

      val finishedArchive = archive
      if (finishedArchive != null) {
        finishedArchive.finish()
        keepArchive = true
        ExportNotifications.postFinished(
          context = context,
          folder = root,
          title = context.getString(R.string.ChatExportJob__export_finished),
          text = context.getString(R.string.ChatExportJob__saved_encrypted_as_s, finishedArchive.file.name.orEmpty())
        )
      } else {
        ExportNotifications.postFinished(
          context = context,
          folder = folder ?: throw IOException("The export folder is missing"),
          title = context.getString(R.string.ChatExportJob__export_finished),
          text = context.getString(R.string.ChatExportJob__tap_to_open_the_folder)
        )
      }
      return Result.success()
    } catch (e: ExportCanceledException) {
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
      passphrase?.fill(0.toChar())
      if (!keepArchive) archive?.closeAndDelete()
      notification?.close()
      ExportTreeGrants.releaseIfUnused(context, treeUri, id, releasePermission)
    }
  }

  override fun onFailure() {
    ExportPassphrases.discard(id)
    if (!started) {
      ExportTreeGrants.onDropped(context, treeUri, id, releasePermission)
    }
  }

  private fun postFailedNotification() {
    ExportNotifications.postFailed(
      context = context,
      title = context.getString(R.string.ChatExportJob__export_failed),
      text = context.getString(R.string.ChatExportJob__export_failed_body)
    )
  }

  class Factory : Job.Factory<ChatExportJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ChatExportJob {
      val data = JsonJobData.deserialize(serializedData)
      return ChatExportJob(
        threadId = data.getLong(KEY_THREAD_ID),
        format = ChatExportFormat.valueOf(data.getString(KEY_FORMAT)),
        includeMedia = data.getBoolean(KEY_INCLUDE_MEDIA),
        encrypted = data.getBooleanOrDefault(KEY_ENCRYPTED, false),
        treeUri = data.getString(ExportTreeGrants.KEY_TREE_URI),
        releasePermission = data.getBoolean(ExportTreeGrants.KEY_RELEASE_PERMISSION),
        parameters = parameters
      )
    }
  }
}
