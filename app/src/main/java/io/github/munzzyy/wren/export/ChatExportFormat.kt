// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

enum class ChatExportFormat(val fileName: String, val mimeType: String) {
  HTML("chat.html", "text/html"),
  TEXT("chat.txt", "text/plain"),
  JSON("chat.json", "application/json")
}
