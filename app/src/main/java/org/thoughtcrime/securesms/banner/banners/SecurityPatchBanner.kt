// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import io.github.munzzyy.wren.devicecheck.DeviceCheckActivity
import io.github.munzzyy.wren.devicecheck.DeviceCheckStore
import io.github.munzzyy.wren.devicecheck.PatchBannerPolicy
import io.github.munzzyy.wren.devicecheck.PatchLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import java.time.LocalDate

/**
 * Warns when the phone's Android security patch level is more than six months old.
 */
class SecurityPatchBanner(private val context: Context) : Banner<Int>() {

  private val store = DeviceCheckStore(context)

  private val patch: LocalDate? = PatchLevel.parse(Build.VERSION.SECURITY_PATCH)

  override val enabled: Boolean
    get() = PatchBannerPolicy.shouldShow(patch, LocalDate.now(), store.patchBannerDismissedAt, System.currentTimeMillis())

  override val dataFlow: Flow<Int>
    get() = flowOf(patch?.let { PatchLevel.monthsBehind(it, LocalDate.now()).toInt() } ?: 0)

  private val enabledState = MutableStateFlow(enabled)

  override val stateUpdates: Flow<Unit>
    get() = enabledState.map { }

  @Composable
  override fun DisplayBanner(model: Int, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      monthsBehind = model,
      onLearnMoreClicked = {
        context.startActivity(DeviceCheckActivity.createIntent(context))
      },
      onDismissed = {
        store.patchBannerDismissedAt = System.currentTimeMillis()
        enabledState.value = false
      }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, monthsBehind: Int, onLearnMoreClicked: () -> Unit = {}, onDismissed: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = pluralStringResource(R.plurals.DeviceCheck__security_updates_stopped_d_months_ago, monthsBehind, monthsBehind),
    importance = Importance.ERROR,
    onDismissListener = onDismissed,
    actions = listOf(
      Action(R.string.LearnMoreTextView_learn_more) {
        onLearnMoreClicked()
      }
    ),
    paddingValues = contentPadding
  )
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(
      contentPadding = PaddingValues(0.dp),
      monthsBehind = 14
    )
  }
}
