// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Not exported: only [PanicResponderActivity] starts it, with the caller Android
 * reported there, and forwards the result back to the trigger app.
 */
class PanicConnectActivity : AppCompatActivity() {

  companion object {
    private val TAG = Log.tag(PanicConnectActivity::class.java)

    private const val EXTRA_PACKAGE = "package"

    fun createIntent(context: Context, packageName: String): Intent {
      return Intent(context, PanicConnectActivity::class.java).putExtra(EXTRA_PACKAGE, packageName)
    }
  }

  private var dialog: AlertDialog? = null
  private var answered = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)

    val pending = intent.getStringExtra(EXTRA_PACKAGE)
    val digest = pending?.takeIf { it.isNotEmpty() }?.let { SigningCertificates.sha256(packageManager, it) }
    if (pending.isNullOrEmpty() || digest == null) {
      finish()
      return
    }

    val shown = MaterialAlertDialogBuilder(this)
      .setTitle(R.string.PanicConnectActivity__let_this_app_trigger_wrens_panic_action)
      .setMessage(getString(R.string.PanicConnectActivity__s_s_wants_to_connect_signed_s, appLabel(pending), pending, SigningCertificates.format(digest)))
      .setPositiveButton(R.string.PanicConnectActivity__allow) { _, _ -> answer(pending, digest, allowed = true) }
      .setNegativeButton(R.string.PanicConnectActivity__deny) { _, _ -> answer(pending, digest, allowed = false) }
      .setOnDismissListener { finish() }
      .show()

    shown.window?.decorView?.filterTouchesWhenObscured = true
    shown.getButton(DialogInterface.BUTTON_POSITIVE)?.filterTouchesWhenObscured = true
    dialog = shown
  }

  override fun onDestroy() {
    dialog?.setOnDismissListener(null)
    dialog?.dismiss()
    dialog = null
    super.onDestroy()
  }

  private fun answer(pending: String, shownDigest: String, allowed: Boolean) {
    if (answered) {
      return
    }
    answered = true

    val result = try {
      val store = DuressStore(this)
      val currentDigest = SigningCertificates.sha256(packageManager, pending)
      when (PanicDecision.confirm(store.panicTriggerPackage, pending, shownDigest, currentDigest, packageName, allowed)) {
        PanicConfirmResult.CONNECT -> {
          store.connectPanicTrigger(pending, shownDigest)
          RESULT_OK
        }
        PanicConfirmResult.ALREADY_CONNECTED -> RESULT_OK
        PanicConfirmResult.REFUSE -> RESULT_CANCELED
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not store panic trigger", e)
      RESULT_CANCELED
    }

    setResult(result)
  }

  private fun appLabel(packageName: String): String {
    return try {
      packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString().takeIf { it.isNotBlank() } ?: packageName
    } catch (e: PackageManager.NameNotFoundException) {
      packageName
    }
  }
}
