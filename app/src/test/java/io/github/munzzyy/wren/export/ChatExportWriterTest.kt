// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExportWriterTest {

  @Test
  fun `text export matches golden output`() {
    val expected = """
      Chat: Alice
      Type: direct
      Exported: 2026-10-08 14:03 (UTC)
      Messages: 7

      [2026-10-08 13:00] You: Hi there
        second line
        Disappears after 1 day

      [2026-10-08 13:01] Alice: Look at this
        > You: Hi there
        Attachment: media/2-1.jpg [image/jpeg, 2.0 KB]
        Reactions: $THUMBS You, $HEART Alice

      [2026-10-08 13:02] * Alice set the disappearing message timer to 1 day.

      [2026-10-08 13:03] Alice: [deleted]

      [2026-10-08 13:04] Alice: [view-once media]

      [2026-10-08 13:05] You: https://example.org/post
        Link: Example post <https://example.org/post>

      [2026-10-08 13:06] Alice:
        Attachment: 7-1.m4a (not downloaded) [audio/mp4, 4.8 MB]


    """.trimIndent()

    assertEquals(expected, ChatExportFixture.render(ChatExportFormat.TEXT))
  }

  @Test
  fun `html export matches golden output`() {
    val html = ChatExportFixture.render(ChatExportFormat.HTML)

    val expectedBody = """
      <body>
      <header>
      <h1>Alice</h1>
      <p class="meta">Exported 2026-10-08 14:03 &middot; 7 messages</p>
      </header>
      <main>
      <div class="msg out" id="m1">
      <div class="bubble">
      <div class="sender">You</div>
      <div class="body">Hi there
      second line</div>
      <div class="time">2026-10-08 13:00 <span class="timer">&#9201; disappears after 1 day</span></div>
      </div>
      </div>
      <div class="msg in" id="m2">
      <div class="bubble">
      <div class="sender">Alice</div>
      <blockquote class="quote"><div class="sender">You</div><div>Hi there</div></blockquote>
      <div class="attachment"><a href="media/2-1.jpg"><img class="image" src="media/2-1.jpg" alt="2-1.jpg" loading="lazy"></a></div>
      <div class="body">Look at this</div>
      <div class="reactions"><span title="You">$THUMBS</span><span title="Alice">$HEART</span></div>
      <div class="time">2026-10-08 13:01</div>
      </div>
      </div>
      <div class="system" id="m3">Alice set the disappearing message timer to 1 day. <span class="time">2026-10-08 13:02</span></div>
      <div class="msg in" id="m4">
      <div class="bubble">
      <div class="sender">Alice</div>
      <div class="placeholder">This message was deleted.</div>
      <div class="time">2026-10-08 13:03</div>
      </div>
      </div>
      <div class="msg in" id="m5">
      <div class="bubble">
      <div class="sender">Alice</div>
      <div class="placeholder">View-once media</div>
      <div class="time">2026-10-08 13:04</div>
      </div>
      </div>
      <div class="msg out" id="m6">
      <div class="bubble">
      <div class="sender">You</div>
      <div class="link"><a href="https://example.org/post" rel="noopener noreferrer">Example post</a><div class="url">https://example.org/post</div></div>
      <div class="body">https://example.org/post</div>
      <div class="time">2026-10-08 13:05</div>
      </div>
      </div>
      <div class="msg in" id="m7">
      <div class="bubble">
      <div class="sender">Alice</div>
      <div class="attachment missing">7-1.m4a (audio/mp4, 4.8 MB) &middot; not downloaded</div>
      <div class="time">2026-10-08 13:06</div>
      </div>
      </div>
      </main>
      </body>
      </html>

    """.trimIndent()

    assertTrue(html.startsWith("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"))
    assertTrue(html.contains("<title>Alice</title>"))
    assertEquals(expectedBody, html.substring(html.indexOf("<body>")))
  }

  @Test
  fun `html export is self contained`() {
    val html = ChatExportFixture.render(ChatExportFormat.HTML)

    assertFalse(html.contains("<script", ignoreCase = true))
    assertFalse(html.contains("<link", ignoreCase = true))
    assertFalse(html.contains("@import", ignoreCase = true))
    assertFalse(html.contains("url(", ignoreCase = true))
    assertTrue(html.contains("script-src 'none'"))

    val srcs = Regex("src=\"([^\"]*)\"").findAll(html).map { it.groupValues[1] }.toList()
    assertEquals(listOf("media/2-1.jpg"), srcs)
  }

  @Test
  fun `html renders audio and video with controls and other files as links`() {
    val message = ExportMessage(
      id = 9,
      sentAtMillis = 0,
      receivedAtMillis = 0,
      sender = "Alice",
      outgoing = false,
      body = "",
      attachments = listOf(
        ExportAttachment("9-1.m4a", "audio/mp4", 10, voiceNote = true),
        ExportAttachment("9-2.mp4", "video/mp4", 10),
        ExportAttachment("9-3.pdf", "application/pdf", 10),
        ExportAttachment("9-4.webp", "image/webp", 10, sticker = true)
      )
    )

    val html = ChatExportFixture.render(ChatExportFormat.HTML, messages = listOf(message))

    assertTrue(html.contains("<audio controls preload=\"none\" src=\"media/9-1.m4a\"></audio>"))
    assertTrue(html.contains("<video controls preload=\"none\" src=\"media/9-2.mp4\"></video>"))
    assertTrue(html.contains("<a href=\"media/9-3.pdf\">9-3.pdf (application/pdf, 10 B)</a>"))
    assertTrue(html.contains("<img class=\"sticker\" src=\"media/9-4.webp\""))
  }

  @Test
  fun `media is not linked when the export leaves it out`() {
    val chat = ChatExportFixture.chat.copy(includesMedia = false)

    val html = ChatExportFixture.render(ChatExportFormat.HTML, chat = chat)
    assertFalse(html.contains("media/"))
    assertTrue(html.contains("2-1.jpg (image/jpeg, 2.0 KB) &middot; not included"))

    val text = ChatExportFixture.render(ChatExportFormat.TEXT, chat = chat)
    assertTrue(text.contains("  Attachment: 2-1.jpg (not included) [image/jpeg, 2.0 KB]\n"))
    assertTrue(text.contains("Media: not included\n"))

    val json = Json.parseToJsonElement(ChatExportFixture.render(ChatExportFormat.JSON, chat = chat)).jsonObject
    val attachment = json["messages"]!!.jsonArray[1].jsonObject["attachments"]!!.jsonArray[0].jsonObject
    assertEquals("null", attachment["path"].toString())
  }

  @Test
  fun `json export matches golden output`() {
    val expected = """
      {
      "format":"wren-chat-export",
      "version":1,
      "chat":{"name":"Alice","isGroup":false,"messageCount":7,"includesMedia":true,"exportedAt":1791468180000,"exportedAtIso":"2026-10-08T14:03:00Z"},
      "messages":[
      {"id":1,"sentAt":1791464400000,"sentAtIso":"2026-10-08T13:00:00Z","receivedAt":1791464400000,"receivedAtIso":"2026-10-08T13:00:00Z","sender":"You","outgoing":true,"system":false,"remoteDeleted":false,"viewOnce":false,"expiresInMillis":86400000,"body":"Hi there\nsecond line","quote":null,"reactions":[],"attachments":[],"linkPreviews":[]},
      {"id":2,"sentAt":1791464460000,"sentAtIso":"2026-10-08T13:01:00Z","receivedAt":1791464465000,"receivedAtIso":"2026-10-08T13:01:05Z","sender":"Alice","outgoing":false,"system":false,"remoteDeleted":false,"viewOnce":false,"expiresInMillis":0,"body":"Look at this","quote":{"author":"You","text":"Hi there"},"reactions":[{"emoji":"$THUMBS","author":"You"},{"emoji":"$HEART","author":"Alice"}],"attachments":[{"fileName":"2-1.jpg","path":"media/2-1.jpg","contentType":"image/jpeg","sizeBytes":2048,"voiceNote":false,"sticker":false,"missing":false}],"linkPreviews":[]},
      {"id":3,"sentAt":1791464520000,"sentAtIso":"2026-10-08T13:02:00Z","receivedAt":1791464520000,"receivedAtIso":"2026-10-08T13:02:00Z","sender":"Alice","outgoing":false,"system":true,"remoteDeleted":false,"viewOnce":false,"expiresInMillis":0,"body":"Alice set the disappearing message timer to 1 day.","quote":null,"reactions":[],"attachments":[],"linkPreviews":[]},
      {"id":4,"sentAt":1791464580000,"sentAtIso":"2026-10-08T13:03:00Z","receivedAt":1791464580000,"receivedAtIso":"2026-10-08T13:03:00Z","sender":"Alice","outgoing":false,"system":false,"remoteDeleted":true,"viewOnce":false,"expiresInMillis":0,"body":"","quote":null,"reactions":[],"attachments":[],"linkPreviews":[]},
      {"id":5,"sentAt":1791464640000,"sentAtIso":"2026-10-08T13:04:00Z","receivedAt":1791464640000,"receivedAtIso":"2026-10-08T13:04:00Z","sender":"Alice","outgoing":false,"system":false,"remoteDeleted":false,"viewOnce":true,"expiresInMillis":0,"body":"","quote":null,"reactions":[],"attachments":[],"linkPreviews":[]},
      {"id":6,"sentAt":1791464700000,"sentAtIso":"2026-10-08T13:05:00Z","receivedAt":1791464700000,"receivedAtIso":"2026-10-08T13:05:00Z","sender":"You","outgoing":true,"system":false,"remoteDeleted":false,"viewOnce":false,"expiresInMillis":0,"body":"https://example.org/post","quote":null,"reactions":[],"attachments":[],"linkPreviews":[{"title":"Example post","url":"https://example.org/post"}]},
      {"id":7,"sentAt":1791464760000,"sentAtIso":"2026-10-08T13:06:00Z","receivedAt":1791464760000,"receivedAtIso":"2026-10-08T13:06:00Z","sender":"Alice","outgoing":false,"system":false,"remoteDeleted":false,"viewOnce":false,"expiresInMillis":0,"body":"","quote":null,"reactions":[],"attachments":[{"fileName":"7-1.m4a","path":null,"contentType":"audio/mp4","sizeBytes":5000000,"voiceNote":true,"sticker":false,"missing":true}],"linkPreviews":[]}
      ]
      }

    """.trimIndent()

    assertEquals(expected, ChatExportFixture.render(ChatExportFormat.JSON))
  }

  @Test
  fun `json export parses and round trips user content`() {
    val json = Json.parseToJsonElement(ChatExportFixture.render(ChatExportFormat.JSON)).jsonObject

    assertEquals("Alice", json["chat"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    val messages = json["messages"] as JsonArray
    assertEquals(ChatExportFixture.messages.size, messages.size)
    ChatExportFixture.messages.zip(messages).forEach { (expected, actual) ->
      actual as JsonObject
      assertEquals(expected.id, actual["id"]!!.jsonPrimitive.long)
      assertEquals(expected.body, actual["body"]!!.jsonPrimitive.content)
      assertEquals(expected.sentAtMillis, actual["sentAt"]!!.jsonPrimitive.long)
    }
  }

  @Test
  fun `empty chat writes valid json`() {
    val text = ChatExportFixture.render(ChatExportFormat.JSON, chat = ChatExportFixture.chat.copy(messageCount = 0), messages = emptyList())
    val json = Json.parseToJsonElement(text).jsonObject
    assertEquals(0, json["messages"]!!.jsonArray.size)
  }

  @Test
  fun `html escapes hostile content in text and attributes`() {
    val hostile = ExportMessage(
      id = 66,
      sentAtMillis = 0,
      receivedAtMillis = 0,
      sender = "Eve \"the\" <b>bold</b> & 'co'",
      outgoing = false,
      body = "<script>alert(1)</script> & \"quoted\" 'single' &amp;",
      quote = ExportQuote(author = "<i>x</i>", text = "</blockquote><script>alert(2)</script>"),
      reactions = listOf(ExportReaction(emoji = "<img src=x onerror=alert(3)>", author = "\" onmouseover=\"alert(4)")),
      attachments = listOf(ExportAttachment(fileName = "a\"><script>alert(5)</script>.jpg", contentType = "image/jpeg", sizeBytes = 1)),
      linkPreviews = listOf(
        ExportLink(title = "<img src=x onerror=alert(6)>", url = "javascript:alert(7)"),
        ExportLink(title = "upper", url = "JAVASCRIPT:alert(8)"),
        ExportLink(title = "spaced", url = " https://example.org"),
        ExportLink(title = "data", url = "data:text/html,<script>alert(9)</script>"),
        ExportLink(title = "ok", url = "https://example.org/?a=1&b=\"2\"")
      )
    )
    val chat = ChatExportFixture.chat.copy(name = "</title><script>alert(0)</script>")

    val html = ChatExportFixture.render(ChatExportFormat.HTML, chat = chat, messages = listOf(hostile))

    assertFalse(html.contains("<script", ignoreCase = true))
    assertFalse(html.contains("<img src=x"))
    assertFalse(html.contains("<b>"))
    assertFalse(html.contains("<i>"))
    assertFalse(html.contains("href=\"javascript", ignoreCase = true))
    assertFalse(html.contains("href=\" "))
    assertFalse(html.contains("href=\"data:"))
    assertFalse(html.contains("\" onmouseover"))

    assertTrue(html.contains("<title>&lt;/title&gt;&lt;script&gt;alert(0)&lt;/script&gt;</title>"))
    assertTrue(html.contains("<div class=\"sender\">Eve &quot;the&quot; &lt;b&gt;bold&lt;/b&gt; &amp; &#39;co&#39;</div>"))
    assertTrue(html.contains("<div class=\"body\">&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quoted&quot; &#39;single&#39; &amp;amp;</div>"))
    assertTrue(html.contains("<span title=\"&quot; onmouseover=&quot;alert(4)\">&lt;img src=x onerror=alert(3)&gt;</span>"))
    assertTrue(html.contains("<span>&lt;img src=x onerror=alert(6)&gt;</span><div class=\"url\">javascript:alert(7)</div>"))
    assertTrue(html.contains("src=\"media/a&quot;&gt;&lt;script&gt;alert(5)&lt;/script&gt;.jpg\""))
    assertTrue(html.contains("<a href=\"https://example.org/?a=1&amp;b=&quot;2&quot;\" rel=\"noopener noreferrer\">ok</a>"))

    val hrefs = Regex("href=\"([^\"]*)\"").findAll(html).map { it.groupValues[1] }.toList()
    assertEquals(listOf("media/a&quot;&gt;&lt;script&gt;alert(5)&lt;/script&gt;.jpg", "https://example.org/?a=1&amp;b=&quot;2&quot;"), hrefs)
  }

  @Test
  fun `escape covers every html special character`() {
    assertEquals("&lt;&gt;&amp;&quot;&#39;plain", HtmlChatExportWriter.escape("<>&\"'plain"))
  }

  @Test
  fun `json escapes control characters and quotes`() {
    val message = ChatExportFixture.messages[0].copy(body = "tab\tquote\"backslash\\nul\u0000line end", sender = "</script>")
    val text = ChatExportFixture.render(ChatExportFormat.JSON, messages = listOf(message))

    assertTrue(text.contains("\"body\":\"tab\\tquote\\\"backslash\\\\nul\\u0000line end\""))
    val parsed = Json.parseToJsonElement(text).jsonObject["messages"]!!.jsonArray[0].jsonObject
    assertEquals(message.body, (parsed["body"] as JsonPrimitive).content)
  }

  @Test
  fun `text keeps multi line quotes and names on their own lines`() {
    val message = ChatExportFixture.messages[1].copy(
      sender = "Multi\nLine",
      quote = ExportQuote(author = "You", text = "one\ntwo"),
      reactions = emptyList(),
      attachments = emptyList()
    )
    val text = ChatExportFixture.render(ChatExportFormat.TEXT, messages = listOf(message))

    assertTrue(text.contains("[2026-10-08 13:01] Multi Line: Look at this\n  > You: one\n  > two\n\n"))
  }

  @Test
  fun `timer labels`() {
    assertEquals("30 seconds", ChatExportWriter.timerLabel(30_000))
    assertEquals("1 minute", ChatExportWriter.timerLabel(60_000))
    assertEquals("8 hours", ChatExportWriter.timerLabel(8 * 3_600_000L))
    assertEquals("1 week", ChatExportWriter.timerLabel(7 * 86_400_000L))
    assertEquals("90 seconds", ChatExportWriter.timerLabel(90_000))
  }

  companion object {
    private const val THUMBS = "👍"
    private const val HEART = "❤️"
  }
}
