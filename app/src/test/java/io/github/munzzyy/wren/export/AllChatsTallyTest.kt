// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import org.junit.Assert.assertEquals
import org.junit.Test

class AllChatsTallyTest {

  @Test
  fun `any written chat keeps the export`() {
    assertEquals(AllChatsTally.Outcome.WROTE_SOME, AllChatsTally(written = 1).outcome())
    assertEquals(AllChatsTally.Outcome.WROTE_SOME, AllChatsTally(written = 3, skipped = 2, failed = 5).outcome())
  }

  @Test
  fun `only failures is a failed export`() {
    assertEquals(AllChatsTally.Outcome.ALL_FAILED, AllChatsTally(failed = 1).outcome())
    assertEquals(AllChatsTally.Outcome.ALL_FAILED, AllChatsTally(skipped = 4, failed = 1).outcome())
  }

  @Test
  fun `only empty chats means there was nothing to export`() {
    assertEquals(AllChatsTally.Outcome.NOTHING_TO_EXPORT, AllChatsTally().outcome())
    assertEquals(AllChatsTally.Outcome.NOTHING_TO_EXPORT, AllChatsTally(skipped = 7).outcome())
  }
}
