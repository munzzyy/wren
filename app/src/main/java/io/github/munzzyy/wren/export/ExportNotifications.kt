// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.jobs.UnableToStartException
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.NotificationController

internal object ExportNotifications {

  private val TAG = Log.tag(ExportNotifications::class.java)

  /** The foreground progress notification, with a Cancel action that cancels [jobId]. */
  fun startProgress(context: Context, title: String, jobId: String): NotificationController? {
    return try {
      GenericForegroundService.startForegroundTask(
        context = context,
        task = title,
        channelId = NotificationChannels.getInstance().BACKUPS,
        iconRes = R.drawable.ic_notification_backup,
        actionTitle = context.getString(android.R.string.cancel),
        actionIntent = ChatExportCancelReceiver.pendingIntent(context, jobId)
      )
    } catch (e: UnableToStartException) {
      Log.w(TAG, "Unable to start foreground service, continuing without it")
      null
    }
  }

  fun postFinished(context: Context, folder: DocumentFile, title: String, text: String) {
    val documentUri = DocumentsContract.buildDocumentUri(folder.uri.authority, DocumentsContract.getDocumentId(folder.uri))
    val view = Intent(Intent.ACTION_VIEW)
      .setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
    val chooser = Intent.createChooser(view, context.getString(R.string.ChatExportJob__open_folder))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    post(
      context = context,
      channel = NotificationChannels.getInstance().BACKUPS,
      title = title,
      text = text,
      contentIntent = PendingIntent.getActivity(context, 0, chooser, PendingIntentFlags.immutable())
    )
  }

  fun postMessage(context: Context, title: String, text: String) {
    post(
      context = context,
      channel = NotificationChannels.getInstance().BACKUPS,
      title = title,
      text = text,
      contentIntent = null
    )
  }

  fun postFailed(context: Context, title: String, text: String) {
    post(
      context = context,
      channel = NotificationChannels.getInstance().FAILURES,
      title = title,
      text = text,
      contentIntent = null
    )
  }

  private fun post(context: Context, channel: String, title: String, text: String, contentIntent: PendingIntent?) {
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
}
