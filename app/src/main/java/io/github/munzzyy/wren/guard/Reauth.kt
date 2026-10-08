// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.guard

import android.content.Context
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Gate in front of actions that could erase data or weaken a protection. With
 * the passphrase lock off there is nothing to ask for, so it passes straight through.
 */
object Reauth {

  private const val TAG = "ReauthDialogFragment"

  fun isRequired(context: Context): Boolean = TextSecurePreferences.isPassphraseLockEnabled(context)

  fun require(fragment: Fragment, onVerified: () -> Unit) {
    if (!isRequired(fragment.requireContext())) {
      onVerified()
      return
    }

    val fragmentManager = fragment.childFragmentManager
    if (fragmentManager.findFragmentByTag(TAG) != null) {
      return
    }

    val dialog = ReauthDialogFragment()
    dialog.onVerified = onVerified
    dialog.show(fragmentManager, TAG)
  }
}
