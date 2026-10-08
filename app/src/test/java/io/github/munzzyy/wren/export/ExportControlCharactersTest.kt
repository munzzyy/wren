// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportControlCharactersTest {

  private val unsafe = listOf(
    '\u0000', '\u0001', '\u0007', '\u000B', '\u000C', '\u000D', '\u001B', '\u001F',
    '\u007F', '\u0080', '\u0085', '\u009B', '\u009F',
    '؜', '‎', '‏',
    '‪', '‫', '‬', '‭', '‮',
    '⁦', '⁧', '⁨', '⁩'
  )

  private val replacement = '�'

  private fun containsUnsafe(text: String): Boolean = text.any { it in unsafe }

  private val hostileName = "Ev‮exe.gpj‬\u0007e"
  private val hostileBody = "line one\u001B[2J\ttabbed\nline⁦two⁩\u0000"

  private fun message(sender: String = hostileName, body: String = hostileBody) = ChatExportFixture.messages[1].copy(
    sender = sender,
    body = body,
    quote = ExportQuote(author = hostileName, text = "quoted‮text"),
    reactions = listOf(ExportReaction("\u0001x", hostileName)),
    attachments = listOf(ExportAttachment(fileName = "2-1‮.jpg", contentType = "image/jpeg\u0000", sizeBytes = 1)),
    linkPreviews = listOf(ExportLink(title = "t‏itle", url = "https://example.org/‮"))
  )

  @Test
  fun `the unsafe set is exactly what is replaced`() {
    for (c in unsafe) {
      assertTrue("U+%04X".format(c.code), ChatExportWriter.isUnsafeControl(c))
    }
    for (c in listOf('\t', '\n', ' ', 'a', ' ', '‍', '️', 'א', '⁥', '⁪', ' ', '­')) {
      assertFalse("U+%04X".format(c.code), ChatExportWriter.isUnsafeControl(c))
    }
  }

  @Test
  fun `text export replaces every unsafe character and keeps tabs and newlines`() {
    val text = ChatExportFixture.render(ChatExportFormat.TEXT, chat = ChatExportFixture.chat.copy(name = hostileName), messages = listOf(message()))

    assertFalse(text, containsUnsafe(text))
    assertTrue(text.contains("Chat: Ev${replacement}exe.gpj$replacement${replacement}e\n"))
    assertTrue(text.contains("] Ev${replacement}exe.gpj$replacement${replacement}e: line one$replacement[2J\ttabbed\n  line${replacement}two$replacement$replacement\n"))
    assertTrue(text.contains("[image/jpeg$replacement, "))
  }

  @Test
  fun `text export turns a lone carriage return into a line break, not a replacement`() {
    val text = ChatExportFixture.render(ChatExportFormat.TEXT, messages = listOf(ChatExportFixture.messages[0].copy(body = "a\rb\r\nc")))
    assertTrue(text, text.contains(": a\n  b\n  c\n"))
  }

  @Test
  fun `html export strips unsafe characters from names`() {
    val html = ChatExportFixture.render(ChatExportFormat.HTML, chat = ChatExportFixture.chat.copy(name = hostileName), messages = listOf(message()))

    assertTrue(html.contains("<title>Evexe.gpje</title>"))
    assertTrue(html.contains("<h1>Evexe.gpje</h1>"))
    assertTrue(html.contains("<div class=\"sender\">Evexe.gpje</div>"))
    assertTrue(html.contains("<span title=\"Evexe.gpje\">"))
    assertTrue(html.contains("alt=\"2-1.jpg\""))
    val names = Regex("<(?:title|h1)>[^<]*<|class=\"sender\">[^<]*<|title=\"[^\"]*\"|alt=\"[^\"]*\"").findAll(html).map { it.value }.toList()
    assertTrue(names.isNotEmpty())
    for (name in names) {
      assertFalse(name, containsUnsafe(name))
    }
  }

  @Test
  fun `json export strips unsafe characters from names and keeps bodies exact`() {
    val text = ChatExportFixture.render(ChatExportFormat.JSON, chat = ChatExportFixture.chat.copy(name = hostileName), messages = listOf(message()))
    val json = Json.parseToJsonElement(text).jsonObject
    val parsed = json["messages"]!!.jsonArray[0].jsonObject

    assertEquals("Evexe.gpje", json["chat"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    assertEquals("Evexe.gpje", parsed["sender"]!!.jsonPrimitive.content)
    assertEquals("Evexe.gpje", parsed["quote"]!!.jsonObject["author"]!!.jsonPrimitive.content)
    assertEquals("Evexe.gpje", parsed["reactions"]!!.jsonArray[0].jsonObject["author"]!!.jsonPrimitive.content)
    assertEquals("2-1.jpg", parsed["attachments"]!!.jsonArray[0].jsonObject["fileName"]!!.jsonPrimitive.content)
    assertEquals(hostileBody, parsed["body"]!!.jsonPrimitive.content)
  }

  @Test
  fun `clean text passes through unchanged`() {
    val clean = "Zoë שלום 👍‍\tok"
    assertTrue(clean === ChatExportWriter.replaceControls(clean))
    assertTrue(clean === ChatExportWriter.stripControls(clean))
  }
}
