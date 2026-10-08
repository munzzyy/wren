// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.duress

import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256 of the certificates an installed app is signed with right now. Rotated-away
 * certificates are left out on purpose: a trigger that rotates its key has to be connected again.
 */
object SigningCertificates {

  fun sha256(packageManager: PackageManager, packageName: String): String? {
    return try {
      digestOf(signers(packageManager, packageName))
    } catch (e: PackageManager.NameNotFoundException) {
      null
    } catch (e: RuntimeException) {
      null
    }
  }

  /** Lowercase hex digests, sorted and joined with commas so the order of signers doesn't matter. */
  fun digestOf(certificates: List<ByteArray>): String? {
    if (certificates.isEmpty()) {
      return null
    }
    return certificates
      .map { cert -> MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it.toInt() and 0xff) } }
      .sorted()
      .joinToString(",")
  }

  /** One line per signer, uppercase byte pairs split by colons, the way keytool prints it. */
  fun format(digest: String): String {
    return digest.split(',').joinToString("\n") { hex ->
      hex.uppercase(Locale.ROOT).chunked(2).joinToString(":")
    }
  }

  @Suppress("DEPRECATION")
  private fun signers(packageManager: PackageManager, packageName: String): List<ByteArray> {
    return if (Build.VERSION.SDK_INT >= 28) {
      val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
      info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
    } else {
      val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
      info.signatures.orEmpty().map { it.toByteArray() }
    }
  }
}
