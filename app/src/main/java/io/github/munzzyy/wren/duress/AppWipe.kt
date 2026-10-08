// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.Context
import android.os.Process
import org.signal.core.util.ServiceUtil
import org.signal.core.util.logging.Log
import java.io.File
import java.security.KeyStore
import kotlin.system.exitProcess

object AppWipe {

  private val TAG = Log.tag(AppWipe::class.java)

  private const val CLEAR_GRACE_MILLIS = 15_000L

  /**
   * Erases every local trace of the app and ends the process. Blocks, so call it
   * off the main thread or use [wipeInBackground].
   */
  @JvmStatic
  fun wipeNow(context: Context) {
    val app = context.applicationContext ?: context

    runCatching { Log.w(TAG, "Wiping all local data") }

    runCatching { deleteKeyStoreEntries() }

    val requested = runCatching { ServiceUtil.getActivityManager(app).clearApplicationUserData() }.getOrDefault(false)
    if (requested) {
      runCatching { Thread.sleep(CLEAR_GRACE_MILLIS) }
    }

    runCatching { deleteLocalFiles(app) }

    runCatching { Process.killProcess(Process.myPid()) }
    runCatching { exitProcess(0) }
  }

  @JvmStatic
  fun wipeInBackground(context: Context) {
    val app = context.applicationContext ?: context
    runCatching {
      Thread({ wipeNow(app) }, "wren-wipe").start()
    }.onFailure {
      wipeNow(app)
    }
  }

  private fun deleteKeyStoreEntries() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    for (alias in keyStore.aliases().toList()) {
      runCatching { keyStore.deleteEntry(alias) }
    }
  }

  private fun deleteLocalFiles(context: Context) {
    for (name in context.databaseList()) {
      runCatching { context.deleteDatabase(name) }
    }

    val dataDirs = listOfNotNull(
      runCatching { File(context.applicationInfo.dataDir) }.getOrNull(),
      runCatching { File(context.createDeviceProtectedStorageContext().applicationInfo.dataDir) }.getOrNull()
    )

    val dirs = buildList {
      dataDirs.forEach { add(File(it, "shared_prefs")) }
      add(context.filesDir)
      add(context.cacheDir)
      add(context.noBackupFilesDir)
      add(context.codeCacheDir)
      addAll(context.getExternalFilesDirs(null).filterNotNull())
      addAll(context.externalCacheDirs.filterNotNull())
      addAll(dataDirs)
    }

    for (dir in dirs) {
      runCatching { deleteContents(dir) }
    }
  }

  private fun deleteContents(dir: File) {
    val children = dir.listFiles() ?: return
    for (child in children) {
      if (child.name == "lib") {
        continue
      }
      runCatching { child.deleteRecursively() }
    }
  }
}
