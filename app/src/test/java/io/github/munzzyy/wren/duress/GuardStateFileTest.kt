// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class GuardStateFileTest {

  @get:Rule
  val temp = TemporaryFolder()

  private fun stateFile(): GuardStateFile = GuardStateFile(File(temp.root, "no_backup/wren-guard-state"))

  @Test
  fun `a missing file reads as nothing recorded`() {
    assertEquals(GuardStateFile.State(), stateFile().read())
    assertFalse(stateFile().exists())
  }

  @Test
  fun `write then read round-trips and leaves no temp file`() {
    val file = stateFile()
    file.write(GuardStateFile.State(failedAttempts = 7, lastUnlockAt = 1_760_000_000_000L))

    assertEquals(GuardStateFile.State(7, 1_760_000_000_000L), stateFile().read())
    assertEquals(listOf("wren-guard-state"), File(temp.root, "no_backup").list()!!.toList())
  }

  @Test
  fun `a damaged file reads as nothing recorded`() {
    val raw = File(temp.root, "no_backup/wren-guard-state")
    raw.parentFile!!.mkdirs()
    raw.writeText("failed_attempts=lots\nlast_unlock_at=\ngarbage")

    assertEquals(GuardStateFile.State(), GuardStateFile(raw).read())
  }

  @Test
  fun `negative values are clamped`() {
    val raw = File(temp.root, "no_backup/wren-guard-state")
    raw.parentFile!!.mkdirs()
    raw.writeText("failed_attempts=-3\nlast_unlock_at=-9\n")

    assertEquals(GuardStateFile.State(0, 0L), GuardStateFile(raw).read())
  }

  @Test
  fun `migration copies the old values once and clears them after the write`() {
    val file = stateFile()
    var legacyReads = 0
    var cleared = 0

    val first = file.readOrMigrate(
      legacy = {
        legacyReads++
        GuardStateFile.State(failedAttempts = 3, lastUnlockAt = 42L)
      },
      clearLegacy = {
        assertTrue("cleared before the file was written", file.exists())
        cleared++
      }
    )

    assertEquals(GuardStateFile.State(3, 42L), first)
    assertEquals(GuardStateFile.State(3, 42L), stateFile().read())

    val second = file.readOrMigrate(legacy = { legacyReads++; GuardStateFile.State(99, 99L) }, clearLegacy = { cleared++ })
    assertEquals(GuardStateFile.State(3, 42L), second)
    assertEquals(1, legacyReads)
    assertEquals(1, cleared)
  }

  @Test
  fun `a failed migration keeps the old values`() {
    val blocker = File(temp.root, "no_backup")
    blocker.writeText("a file where the directory should be")
    val file = GuardStateFile(File(blocker, "wren-guard-state"))
    var cleared = false

    try {
      file.readOrMigrate(legacy = { GuardStateFile.State(4, 5L) }, clearLegacy = { cleared = true })
      fail("expected an IOException")
    } catch (e: IOException) {
      assertFalse(cleared)
    }
  }
}
