// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.signal.core.util.ServiceUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException

class DuressPassphraseDialogFragment : DialogFragment() {

  companion object {
    private val TAG = Log.tag(DuressPassphraseDialogFragment::class.java)

    private const val KEY_MODE = "mode"

    const val MODE_SET = 0
    const val MODE_CLEAR = 1

    @JvmStatic
    fun newInstance(mode: Int): DuressPassphraseDialogFragment {
      return DuressPassphraseDialogFragment().apply {
        arguments = bundleOf(KEY_MODE to mode)
      }
    }
  }

  fun interface Listener {
    fun onDuressPassphraseChanged(enabled: Boolean)
  }

  private enum class SaveResult {
    SAVED,
    REAL_PASSPHRASE,
    FAILED
  }

  var listener: Listener? = null

  private var mode = MODE_SET

  private lateinit var alertDialog: AlertDialog

  private var contentView: View? = null
  private var progressView: View? = null
  private var passphraseLayout: TextInputLayout? = null
  private var repeatLayout: TextInputLayout? = null
  private var passphraseInput: EditText? = null
  private var repeatInput: EditText? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    mode = arguments?.getInt(KEY_MODE, MODE_SET) ?: MODE_SET

    val builder = MaterialAlertDialogBuilder(requireContext())

    alertDialog = if (mode == MODE_CLEAR) {
      builder
        .setTitle(R.string.DuressPassphraseDialogFragment__turn_off_duress_passphrase)
        .setMessage(R.string.DuressPassphraseDialogFragment__turn_off_message)
        .setPositiveButton(R.string.DuressPassphraseDialogFragment__turn_off) { _, _ -> clearDuress() }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    } else {
      builder
        .setTitle(R.string.DuressPassphraseDialogFragment__set_duress_passphrase)
        .setView(createSetView())
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    }

    return alertDialog
  }

  override fun onResume() {
    super.onResume()

    if (mode == MODE_SET) {
      alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { button ->
        ServiceUtil.getInputMethodManager(requireContext()).hideSoftInputFromWindow(button.windowToken, 0)
        onSetClicked()
      }
      contentView?.requestFocus()
    }
  }

  override fun onDetach() {
    super.onDetach()
    listener = null
  }

  private fun createSetView(): View {
    val view = requireActivity().layoutInflater.inflate(R.layout.duress_passphrase_dialog_view, null)

    contentView = view.findViewById(R.id.content_container)
    progressView = view.findViewById(R.id.progress_container)
    passphraseLayout = view.findViewById(R.id.duress_passphrase_layout)
    repeatLayout = view.findViewById(R.id.repeat_duress_passphrase_layout)
    passphraseInput = view.findViewById(R.id.duress_passphrase_input)
    repeatInput = view.findViewById(R.id.repeat_duress_passphrase_input)

    passphraseInput?.doAfterTextChanged { passphraseLayout?.error = null }
    repeatInput?.doAfterTextChanged { repeatLayout?.error = null }

    return view
  }

  private fun onSetClicked() {
    val passphrase = getEnteredPassphrase(passphraseInput)
    val repeated = getEnteredPassphrase(repeatInput)

    if (passphrase.isEmpty()) {
      repeated.fill(0.toChar())
      passphraseLayout?.error = getString(R.string.PassphraseChangeActivity_enter_new_passphrase_exclamation)
      return
    }

    val same = passphrase.contentEquals(repeated)
    repeated.fill(0.toChar())

    if (!same) {
      passphrase.fill(0.toChar())
      repeatLayout?.error = getString(R.string.PassphraseChangeActivity_passphrases_dont_match_exclamation)
      return
    }

    showProgress(true)

    val context = requireContext().applicationContext
    val resultListener = listener

    SignalExecutors.UNBOUNDED.execute {
      val result = try {
        saveDuress(context, passphrase)
      } finally {
        passphrase.fill(0.toChar())
      }

      ThreadUtil.runOnMain { onSaveResult(result, resultListener) }
    }
  }

  private fun onSaveResult(result: SaveResult, resultListener: Listener?) {
    if (result == SaveResult.SAVED) {
      resultListener?.onDuressPassphraseChanged(true)
      if (isAdded) {
        dismissAllowingStateLoss()
      }
      return
    }

    if (!isAdded) {
      return
    }

    showProgress(false)

    if (result == SaveResult.REAL_PASSPHRASE) {
      passphraseLayout?.error = getString(R.string.DuressPassphraseDialogFragment__this_is_your_real_passphrase)
    } else {
      Toast.makeText(requireContext(), R.string.DuressPassphraseDialogFragment__could_not_save, Toast.LENGTH_LONG).show()
    }
  }

  private fun saveDuress(context: Context, passphrase: CharArray): SaveResult {
    try {
      MasterSecretUtil.getMasterSecret(context, passphrase).close()
      return SaveResult.REAL_PASSPHRASE
    } catch (e: InvalidPassphraseException) {
      Log.d(TAG, "Duress candidate differs from the real passphrase")
    } catch (e: UnrecoverableKeyException) {
      Log.w(TAG, "Could not check the real passphrase", e)
      return SaveResult.FAILED
    }

    return try {
      DuressManager.setDuressPassphrase(context, passphrase)
      SaveResult.SAVED
    } catch (e: Throwable) {
      Log.w(TAG, "Could not save duress passphrase", e)
      SaveResult.FAILED
    }
  }

  private fun clearDuress() {
    val context = requireContext().applicationContext
    val resultListener = listener

    SignalExecutors.BOUNDED.execute {
      try {
        DuressManager.clearDuressPassphrase(context)
      } catch (e: RuntimeException) {
        Log.w(TAG, "Could not clear duress passphrase", e)
      }
      ThreadUtil.runOnMain { resultListener?.onDuressPassphraseChanged(false) }
    }
  }

  private fun showProgress(show: Boolean) {
    isCancelable = !show
    alertDialog.setTitle(if (show) R.string.please_wait else R.string.DuressPassphraseDialogFragment__set_duress_passphrase)
    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.visibility = if (show) View.GONE else View.VISIBLE
    alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.visibility = if (show) View.GONE else View.VISIBLE
    contentView?.visibility = if (show) View.GONE else View.VISIBLE
    progressView?.visibility = if (show) View.VISIBLE else View.GONE
  }

  private fun getEnteredPassphrase(editText: EditText?): CharArray {
    val text = editText?.text ?: return CharArray(0)
    val passphrase = CharArray(text.length)
    text.getChars(0, text.length, passphrase, 0)
    return passphrase
  }
}
