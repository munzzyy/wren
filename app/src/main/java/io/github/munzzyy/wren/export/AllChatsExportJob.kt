// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.service.NotificationController
import java.io.IOException
import java.time.ZoneId

/**
 * Writes every chat on the phone into one new "Wren export <date>" folder, one subfolder per
 * chat, in the same formats as [ChatExportJob]. A chat that fails is counted and skipped; losing
 * the folder itself or a cancel stops the whole thing and removes what was written.
 */
class AllChatsExportJob private constructor(
  private val format: ChatExportFormat,
  private val includeMedia: Boolean,
  private val treeUri: String,
  private val releasePermission: Boolean,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY = "AllChatsExportJob"

    private val TAG = Log.tag(AllChatsExportJob::class.java)

    private const val KEY_FORMAT = "format"
    private const val KEY_INCLUDE_MEDIA = "include_media"

    private const val TITLE_INTERVAL_MS = 1000L

    @WorkerThread
    @JvmStatic
    fun enqueue(context: Context, format: ChatExportFormat, includeMedia: Boolean, treeUri: Uri) {
      ExportTreeGrants.take(context, treeUri) { releasePermission ->
        AppDependencies.jobManager.add(
          AllChatsExportJob(
            format = format,
            includeMedia = includeMedia,
            treeUri = treeUri.toString(),
            releasePermission = releasePermission,
            parameters = Parameters.Builder()
              .setQueue(ChatExportJob.QUEUE)
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
    var exportFolder: DocumentFile? = null
    var keepFolder = false

    try {
      notification = ExportNotifications.startProgress(context, context.getString(R.string.AllChatsExportJob__exporting_chats), id)
      notification?.setIndeterminateProgress()

      val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
      if (root == null || !root.canWrite()) {
        throw IOException("Cannot write to the chosen folder")
      }

      val threadIds = readThreadIds()
      val zone = ZoneId.systemDefault()
      val folder = root.createDirectory(ExportFileNames.allChatsFolderName(System.currentTimeMillis(), zone)) ?: throw IOException("Could not create the export folder")
      exportFolder = folder

      val exporter = ChatExporter(context, format, includeMedia) { isCanceled }
      val usedNames = HashSet<String>()
      val tally = AllChatsTally()
      var lastTitleAt = 0L

      threadIds.forEachIndexed { index, threadId ->
        if (isCanceled) throw ExportCanceledException()

        val now = System.currentTimeMillis()
        if (now - lastTitleAt >= TITLE_INTERVAL_MS) {
          lastTitleAt = now
          notification?.replaceTitle(context.getString(R.string.AllChatsExportJob__exporting_chat_d_of_d, index + 1, threadIds.size))
        }
        notification?.setProgress(threadIds.size.toLong(), index.toLong())

        if (exporter.messageCount(threadId) == 0) {
          tally.skipped++
          return@forEachIndexed
        }

        try {
          exporter.export(
            threadId = threadId,
            parent = folder,
            folderName = { chatName -> ExportFileNames.uniqueName(ExportFileNames.sanitizeChatName(chatName), usedNames) },
            onProgress = { _, _ -> }
          )
          tally.written++
        } catch (e: SecurityException) {
          throw e
        } catch (e: IOException) {
          Log.w(TAG, "Could not export one chat, moving on", e)
          tally.failed++
        } catch (e: RuntimeException) {
          Log.w(TAG, "Could not export one chat, moving on", e)
          tally.failed++
        }
      }

      notification?.setProgress(threadIds.size.toLong(), threadIds.size.toLong())

      when (tally.outcome()) {
        AllChatsTally.Outcome.WROTE_SOME -> {
          keepFolder = true
          ExportNotifications.postFinished(
            context = context,
            folder = folder,
            title = context.getString(R.string.ChatExportJob__export_finished),
            text = summary(tally)
          )
        }
        AllChatsTally.Outcome.NOTHING_TO_EXPORT -> {
          ExportNotifications.postMessage(
            context = context,
            title = context.getString(R.string.ChatExportJob__export_finished),
            text = context.getString(R.string.AllChatsExportJob__no_chats_with_messages)
          )
        }
        AllChatsTally.Outcome.ALL_FAILED -> {
          ExportNotifications.postFailed(
            context = context,
            title = context.getString(R.string.AllChatsExportJob__export_failed),
            text = context.getString(R.string.ChatExportJob__export_failed_body)
          )
        }
      }

      return if (keepFolder) Result.success() else Result.failure()
    } catch (e: ExportCanceledException) {
      Log.w(TAG, "All chats export canceled")
      return Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, "All chats export failed", e)
      postFailedNotification()
      return Result.failure()
    } catch (e: SecurityException) {
      Log.w(TAG, "Lost access to the export folder", e)
      postFailedNotification()
      return Result.failure()
    } finally {
      notification?.close()
      if (!keepFolder) {
        exportFolder?.let { if (!it.delete()) Log.w(TAG, "Could not delete the partial export") }
      }
      ExportTreeGrants.releaseIfUnused(context, treeUri, id, releasePermission)
    }
  }

  override fun onFailure() {
    if (!started) {
      ExportTreeGrants.onDropped(context, treeUri, id, releasePermission)
    }
  }

  private fun readThreadIds(): List<Long> {
    return SignalDatabase.rawDatabase
      .select(ThreadTable.ID)
      .from(ThreadTable.TABLE_NAME)
      .where("${ThreadTable.ACTIVE} = 1")
      .orderBy("${ThreadTable.DATE} DESC")
      .run()
      .readToList { it.requireLong(ThreadTable.ID) }
  }

  private fun summary(tally: AllChatsTally): String {
    val wrote = context.resources.getQuantityString(R.plurals.AllChatsExportJob__wrote_d_chats, tally.written, tally.written)
    return if (tally.skipped == 0 && tally.failed == 0) {
      wrote
    } else {
      "$wrote ${context.getString(R.string.AllChatsExportJob__d_skipped_d_failed, tally.skipped, tally.failed)}"
    }
  }

  private fun postFailedNotification() {
    ExportNotifications.postFailed(
      context = context,
      title = context.getString(R.string.AllChatsExportJob__export_failed),
      text = context.getString(R.string.ChatExportJob__export_failed_body)
    )
  }

  class Factory : Job.Factory<AllChatsExportJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AllChatsExportJob {
      val data = JsonJobData.deserialize(serializedData)
      return AllChatsExportJob(
        format = ChatExportFormat.valueOf(data.getString(KEY_FORMAT)),
        includeMedia = data.getBoolean(KEY_INCLUDE_MEDIA),
        treeUri = data.getString(ExportTreeGrants.KEY_TREE_URI),
        releasePermission = data.getBoolean(ExportTreeGrants.KEY_RELEASE_PERMISSION),
        parameters = parameters
      )
    }
  }
}

/** What happened to each chat in an all chats export. */
internal class AllChatsTally(
  var written: Int = 0,
  var skipped: Int = 0,
  var failed: Int = 0
) {
  enum class Outcome { WROTE_SOME, NOTHING_TO_EXPORT, ALL_FAILED }

  fun outcome(): Outcome {
    return when {
      written > 0 -> Outcome.WROTE_SOME
      failed > 0 -> Outcome.ALL_FAILED
      else -> Outcome.NOTHING_TO_EXPORT
    }
  }
}
