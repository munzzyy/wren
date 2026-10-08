// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.viewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DeviceCheckActivity : PassphraseRequiredActivity() {

  companion object {
    fun createIntent(context: Context): Intent = Intent(context, DeviceCheckActivity::class.java)

    private const val ACTION_SYSTEM_UPDATE_SETTINGS = "android.settings.SYSTEM_UPDATE_SETTINGS"

    private val PHONE_CHECKS = listOf(CheckId.SECURITY_PATCH, CheckId.ANDROID_VERSION, CheckId.SCREEN_LOCK)
    private val WREN_CHECKS = listOf(CheckId.PASSPHRASE_LOCK, CheckId.DURESS_PASSPHRASE, CheckId.REGISTRATION_LOCK)
  }

  private val dynamicTheme = DynamicNoActionBarTheme()

  private val viewModel: DeviceCheckViewModel by viewModel {
    DeviceCheckViewModel(DeviceCheckRepository(this))
  }

  override fun onPreCreate() {
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      SignalTheme {
        DeviceCheckScreen(
          state = state,
          onNavigationClick = ::finish,
          onCheckClick = ::onCheckClick,
          onApplyClick = { confirmHardenedDefaults(state.plan) }
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
    viewModel.refresh()
  }

  private fun onCheckClick(id: CheckId) {
    when (id) {
      CheckId.SECURITY_PATCH,
      CheckId.ANDROID_VERSION -> openFirst(Intent(ACTION_SYSTEM_UPDATE_SETTINGS), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
      CheckId.SCREEN_LOCK -> openFirst(Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
      CheckId.PASSPHRASE_LOCK,
      CheckId.DURESS_PASSPHRASE -> startActivity(AppSettingsActivity.privacy(this))
      CheckId.REGISTRATION_LOCK -> startActivity(AppSettingsActivity.account(this))
      CheckId.NOTIFICATION_PRIVACY -> startActivity(AppSettingsActivity.notifications(this))
      CheckId.ORBOT -> onOrbotClick()
      else -> HardenedSetting.forCheck(id)?.let { viewModel.toggle(it) }
    }
  }

  private fun onOrbotClick() {
    if (viewModel.state.value.snapshot.routedThroughOrbot) {
      startActivity(AppSettingsActivity.proxy(this))
      return
    }

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.DeviceCheck__route_through_orbot_title)
      .setMessage(R.string.DeviceCheck__route_through_orbot_message)
      .setPositiveButton(R.string.DeviceCheck__route_through_orbot) { _, _ -> viewModel.toggle(HardenedSetting.ORBOT) }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun openFirst(vararg intents: Intent) {
    for (intent in intents) {
      try {
        startActivity(intent)
        return
      } catch (e: ActivityNotFoundException) {
        continue
      }
    }
  }

  private fun confirmHardenedDefaults(plan: List<HardenedSetting>) {
    if (plan.isEmpty()) {
      return
    }

    val lines = plan.joinToString("\n") { "• " + getString(changeLabel(it)) }

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.DeviceCheck__apply_hardened_defaults_title)
      .setMessage(getString(R.string.DeviceCheck__apply_hardened_defaults_message, lines))
      .setPositiveButton(R.string.DeviceCheck__apply) { _, _ ->
        viewModel.applyHardenedDefaults(plan) {
          Toast.makeText(this, R.string.DeviceCheck__hardened_defaults_applied, Toast.LENGTH_SHORT).show()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  @Composable
  private fun DeviceCheckScreen(
    state: DeviceCheckState,
    onNavigationClick: () -> Unit,
    onCheckClick: (CheckId) -> Unit,
    onApplyClick: () -> Unit
  ) {
    val byId = state.results.associateBy { it.id }

    Scaffolds.Settings(
      title = stringResource(R.string.DeviceCheck__check_this_device),
      onNavigationClick = onNavigationClick,
      navigationIcon = SignalIcons.ArrowStart.imageVector
    ) { paddingValues ->
      LazyColumn(
        modifier = Modifier
          .padding(paddingValues)
          .then(rememberStatusBarColorNestedScrollModifier())
      ) {
        item {
          Text(
            text = stringResource(R.string.DeviceCheck__d_of_d_checks_pass, DeviceChecks.countGood(state.results), state.results.size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
          )
        }

        section(R.string.DeviceCheck__this_phone, PHONE_CHECKS, byId, state, onCheckClick)
        section(R.string.DeviceCheck__wren, WREN_CHECKS, byId, state, onCheckClick)
        section(R.string.DeviceCheck__privacy, CheckId.entries - PHONE_CHECKS.toSet() - WREN_CHECKS.toSet(), byId, state, onCheckClick)

        item {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 24.dp)
          ) {
            Buttons.LargeTonal(
              onClick = onApplyClick,
              enabled = state.plan.isNotEmpty(),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(text = stringResource(R.string.DeviceCheck__apply_hardened_defaults))
            }

            if (state.plan.isEmpty()) {
              Text(
                text = stringResource(R.string.DeviceCheck__hardened_defaults_already_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
              )
            }
          }
        }
      }
    }
  }

  private fun LazyListScope.section(
    header: Int,
    ids: List<CheckId>,
    byId: Map<CheckId, CheckResult>,
    state: DeviceCheckState,
    onCheckClick: (CheckId) -> Unit
  ) {
    item {
      Dividers.Default()
      Texts.SectionHeader(stringResource(header))
    }

    for (id in ids) {
      val result = byId[id] ?: continue
      item(key = id.name) {
        val setting = HardenedSetting.forCheck(id)
        val clickable = setting == null || setting.canChange(state.snapshot)

        Rows.TextRow(
          icon = statusIcon(result.status),
          iconTint = statusTint(result.status),
          text = stringResource(titleOf(id)),
          label = verdictOf(result, state),
          onClick = if (clickable) ({ onCheckClick(id) }) else null
        )
      }
    }
  }

  @Composable
  private fun statusIcon(status: CheckStatus): ImageVector {
    return when (status) {
      CheckStatus.GOOD -> SignalIcons.CheckCircle.imageVector
      CheckStatus.WARNING -> ImageVector.vectorResource(R.drawable.symbol_error_triangle_fill_24)
      CheckStatus.ALERT -> SignalIcons.ErrorCircle.imageVector
    }
  }

  @Composable
  private fun statusTint(status: CheckStatus): Color {
    return when (status) {
      CheckStatus.GOOD -> MaterialTheme.colorScheme.primary
      CheckStatus.WARNING -> SignalTheme.colors.colorOnWarning
      CheckStatus.ALERT -> MaterialTheme.colorScheme.error
    }
  }

  private fun titleOf(id: CheckId): Int {
    return when (id) {
      CheckId.SECURITY_PATCH -> R.string.DeviceCheck__security_patch
      CheckId.ANDROID_VERSION -> R.string.DeviceCheck__android_version
      CheckId.SCREEN_LOCK -> R.string.DeviceCheck__screen_lock
      CheckId.PASSPHRASE_LOCK -> R.string.DeviceCheck__passphrase_lock
      CheckId.DURESS_PASSPHRASE -> R.string.PrivacySettingsFragment__duress_passphrase
      CheckId.REGISTRATION_LOCK -> R.string.preferences_app_protection__registration_lock
      CheckId.NOTIFICATION_PRIVACY -> R.string.DeviceCheck__notification_content
      CheckId.SCREEN_SECURITY -> R.string.preferences__screen_security
      CheckId.INCOGNITO_KEYBOARD -> R.string.preferences__incognito_keyboard
      CheckId.LINK_PREVIEWS -> R.string.preferences__generate_link_previews
      CheckId.READ_RECEIPTS -> R.string.preferences__read_receipts
      CheckId.TYPING_INDICATORS -> R.string.preferences__typing_indicators
      CheckId.BLOCK_UNKNOWN -> R.string.preferences__block_unknown
      CheckId.ORBOT -> R.string.DeviceCheck__route_through_orbot
    }
  }

  @Composable
  private fun verdictOf(result: CheckResult, state: DeviceCheckState): String {
    val snapshot = state.snapshot
    val good = result.status == CheckStatus.GOOD

    return when (result.id) {
      CheckId.SECURITY_PATCH -> {
        val patch = PatchLevel.parse(snapshot.securityPatch)
        if (patch == null) {
          stringResource(R.string.DeviceCheck__patch_unknown)
        } else {
          val date = patch.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
          val months = PatchLevel.monthsBehind(patch, state.today).toInt()
          when (result.status) {
            CheckStatus.GOOD -> stringResource(R.string.DeviceCheck__patch_recent, date)
            CheckStatus.WARNING -> pluralStringResource(R.plurals.DeviceCheck__patch_from_s_d_months_old, months, date, months)
            CheckStatus.ALERT -> pluralStringResource(R.plurals.DeviceCheck__patch_from_s_d_months_old_updates_stopped, months, date, months)
          }
        }
      }
      CheckId.ANDROID_VERSION -> stringResource(if (good) R.string.DeviceCheck__android_s else R.string.DeviceCheck__android_s_unsupported, snapshot.androidRelease)
      CheckId.SCREEN_LOCK -> stringResource(if (good) R.string.DeviceCheck__screen_lock_on else R.string.DeviceCheck__screen_lock_off)
      CheckId.PASSPHRASE_LOCK -> stringResource(if (good) R.string.DeviceCheck__passphrase_lock_on else R.string.DeviceCheck__passphrase_lock_off)
      CheckId.DURESS_PASSPHRASE -> stringResource(if (good) R.string.DeviceCheck__duress_on else R.string.DeviceCheck__duress_off)
      CheckId.REGISTRATION_LOCK -> stringResource(if (good) R.string.DeviceCheck__registration_lock_on else R.string.DeviceCheck__registration_lock_off)
      CheckId.NOTIFICATION_PRIVACY -> stringResource(
        when (snapshot.notificationPrivacy) {
          DeviceChecks.NOTIFICATIONS_SHOW_NOTHING -> R.string.DeviceCheck__notifications_hidden
          DeviceChecks.NOTIFICATIONS_SHOW_NAME -> R.string.DeviceCheck__notifications_name
          else -> R.string.DeviceCheck__notifications_all
        }
      )
      CheckId.SCREEN_SECURITY -> stringResource(if (good) R.string.DeviceCheck__screen_security_on else R.string.DeviceCheck__screen_security_off)
      CheckId.INCOGNITO_KEYBOARD -> stringResource(if (good) R.string.DeviceCheck__incognito_keyboard_on else R.string.DeviceCheck__incognito_keyboard_off)
      CheckId.LINK_PREVIEWS -> stringResource(if (good) R.string.DeviceCheck__off else R.string.DeviceCheck__link_previews_on)
      CheckId.READ_RECEIPTS -> stringResource(if (good) R.string.DeviceCheck__off else R.string.DeviceCheck__read_receipts_on)
      CheckId.TYPING_INDICATORS -> stringResource(if (good) R.string.DeviceCheck__off else R.string.DeviceCheck__typing_indicators_on)
      CheckId.BLOCK_UNKNOWN -> stringResource(if (good) R.string.DeviceCheck__on else R.string.DeviceCheck__block_unknown_off)
      CheckId.ORBOT -> stringResource(if (good) R.string.DeviceCheck__orbot_on else R.string.DeviceCheck__orbot_off)
    }
  }

  private fun changeLabel(setting: HardenedSetting): Int {
    return when (setting) {
      HardenedSetting.SCREEN_SECURITY -> R.string.DeviceCheck__turn_on_screen_security
      HardenedSetting.INCOGNITO_KEYBOARD -> R.string.DeviceCheck__turn_on_incognito_keyboard
      HardenedSetting.NOTIFICATION_PRIVACY -> R.string.DeviceCheck__hide_names_and_messages_in_notifications
      HardenedSetting.LINK_PREVIEWS -> R.string.DeviceCheck__turn_off_link_previews
      HardenedSetting.READ_RECEIPTS -> R.string.DeviceCheck__turn_off_read_receipts
      HardenedSetting.TYPING_INDICATORS -> R.string.DeviceCheck__turn_off_typing_indicators
      HardenedSetting.BLOCK_UNKNOWN -> R.string.DeviceCheck__turn_on_block_unknown
      HardenedSetting.ORBOT -> R.string.DeviceCheck__route_through_orbot
    }
  }
}
