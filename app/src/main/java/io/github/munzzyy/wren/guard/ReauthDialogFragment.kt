// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import io.github.munzzyy.wren.duress.DuressManager
import org.signal.core.util.ServiceUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.setIncognitoKeyboardEnabled

/**
 * Asks for the passphrase again. Every attempt is counted by
 * [DuressManager.onAttemptStarting] and a wrong one goes through
 * [DuressManager.onWrongPassphrase], so duress and the failed-attempt limit
 * apply here the same as on the lock screen.
 */
class ReauthDialogFragment : DialogFragment() {

  companion object {
    private val TAG = Log.tag(ReauthDialogFragment::class.java)
  }

  private enum class Result {
    VERIFIED,
    WRONG,
    FAILED
  }

  var onVerified: (() -> Unit)? = null

  private lateinit var alertDialog: AlertDialog

  private var contentView: View? = null
  private var progressView: View? = null
  private var passphraseLayout: TextInputLayout? = null
  private var passphraseInput: EditText? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    alertDialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ReauthDialogFragment__enter_your_passphrase)
      .setView(createView())
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    alertDialog.window?.let { WindowUtil.initializeScreenshotSecurity(requireContext(), it) }

    return alertDialog
  }

  override fun onResume() {
    super.onResume()

    if (onVerified == null) {
      dismissAllowingStateLoss()
      return
    }

    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onOkClicked() }
    contentView?.requestFocus()
  }

  override fun onDetach() {
    super.onDetach()
    onVerified = null
  }

  private fun createView(): View {
    val view = requireActivity().layoutInflater.inflate(R.layout.reauth_dialog_view, null)

    contentView = view.findViewById(R.id.content_container)
    progressView = view.findViewById(R.id.progress_container)
    passphraseLayout = view.findViewById(R.id.reauth_passphrase_layout)
    passphraseInput = view.findViewById(R.id.reauth_passphrase_input)

    passphraseInput?.setIncognitoKeyboardEnabled(TextSecurePreferences.isIncognitoKeyboardEnabled(requireContext()))
    passphraseInput?.doAfterTextChanged { passphraseLayout?.error = null }
    passphraseInput?.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        onOkClicked()
        true
      } else {
        false
      }
    }

    return view
  }

  private fun onOkClicked() {
    passphraseInput?.let { ServiceUtil.getInputMethodManager(requireContext()).hideSoftInputFromWindow(it.windowToken, 0) }

    val passphrase = getEnteredPassphrase()
    if (passphrase.isEmpty()) {
      passphraseLayout?.error = getString(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation)
      return
    }

    showProgress(true)

    val context = requireContext().applicationContext

    SignalExecutors.UNBOUNDED.execute {
      val result = try {
        verify(context, passphrase)
      } finally {
        passphrase.fill(0.toChar())
      }

      ThreadUtil.runOnMain { onResult(result) }
    }
  }

  private fun verify(context: Context, passphrase: CharArray): Result {
    if (!DuressManager.onAttemptStarting(context)) {
      return Result.FAILED
    }

    return try {
      MasterSecretUtil.getMasterSecret(context, passphrase).close()
      DuressManager.onUnlocked(context)
      Result.VERIFIED
    } catch (e: InvalidPassphraseException) {
      Log.w(TAG, "Wrong passphrase at re-authentication")
      DuressManager.onWrongPassphrase(context, passphrase)
      Result.WRONG
    } catch (e: UnrecoverableKeyException) {
      Log.w(TAG, "Could not check the passphrase", e)
      Result.FAILED
    }
  }

  private fun onResult(result: Result) {
    if (!isAdded) {
      return
    }

    if (result == Result.VERIFIED) {
      val callback = onVerified
      onVerified = null
      dismissAllowingStateLoss()
      callback?.invoke()
      return
    }

    showProgress(false)

    if (result == Result.WRONG) {
      passphraseInput?.text = null
      passphraseLayout?.error = getString(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation)
    } else {
      Toast.makeText(requireContext(), R.string.ReauthDialogFragment__could_not_check_your_passphrase, Toast.LENGTH_LONG).show()
    }
  }

  private fun showProgress(show: Boolean) {
    isCancelable = !show
    alertDialog.setTitle(if (show) R.string.please_wait else R.string.ReauthDialogFragment__enter_your_passphrase)
    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.visibility = if (show) View.GONE else View.VISIBLE
    alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.visibility = if (show) View.GONE else View.VISIBLE
    contentView?.visibility = if (show) View.GONE else View.VISIBLE
    progressView?.visibility = if (show) View.VISIBLE else View.GONE
  }

  private fun getEnteredPassphrase(): CharArray {
    val text = passphraseInput?.text ?: return CharArray(0)
    val passphrase = CharArray(text.length)
    text.getChars(0, text.length, passphrase, 0)
    return passphrase
  }
}
