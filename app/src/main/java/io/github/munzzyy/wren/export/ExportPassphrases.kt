// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import java.util.UUID

/**
 * Holds the passphrase for an encrypted export in memory only, from the dialog until the job has
 * derived its key. Never in saved state, job data or preferences: if the process dies first, the
 * passphrase is gone and the export fails instead of falling back to plaintext.
 */
internal object ExportPassphrases {

  private val held = HashMap<String, CharArray>()

  /** Keeps [passphrase] and returns a token for it that is safe to put in saved state. */
  fun stash(passphrase: CharArray): String {
    val token = UUID.randomUUID().toString()
    synchronized(held) { held[token] = passphrase }
    return token
  }

  fun put(key: String, passphrase: CharArray) {
    synchronized(held) { held.put(key, passphrase)?.fill(0.toChar()) }
  }

  /** Removes and returns the passphrase. The caller wipes it when done. */
  fun take(key: String): CharArray? {
    return synchronized(held) { held.remove(key) }
  }

  fun discard(key: String) {
    take(key)?.fill(0.toChar())
  }
}

/**
 * Moves a stashed passphrase from the dialog's token to the job that will use it, and wipes it
 * if no job took it.
 */
internal class ExportPassphraseHandoff private constructor(private var passphrase: CharArray?) {

  fun handTo(jobId: String) {
    val chars = passphrase ?: return
    passphrase = null
    ExportPassphrases.put(jobId, chars)
  }

  companion object {
    /**
     * Calls [block] with null for a plain export, or with a handoff for an encrypted one. If the
     * passphrase behind [token] is gone, it tells the person and does not call [block] at all.
     */
    fun enqueue(context: Context, token: String?, block: (ExportPassphraseHandoff?) -> Unit) {
      if (token == null) {
        block(null)
        return
      }

      val chars = ExportPassphrases.take(token)
      if (chars == null) {
        ExportNotifications.postPassphraseLost(context)
        return
      }

      val handoff = ExportPassphraseHandoff(chars)
      try {
        block(handoff)
      } finally {
        handoff.passphrase?.fill(0.toChar())
        handoff.passphrase = null
      }
    }
  }
}
