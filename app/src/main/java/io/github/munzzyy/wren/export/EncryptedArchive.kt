// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import io.github.munzzyy.wren.crypto.Wrenx
import io.github.munzzyy.wren.crypto.WrenxOutputStream
import org.signal.core.util.logging.Log
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Something went wrong after part of an entry was already in the archive. The stream can't be
 * repaired, so the whole export has to stop, not just the chat that was being written.
 */
internal class ArchiveWriteException(cause: IOException) : IOException(cause)

/**
 * One .wrenx file under a folder the person picked: a tar archive of export folders, encrypted
 * as it is written. Nothing is kept in memory beyond one chunk, and nothing in plaintext goes to
 * disk. The file is only valid after [finish]; anything else should end in [closeAndDelete].
 */
internal class EncryptedArchive private constructor(
  private val spoolDirectory: File,
  val file: DocumentFile,
  private val finalName: String,
  private val wrenx: WrenxOutputStream,
  modifiedSeconds: Long
) : Closeable {

  companion object {
    private val TAG = Log.tag(EncryptedArchive::class.java)

    const val MIME_TYPE = "application/octet-stream"

    /**
     * Creates the file under its partial name and derives the key, which takes as long as scrypt
     * does. [finish] gives it its real name.
     */
    @WorkerThread
    fun create(context: Context, parent: DocumentFile, baseName: String, passphrase: CharArray, modifiedMillis: Long): EncryptedArchive {
      val finalName = "$baseName.${Wrenx.FILE_EXTENSION}"
      val file = parent.createFile(MIME_TYPE, ExportFileNames.partialName(finalName)) ?: throw IOException("Could not create the encrypted export file")

      try {
        val stream = context.contentResolver.openOutputStream(file.uri) ?: throw IOException("Could not open the encrypted export file")
        try {
          val wrenx = WrenxOutputStream(BufferedOutputStream(stream, 64 * 1024), passphrase)
          return EncryptedArchive(context.cacheDir, file, finalName, wrenx, modifiedMillis / 1000)
        } catch (e: Throwable) {
          stream.close()
          throw e
        }
      } catch (e: Throwable) {
        file.delete()
        throw e
      }
    }
  }

  private val tar = TarWriter(wrenx, modifiedSeconds)

  fun addFolder(path: String) {
    guarded { tar.addDirectory(path) }
  }

  fun chatFolder(path: String): ChatDestination {
    addFolder(path)
    return ArchiveFolder(path)
  }

  fun finish() {
    guarded {
      tar.finish()
      wrenx.finish()
    }
    ExportFolders.finish(file, finalName)
  }

  override fun close() {
    try {
      wrenx.close()
    } catch (e: IOException) {
      Log.w(TAG, "Could not close the encrypted export file", e)
    }
  }

  fun closeAndDelete() {
    close()
    if (!file.delete()) {
      Log.w(TAG, "Could not delete the partial encrypted export")
    }
  }

  private fun <T> guarded(block: () -> T): T {
    return try {
      block()
    } catch (e: ArchiveWriteException) {
      throw e
    } catch (e: IOException) {
      throw ArchiveWriteException(e)
    } catch (e: RuntimeException) {
      throw ArchiveWriteException(IOException(e))
    }
  }

  private inner class ArchiveFolder(private val path: String) : ChatDestination {

    private var mediaFolderAdded = false

    override fun writeChatFile(format: ChatExportFormat, write: (OutputStream) -> Unit) {
      ExportSpool(spoolDirectory).use { spool ->
        spool.openOutput().use(write)
        guarded {
          tar.addFile("$path/${format.fileName}", spool.length) { out ->
            spool.openInput().use { it.copyTo(out, 64 * 1024) }
          }
        }
      }
    }

    override fun writeMedia(name: String, open: () -> InputStream): String? {
      val size = try {
        open().use { count(it) }
      } catch (e: IOException) {
        Log.w(TAG, "Could not read an attachment, marking it missing", e)
        return null
      }

      guarded {
        if (!mediaFolderAdded) {
          tar.addDirectory("$path/${ExportFileNames.MEDIA_FOLDER}")
          mediaFolderAdded = true
        }
        tar.addFile("$path/${ExportFileNames.MEDIA_FOLDER}/$name", size) { out ->
          open().use { it.copyTo(out, 64 * 1024) }
        }
      }
      return name
    }

    override fun discard() {
      Log.w(TAG, "A chat failed partway; what it already added stays in the archive")
    }

    private fun count(input: InputStream): Long {
      val buffer = ByteArray(64 * 1024)
      var total = 0L
      while (true) {
        val read = input.read(buffer)
        if (read < 0) return total
        total += read
      }
    }
  }
}
