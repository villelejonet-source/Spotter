package com.viktorolsson.spotter.core.ui

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// Formatting that follows the phone's locale. Android keeps the java.text / java.time / ICU
// formatters it has always used; iOS uses Foundation's.

/** `String.format` in the phone's locale, for the specifiers the app uses: `%d`, `%02d`, `%.1f`, `%s`. */
expect fun String.localFormat(vararg args: Any): String

/** Upper case by the phone's locale's rules. */
expect fun String.uppercaseLocal(): String

/** [date] in the locale's best pattern for a date-time [skeleton] such as "EEEdMMM". */
internal expect fun formatDateSkeleton(date: LocalDate, skeleton: String): String

/** The time of day of [instant] in the phone's zone, in the locale's short style: "14:05", "2:05 PM". */
internal expect fun formatShortTime(instant: Instant): String

/** "4,520": a whole number with the locale's grouping. */
internal expect fun formatGroupedInteger(value: Long): String

/** "72.4": one decimal, rounded half up, always with a point. */
internal expect fun formatOneDecimalHalfUp(value: Double): String

/** "Rows and curls": the locale's own list joiner. */
@Composable
internal expect fun formatList(items: List<String>): String
