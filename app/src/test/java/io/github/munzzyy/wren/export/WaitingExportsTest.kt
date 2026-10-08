// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitingExportsTest {

  private val tree = "content://com.android.externalstorage.documents/tree/primary%3AExports"
  private val otherTree = "content://com.android.externalstorage.documents/tree/primary%3ABackups"

  @Test
  fun `first export gives back a grant it took`() {
    assertTrue(WaitingExports.releaseWhenDone(alreadyHeld = false, otherExports = emptyList()))
  }

  @Test
  fun `first export keeps a grant the app already had`() {
    assertFalse(WaitingExports.releaseWhenDone(alreadyHeld = true, otherExports = emptyList()))
  }

  @Test
  fun `later export follows the earlier one even though the grant now looks held`() {
    assertTrue(WaitingExports.releaseWhenDone(alreadyHeld = true, otherExports = listOf(true)))
    assertFalse(WaitingExports.releaseWhenDone(alreadyHeld = true, otherExports = listOf(false)))
  }

  @Test
  fun `waiting jobs are counted per folder`() {
    val waiting = WaitingExports()
    waiting.add(tree, true)
    waiting.add(tree, true)
    waiting.add(otherTree, false)

    assertEquals(listOf(true, true), waiting.releaseFlags(tree))
    assertEquals(listOf(false), waiting.releaseFlags(otherTree))

    waiting.remove(tree)
    assertEquals(listOf(true), waiting.releaseFlags(tree))

    waiting.remove(tree)
    assertTrue(waiting.releaseFlags(tree).isEmpty())
    assertEquals(listOf(false), waiting.releaseFlags(otherTree))
  }

  @Test
  fun `removing more than were added does nothing`() {
    val waiting = WaitingExports()
    waiting.remove(tree)
    waiting.add(tree, false)
    waiting.remove(tree)
    waiting.remove(tree)

    assertTrue(waiting.releaseFlags(tree).isEmpty())
  }

  @Test
  fun `flags returned are a copy`() {
    val waiting = WaitingExports()
    waiting.add(tree, true)
    val flags = waiting.releaseFlags(tree)
    waiting.remove(tree)

    assertEquals(listOf(true), flags)
  }
}
