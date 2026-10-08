// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R

object ChatExportDialog {

  fun show(
    context: Context,
    @StringRes title: Int = R.string.ConversationSettingsFragment__export_chat,
    onConfirm: (format: ChatExportFormat, includeMedia: Boolean) -> Unit
  ) {
    MaterialAlertDialogBuilder(context)
      .setTitle(title)
      .setView(R.layout.chat_export_dialog)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.ChatExportDialog__choose_folder) { dialog, _ ->
        val alert = dialog as AlertDialog
        val format = when (alert.findViewById<RadioGroup>(R.id.chat_export_format)?.checkedRadioButtonId) {
          R.id.chat_export_format_text -> ChatExportFormat.TEXT
          R.id.chat_export_format_json -> ChatExportFormat.JSON
          else -> ChatExportFormat.HTML
        }
        val includeMedia = alert.findViewById<CheckBox>(R.id.chat_export_include_media)?.isChecked ?: true
        onConfirm(format, includeMedia)
      }
      .show()
  }
}
