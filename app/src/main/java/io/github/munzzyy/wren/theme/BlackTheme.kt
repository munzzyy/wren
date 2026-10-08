// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.theme

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import org.signal.core.ui.util.ThemeUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.util.TextSecurePreferences

object BlackTheme {

  @JvmStatic
  fun isSelected(context: Context): Boolean {
    return TextSecurePreferences.getTheme(context) == SettingsValues.Theme.BLACK.serialize()
  }

  @JvmStatic
  fun applyIfSelected(activity: Activity) {
    if (isSelected(activity) && ThemeUtil.isDarkTheme(activity)) {
      applyTo(activity.theme)
    }
  }

  @JvmStatic
  fun applyTo(theme: Resources.Theme) {
    theme.applyStyle(R.style.ThemeOverlay_Wren_Black, true)
  }

  // onActivityCreated fires inside Activity.onCreate: after onPreCreate's setTheme, before content is inflated.
  object ActivityCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = applyIfSelected(activity)
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
  }
}
