/*
 * Copyright 2026 Cole Munz
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.gcm;

import android.content.Context;

import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Optional;

public final class FcmUtil {

  private FcmUtil() {}

  @WorkerThread
  public static Optional<String> getToken(Context context) {
    return Optional.empty();
  }

  @WorkerThread
  public static Optional<String> getTokenWithoutAvailCheck(Context context) {
    return Optional.empty();
  }

  @WorkerThread
  public static void deleteFirebaseInstallationId(Context context) throws IOException {
  }
}
