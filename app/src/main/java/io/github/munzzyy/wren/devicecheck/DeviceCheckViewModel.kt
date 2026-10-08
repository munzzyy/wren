// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DeviceCheckState(
  val snapshot: DeviceSnapshot,
  val results: List<CheckResult>,
  val plan: List<HardenedSetting>,
  val today: LocalDate
)

class DeviceCheckViewModel(private val repository: DeviceCheckRepository) : ViewModel() {

  private val store = MutableStateFlow(load())
  val state: StateFlow<DeviceCheckState> = store

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      store.value = load()
    }
  }

  fun toggle(setting: HardenedSetting) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.toggle(setting)
      store.value = load()
    }
  }

  fun applyHardenedDefaults(changes: List<HardenedSetting>, onApplied: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.applyHardenedDefaults(changes)
      store.value = load()
      launch(Dispatchers.Main) { onApplied() }
    }
  }

  private fun load(): DeviceCheckState {
    val snapshot = repository.snapshot()
    val today = LocalDate.now()
    return DeviceCheckState(
      snapshot = snapshot,
      results = DeviceChecks.evaluate(snapshot, today),
      plan = HardeningPlan.changesFor(snapshot),
      today = today
    )
  }
}
