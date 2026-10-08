// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.logging.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Where one chat's files go: a plain folder, or a folder inside an encrypted archive. */
internal interface ChatDestination {

  /** Writes the chat file. [write] must not close the stream it gets. */
  fun writeChatFile(format: ChatExportFormat, write: (OutputStream) -> Unit)

  /**
   * Copies one attachment into media/ and returns the name it was saved under, or null when it
   * could not be read and should be listed as missing.
   */
  fun writeMedia(name: String, contentType: String, open: () -> InputStream): String?

  /** Removes what this chat wrote so far, where that is possible. */
  fun discard()
}

internal class FolderDestination(private val context: Context, val folder: DocumentFile) : ChatDestination {

  companion object {
    private val TAG = Log.tag(FolderDestination::class.java)
  }

  private var mediaFolder: DocumentFile? = null

  override fun writeChatFile(format: ChatExportFormat, write: (OutputStream) -> Unit) {
    val file = folder.createFile(format.mimeType, format.fileName) ?: throw IOException("Could not create ${format.fileName}")
    openOutput(file).use(write)
  }

  override fun writeMedia(name: String, contentType: String, open: () -> InputStream): String? {
    val media = mediaFolder ?: (folder.createDirectory(ExportFileNames.MEDIA_FOLDER) ?: throw IOException("Could not create the media folder")).also { mediaFolder = it }

    val target = media.createFile(contentType, name)
    if (target == null) {
      Log.w(TAG, "Could not create a media file, marking it missing")
      return null
    }

    return try {
      open().use { input ->
        openOutput(target).use { output -> input.copyTo(output, 64 * 1024) }
      }
      target.name ?: name
    } catch (e: IOException) {
      Log.w(TAG, "Could not copy an attachment, marking it missing", e)
      target.delete()
      null
    }
  }

  override fun discard() {
    if (!folder.delete()) {
      Log.w(TAG, "Could not delete the partial chat folder")
    }
  }

  private fun openOutput(file: DocumentFile): OutputStream {
    return context.contentResolver.openOutputStream(file.uri) ?: throw IOException("Could not open ${file.name}")
  }
}
