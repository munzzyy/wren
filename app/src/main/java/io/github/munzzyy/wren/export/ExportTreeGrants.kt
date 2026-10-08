// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.JsonJobData

/**
 * Holds the persistable grant on an export folder while any export job still needs it. When the
 * last one is done the grant goes back, unless the app already had it before the first export
 * asked (the backup folder, say).
 */
internal object ExportTreeGrants {

  const val KEY_TREE_URI = "tree_uri"
  const val KEY_RELEASE_PERMISSION = "release_permission"

  private val TAG = Log.tag(ExportTreeGrants::class.java)

  private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
  private val EXPORT_JOB_KEYS = setOf(ChatExportJob.KEY, AllChatsExportJob.KEY)

  private val lock = Any()
  private val waiting = WaitingExports()

  /**
   * Takes the grant for [treeUri] and calls [enqueue] with whether the new job should give the
   * grant back when it is done. [enqueue] runs under the lock so the job is counted before anyone
   * else looks.
   */
  @WorkerThread
  fun take(context: Context, treeUri: Uri, enqueue: (releasePermission: Boolean) -> Unit) {
    val key = treeUri.toString()

    synchronized(lock) {
      val resolver = context.contentResolver
      val alreadyHeld = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
      try {
        resolver.takePersistableUriPermission(treeUri, FLAGS)
      } catch (e: SecurityException) {
        Log.w(TAG, "The folder picker did not hand over a lasting grant", e)
        ExportNotifications.postFailed(context, context.getString(R.string.ChatExportJob__export_failed), context.getString(R.string.ChatExportJob__export_failed_body))
        return
      }

      val others = waiting.releaseFlags(key) + exportsUsing(key, excludingId = null)
      val release = WaitingExports.releaseWhenDone(alreadyHeld, others)
      waiting.add(key, release)
      enqueue(release)
    }
  }

  fun onStarted(treeUri: String) {
    synchronized(lock) {
      waiting.remove(treeUri)
    }
  }

  /** For a job that was canceled before it ever ran. */
  fun onDropped(context: Context, treeUri: String, jobId: String, releasePermission: Boolean) {
    synchronized(lock) {
      waiting.remove(treeUri)
    }
    releaseIfUnused(context, treeUri, jobId, releasePermission)
  }

  fun releaseIfUnused(context: Context, treeUri: String, jobId: String, releasePermission: Boolean) {
    if (!releasePermission) return

    synchronized(lock) {
      if (waiting.releaseFlags(treeUri).isNotEmpty() || exportsUsing(treeUri, excludingId = jobId).isNotEmpty()) {
        return
      }

      try {
        context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), FLAGS)
      } catch (e: SecurityException) {
        Log.w(TAG, "Export folder grant was already gone", e)
      }
    }
  }

  private fun exportsUsing(treeUri: String, excludingId: String?): List<Boolean> {
    return AppDependencies.jobManager
      .find { it.factoryKey in EXPORT_JOB_KEYS && it.id != excludingId }
      .map { JsonJobData.deserialize(it.serializedData) }
      .filter { it.getString(KEY_TREE_URI) == treeUri }
      .map { it.getBoolean(KEY_RELEASE_PERMISSION) }
  }
}

/** Export jobs that were queued in this process but have not started yet, per folder. */
internal class WaitingExports {

  private val byTree = HashMap<String, MutableList<Boolean>>()

  fun add(treeUri: String, releaseWhenDone: Boolean) {
    byTree.getOrPut(treeUri) { mutableListOf() }.add(releaseWhenDone)
  }

  fun remove(treeUri: String) {
    val flags = byTree[treeUri] ?: return
    flags.removeAt(flags.lastIndex)
    if (flags.isEmpty()) byTree.remove(treeUri)
  }

  fun releaseFlags(treeUri: String): List<Boolean> = byTree[treeUri].orEmpty().toList()

  companion object {
    /**
     * A new export follows whatever the exports already using the folder decided, so a second
     * export into the backup folder never gives that grant back.
     */
    fun releaseWhenDone(alreadyHeld: Boolean, otherExports: List<Boolean>): Boolean {
      return otherExports.firstOrNull() ?: !alreadyHeld
    }
  }
}
