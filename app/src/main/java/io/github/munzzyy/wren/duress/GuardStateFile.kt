// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The failed unlock count and the time of the last unlock. They live in a file under
 * no_backup instead of shared_prefs, so restoring an adb backup can't roll them back.
 * Every write is synced to disk and swapped in with a rename.
 */
class GuardStateFile(private val file: File) {

  companion object {
    private const val FAILED_ATTEMPTS = "failed_attempts"
    private const val LAST_UNLOCK_AT = "last_unlock_at"
  }

  data class State(val failedAttempts: Int = 0, val lastUnlockAt: Long = 0L)

  fun exists(): Boolean = file.isFile

  /** A missing or damaged file reads as no failed attempts and no recorded unlock. */
  fun read(): State {
    val lines = try {
      file.readLines(Charsets.UTF_8)
    } catch (e: IOException) {
      return State()
    }

    val values = lines.mapNotNull { line ->
      val parts = line.split('=', limit = 2)
      if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }.toMap()

    return State(
      failedAttempts = values[FAILED_ATTEMPTS]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
      lastUnlockAt = values[LAST_UNLOCK_AT]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    )
  }

  @Throws(IOException::class)
  fun write(state: State) {
    val parent = file.parentFile ?: throw IOException("No parent directory")
    if (!parent.isDirectory && !parent.mkdirs()) {
      throw IOException("Could not create ${parent.name}")
    }

    val temp = File(parent, "${file.name}.new")
    try {
      FileOutputStream(temp).use { out ->
        out.write("$FAILED_ATTEMPTS=${state.failedAttempts.coerceAtLeast(0)}\n$LAST_UNLOCK_AT=${state.lastUnlockAt.coerceAtLeast(0L)}\n".toByteArray(Charsets.UTF_8))
        out.flush()
        out.fd.sync()
      }
      if (!temp.renameTo(file)) {
        throw IOException("Could not replace ${file.name}")
      }
    } catch (e: IOException) {
      temp.delete()
      throw e
    }
  }

  /**
   * Reads the file, creating it from [legacy] the first time. [clearLegacy] runs only once the
   * file is safely on disk, so a failed write leaves the old values where they were.
   */
  @Throws(IOException::class)
  fun readOrMigrate(legacy: () -> State, clearLegacy: () -> Unit): State {
    if (exists()) {
      return read()
    }
    val state = legacy()
    write(state)
    clearLegacy()
    return state
  }
}
