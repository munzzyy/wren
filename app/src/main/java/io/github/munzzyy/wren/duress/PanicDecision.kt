// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

enum class PanicAction(val key: String) {
  LOCK("lock"),
  WIPE("wipe");

  companion object {
    fun fromKey(key: String?): PanicAction = entries.firstOrNull { it.key == key } ?: LOCK
  }
}

enum class PanicResponse {
  LOCK,
  WIPE,
  NOTHING
}

enum class PanicConnectResult {
  CONNECT,
  ALREADY_CONNECTED,
  REFUSE
}

object PanicDecision {

  /** Only the connected trigger app can cause a wipe; any other caller gets a lock at most. */
  fun decide(action: PanicAction, connectedPackage: String?, callingPackage: String?, passphraseLockEnabled: Boolean): PanicResponse {
    return when {
      action == PanicAction.WIPE && isConnectedCaller(connectedPackage, callingPackage) -> PanicResponse.WIPE
      passphraseLockEnabled -> PanicResponse.LOCK
      else -> PanicResponse.NOTHING
    }
  }

  /**
   * A different app cannot replace the connected trigger; the user has to
   * disconnect it in settings first, so no app can quietly take over a wipe.
   */
  fun connect(connectedPackage: String?, callingPackage: String?, ownPackage: String): PanicConnectResult {
    return when {
      callingPackage.isNullOrEmpty() || callingPackage == ownPackage -> PanicConnectResult.REFUSE
      connectedPackage.isNullOrEmpty() -> PanicConnectResult.CONNECT
      connectedPackage == callingPackage -> PanicConnectResult.ALREADY_CONNECTED
      else -> PanicConnectResult.REFUSE
    }
  }

  fun isConnectedCaller(connectedPackage: String?, callingPackage: String?): Boolean {
    return !connectedPackage.isNullOrEmpty() && connectedPackage == callingPackage
  }
}
