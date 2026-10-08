// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only
package io.github.munzzyy.wren.devicecheck

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

object PatchLevel {

  const val WARN_AFTER_MONTHS = 3L
  const val ALERT_AFTER_MONTHS = 6L

  private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

  fun parse(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) {
      return null
    }

    return try {
      LocalDate.parse(raw.trim(), FORMAT)
    } catch (e: DateTimeParseException) {
      null
    }
  }

  fun monthsBehind(patch: LocalDate, today: LocalDate): Long {
    return if (patch.isAfter(today)) 0 else ChronoUnit.MONTHS.between(patch, today)
  }

  fun status(patch: LocalDate?, today: LocalDate): CheckStatus {
    return when {
      patch == null -> CheckStatus.WARNING
      patch.isBefore(today.minusMonths(ALERT_AFTER_MONTHS)) -> CheckStatus.ALERT
      patch.isBefore(today.minusMonths(WARN_AFTER_MONTHS)) -> CheckStatus.WARNING
      else -> CheckStatus.GOOD
    }
  }
}
