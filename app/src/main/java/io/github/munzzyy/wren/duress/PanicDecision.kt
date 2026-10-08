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
  ASK_USER,
  ALREADY_CONNECTED,
  REFUSE
}

enum class PanicConfirmResult {
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
   * A first connect is never stored until the user allows it.
   */
  fun connect(connectedPackage: String?, callingPackage: String?, ownPackage: String): PanicConnectResult {
    return when {
      callingPackage.isNullOrEmpty() || callingPackage == ownPackage -> PanicConnectResult.REFUSE
      connectedPackage.isNullOrEmpty() -> PanicConnectResult.ASK_USER
      connectedPackage == callingPackage -> PanicConnectResult.ALREADY_CONNECTED
      else -> PanicConnectResult.REFUSE
    }
  }

  /** The stored trigger is read again at answer time, so a trigger connected meanwhile still wins. */
  fun confirm(connectedPackage: String?, pendingPackage: String?, ownPackage: String, allowed: Boolean): PanicConfirmResult {
    if (!allowed) {
      return PanicConfirmResult.REFUSE
    }
    return when (connect(connectedPackage, pendingPackage, ownPackage)) {
      PanicConnectResult.ASK_USER -> PanicConfirmResult.CONNECT
      PanicConnectResult.ALREADY_CONNECTED -> PanicConfirmResult.ALREADY_CONNECTED
      PanicConnectResult.REFUSE -> PanicConfirmResult.REFUSE
    }
  }

  fun isConnectedCaller(connectedPackage: String?, callingPackage: String?): Boolean {
    return !connectedPackage.isNullOrEmpty() && connectedPackage == callingPackage
  }

  /**
   * The package name alone is not enough: an app reinstalled under the trigger's name with
   * another key is a [CallerCheck.MISMATCH], and so is a connection stored without a digest.
   */
  fun checkCaller(connectedPackage: String?, connectedDigest: String?, callingPackage: String?, callingDigest: String?): CallerCheck {
    return when {
      !isConnectedCaller(connectedPackage, callingPackage) -> CallerCheck.OTHER
      connectedDigest.isNullOrEmpty() || connectedDigest != callingDigest -> CallerCheck.MISMATCH
      else -> CallerCheck.CONNECTED
    }
  }

  /** A mismatched caller gets what any other app gets, and the stored trigger is dropped. */
  fun onTrigger(
    action: PanicAction,
    connectedPackage: String?,
    connectedDigest: String?,
    callingPackage: String?,
    callingDigest: String?,
    passphraseLockEnabled: Boolean
  ): TriggerOutcome {
    val check = checkCaller(connectedPackage, connectedDigest, callingPackage, callingDigest)
    val verifiedCaller = callingPackage.takeIf { check == CallerCheck.CONNECTED }
    return TriggerOutcome(
      response = decide(action, connectedPackage, verifiedCaller, passphraseLockEnabled),
      disconnect = check == CallerCheck.MISMATCH
    )
  }

  /** Connect from the trigger's package with another key drops the stored trigger and is refused. */
  fun onConnect(connectedPackage: String?, connectedDigest: String?, callingPackage: String?, callingDigest: String?, ownPackage: String): ConnectOutcome {
    if (checkCaller(connectedPackage, connectedDigest, callingPackage, callingDigest) == CallerCheck.MISMATCH) {
      return ConnectOutcome(PanicConnectResult.REFUSE, disconnect = true)
    }
    return ConnectOutcome(connect(connectedPackage, callingPackage, ownPackage), disconnect = false)
  }

  /** Disconnect from the trigger's package goes through whether or not the key still matches. */
  fun shouldDisconnect(connectedPackage: String?, connectedDigest: String?, callingPackage: String?, callingDigest: String?): Boolean {
    return checkCaller(connectedPackage, connectedDigest, callingPackage, callingDigest) != CallerCheck.OTHER
  }

  /** [pendingDigest] is read again at answer time; a key that changed while the prompt was open is refused. */
  fun confirm(connectedPackage: String?, pendingPackage: String?, shownDigest: String?, pendingDigest: String?, ownPackage: String, allowed: Boolean): PanicConfirmResult {
    if (shownDigest.isNullOrEmpty() || shownDigest != pendingDigest) {
      return PanicConfirmResult.REFUSE
    }
    return confirm(connectedPackage, pendingPackage, ownPackage, allowed)
  }
}

enum class CallerCheck {
  CONNECTED,
  MISMATCH,
  OTHER
}

data class TriggerOutcome(val response: PanicResponse, val disconnect: Boolean)

data class ConnectOutcome(val result: PanicConnectResult, val disconnect: Boolean)
