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

    /** Shared with [AllChatsExportJob] so exports never write at the same time. */
    const val QUEUE = "ChatExportJob"

    private val TAG = Log.tag(ChatExportJob::class.java)

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_FORMAT = "format"
    private const val KEY_INCLUDE_MEDIA = "include_media"

    /**
     * Takes the persistable grant for [treeUri] and queues the export. If the app already held a
     * grant for that tree for something else (the backup folder, say), the job leaves it alone.
     */
    @WorkerThread
    @JvmStatic
    fun enqueue(context: Context, threadId: Long, format: ChatExportFormat, includeMedia: Boolean, treeUri: Uri) {
      ExportTreeGrants.take(context, treeUri) { releasePermission ->
        AppDependencies.jobManager.add(
          ChatExportJob(
            threadId = threadId,
            format = format,
            includeMedia = includeMedia,
            treeUri = treeUri.toString(),
            releasePermission = releasePermission,
            parameters = Parameters.Builder()
              .setQueue(QUEUE)
              .setMaxAttempts(1)
              .setLifespan(Parameters.IMMORTAL)
              .build()
          )
        )
      }
    }
  }

  private var started = false

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_THREAD_ID, threadId)
      .putString(KEY_FORMAT, format.name)
      .putBoolean(KEY_INCLUDE_MEDIA, includeMedia)
      .putString(ExportTreeGrants.KEY_TREE_URI, treeUri)
      .putBoolean(ExportTreeGrants.KEY_RELEASE_PERMISSION, releasePermission)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    started = true
    ExportTreeGrants.onStarted(treeUri)

    var notification: NotificationController? = null

    try {
      notification = ExportNotifications.startProgress(context, context.getString(R.string.ChatExportJob__exporting_chat), id)
      notification?.setIndeterminateProgress()

      val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
      if (root == null || !root.canWrite()) {
        throw IOException("Cannot write to the chosen folder")
      }

      val exporter = ChatExporter(context, format, includeMedia) { isCanceled }
      val folder = exporter.export(
        threadId = threadId,
        parent = root,
        folderName = { chatName -> ExportFileNames.folderName(chatName, System.currentTimeMillis(), ZoneId.systemDefault()) },
        onProgress = { written, total -> notification?.setProgress(total, written) }
      )

      ExportNotifications.postFinished(
        context = context,
        folder = folder,
        title = context.getString(R.string.ChatExportJob__export_finished),
        text = context.getString(R.string.ChatExportJob__tap_to_open_the_folder)
      )
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
      notification?.close()
      ExportTreeGrants.releaseIfUnused(context, treeUri, id, releasePermission)
    }
  }

  override fun onFailure() {
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
        treeUri = data.getString(ExportTreeGrants.KEY_TREE_URI),
        releasePermission = data.getBoolean(ExportTreeGrants.KEY_RELEASE_PERMISSION),
        parameters = parameters
      )
    }
  }
}
