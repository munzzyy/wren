// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import android.content.Context
import android.content.SharedPreferences

class DeviceCheckStore(context: Context) {

  companion object {
    private const val PREFERENCES_NAME = "wren-device-check"
    private const val PATCH_BANNER_DISMISSED_AT = "patch_banner_dismissed_at"
  }

  private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  var patchBannerDismissedAt: Long
    get() = prefs.getLong(PATCH_BANNER_DISMISSED_AT, 0)
    set(value) = prefs.edit().putLong(PATCH_BANNER_DISMISSED_AT, value).apply()
}
