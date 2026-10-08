// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPassphrasesTest {

  @Test
  fun `short, blank and mismatched passphrases are refused`() {
    assertEquals(ExportPassphraseRules.Problem.TOO_SHORT, ExportPassphraseRules.check("1234567".toCharArray(), "1234567".toCharArray()))
    assertEquals(ExportPassphraseRules.Problem.TOO_SHORT, ExportPassphraseRules.check("          ".toCharArray(), "          ".toCharArray()))
    assertEquals(ExportPassphraseRules.Problem.MISMATCH, ExportPassphraseRules.check("plum orbit".toCharArray(), "plum orbjt".toCharArray()))
    assertNull(ExportPassphraseRules.check("plum orbit".toCharArray(), "plum orbit".toCharArray()))
  }

  @Test
  fun `length counts what the person sees, not UTF-16 units`() {
    val sevenEmoji = "😀".repeat(7).toCharArray()
    assertEquals(ExportPassphraseRules.Problem.TOO_SHORT, ExportPassphraseRules.check(sevenEmoji, sevenEmoji.copyOf()))
  }

  @Test
  fun `a stashed passphrase can be taken once`() {
    val token = ExportPassphrases.stash("plum orbit".toCharArray())
    assertArrayEquals("plum orbit".toCharArray(), ExportPassphrases.take(token))
    assertNull(ExportPassphrases.take(token))
  }

  @Test
  fun `discard wipes the passphrase it held`() {
    val passphrase = "plum orbit".toCharArray()
    val token = ExportPassphrases.stash(passphrase)
    ExportPassphrases.discard(token)
    assertArrayEquals(CharArray(passphrase.size), passphrase)
    assertNull(ExportPassphrases.take(token))
  }
}
