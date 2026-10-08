// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.thoughtcrime.securesms.R

object ChatExportDialog {

  /**
   * Shows the export options. [onConfirm] gets a passphrase token when the person chose to
   * encrypt; the passphrase itself stays in [ExportPassphrases] for the job to take.
   */
  fun show(
    context: Context,
    @StringRes title: Int = R.string.ConversationSettingsFragment__export_chat,
    onConfirm: (format: ChatExportFormat, includeMedia: Boolean, passphraseToken: String?) -> Unit
  ) {
    val view = LayoutInflater.from(context).inflate(R.layout.chat_export_dialog, null)
    val formatGroup = view.findViewById<RadioGroup>(R.id.chat_export_format)
    val includeMedia = view.findViewById<CheckBox>(R.id.chat_export_include_media)
    val encrypt = view.findViewById<CheckBox>(R.id.chat_export_encrypt)
    val passphraseGroup = view.findViewById<View>(R.id.chat_export_passphrase_group)
    val passphraseLayout = view.findViewById<TextInputLayout>(R.id.chat_export_passphrase_layout)
    val repeatLayout = view.findViewById<TextInputLayout>(R.id.chat_export_repeat_passphrase_layout)
    val passphraseInput = view.findViewById<EditText>(R.id.chat_export_passphrase)
    val repeatInput = view.findViewById<EditText>(R.id.chat_export_repeat_passphrase)
    val warning = view.findViewById<TextView>(R.id.chat_export_warning)

    val dialog = MaterialAlertDialogBuilder(context)
      .setTitle(title)
      .setView(view)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.ChatExportDialog__choose_folder, null)
      .create()

    encrypt.setOnCheckedChangeListener { _, checked ->
      passphraseGroup.isVisible = checked
      warning.setText(if (checked) R.string.ChatExportDialog__warning_encrypted else R.string.ChatExportDialog__warning)
      if (checked) {
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        passphraseInput.requestFocus()
      }
    }
    passphraseInput.doAfterTextChanged { passphraseLayout.error = null }
    repeatInput.doAfterTextChanged { repeatLayout.error = null }

    dialog.setOnShowListener {
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
        val format = when (formatGroup.checkedRadioButtonId) {
          R.id.chat_export_format_text -> ChatExportFormat.TEXT
          R.id.chat_export_format_json -> ChatExportFormat.JSON
          else -> ChatExportFormat.HTML
        }

        var token: String? = null
        if (encrypt.isChecked) {
          val passphrase = enteredPassphrase(passphraseInput)
          val repeated = enteredPassphrase(repeatInput)
          val problem = ExportPassphraseRules.check(passphrase, repeated)
          repeated.fill(0.toChar())

          when (problem) {
            ExportPassphraseRules.Problem.TOO_SHORT -> {
              passphrase.fill(0.toChar())
              passphraseLayout.error = context.getString(R.string.ChatExportDialog__passphrase_too_short, ExportPassphraseRules.MIN_LENGTH)
              return@setOnClickListener
            }
            ExportPassphraseRules.Problem.MISMATCH -> {
              passphrase.fill(0.toChar())
              repeatLayout.error = context.getString(R.string.ChatExportDialog__passphrases_dont_match)
              return@setOnClickListener
            }
            null -> token = ExportPassphrases.stash(passphrase)
          }
        }

        dialog.dismiss()
        onConfirm(format, includeMedia.isChecked, token)
      }
    }

    dialog.setOnDismissListener {
      passphraseInput.text?.clear()
      repeatInput.text?.clear()
    }

    dialog.show()
  }

  private fun enteredPassphrase(input: EditText): CharArray {
    val text = input.text ?: return CharArray(0)
    val passphrase = CharArray(text.length)
    text.getChars(0, text.length, passphrase, 0)
    return passphrase
  }
}

object ExportPassphraseRules {

  /** Counted in Unicode code points, so an emoji is one character, as the person sees it. */
  const val MIN_LENGTH = 8

  enum class Problem { TOO_SHORT, MISMATCH }

  fun check(passphrase: CharArray, repeated: CharArray): Problem? {
    val length = Character.codePointCount(passphrase, 0, passphrase.size)
    return when {
      length < MIN_LENGTH || passphrase.all { Character.isWhitespace(it) } -> Problem.TOO_SHORT
      !passphrase.contentEquals(repeated) -> Problem.MISMATCH
      else -> null
    }
  }
}
