// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.service.ExportedBroadcastReceiver

/**
 * Cancel button on the export progress notification. The job notices on its next message or
 * attachment chunk and deletes what it has written so far.
 */
class ChatExportCancelReceiver : ExportedBroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(ChatExportCancelReceiver::class.java)

    private const val ACTION_CANCEL = "io.github.munzzyy.wren.export.CANCEL"
    private const val EXTRA_JOB_ID = "job_id"

    fun pendingIntent(context: Context, jobId: String): PendingIntent {
      val intent = Intent(context, ChatExportCancelReceiver::class.java)
        .setAction(ACTION_CANCEL)
        .putExtra(EXTRA_JOB_ID, jobId)

      return PendingIntent.getBroadcast(context, jobId.hashCode(), intent, PendingIntentFlags.immutable() or PendingIntent.FLAG_UPDATE_CURRENT)
    }
  }

  override fun onReceiveUnlock(context: Context, intent: Intent) {
    if (intent.action != ACTION_CANCEL) return

    val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
    Log.i(TAG, "Canceling export $jobId")
    AppDependencies.jobManager.cancel(jobId)
  }
}
