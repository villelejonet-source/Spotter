package com.viktorolsson.spotter.core.ui

import androidx.compose.runtime.Composable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSListFormatter
import platform.Foundation.NSLocale
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.uppercaseStringWithLocale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Instant

// Only the specifiers the shared code uses: %d with optional zero padding and width, %.Nf, %s, %%.
private val specifier = Regex("""%(0?)(\d*)(?:\.(\d+))?([dfs%])""")

actual fun String.localFormat(vararg args: Any): String {
    var next = 0
    return specifier.replace(this) { match ->
        val (zero, width, precision, conversion) = match.destructured
        if (conversion == "%") return@replace "%"
        val arg = args[next++]
        val text = when (conversion) {
            "d" -> arg.toString()
            "f" -> NSNumberFormatter().apply {
                locale = NSLocale.currentLocale
                usesGroupingSeparator = false
                roundingMode = NSNumberFormatterRoundHalfUp
                val digits = (precision.ifEmpty { "6" }).toULong()
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }.stringFromNumber(NSNumber(double = (arg as Number).toDouble())) ?: arg.toString()
            else -> arg.toString()
        }
        val w = width.toIntOrNull() ?: 0
        if (zero.isNotEmpty() && text.startsWith("-")) "-" + text.drop(1).padStart(w - 1, '0')
        else text.padStart(w, if (zero.isNotEmpty()) '0' else ' ')
    }
}

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun String.uppercaseLocal(): String = (this as NSString).uppercaseStringWithLocale(NSLocale.currentLocale)

private val utc = NSTimeZone.timeZoneForSecondsFromGMT(0)

internal actual fun formatDateSkeleton(date: LocalDate, skeleton: String): String {
    val locale = NSLocale.currentLocale
    val formatter = NSDateFormatter().apply {
        this.locale = locale
        timeZone = utc
        dateFormat = NSDateFormatter.dateFormatFromTemplate(skeleton, 0u, locale) ?: skeleton
    }
    // Midday UTC on that date, formatted in UTC: the calendar date never shifts.
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(date.toEpochDays() * 86_400.0 + 43_200.0))
}

internal actual fun formatShortTime(instant: Instant): String = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.localTimeZone
    dateStyle = NSDateFormatterNoStyle
    timeStyle = NSDateFormatterShortStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / 1000.0))

internal actual fun formatGroupedInteger(value: Long): String = NSNumberFormatter().apply {
    locale = NSLocale.currentLocale
    numberStyle = NSNumberFormatterDecimalStyle
    maximumFractionDigits = 0u
}.stringFromNumber(NSNumber(longLong = value)) ?: value.toString()

internal actual fun formatOneDecimalHalfUp(value: Double): String {
    val scaled = value * 10
    val tenths = (if (scaled >= 0) floor(scaled + 0.5) else ceil(scaled - 0.5)).toLong()
    val sign = if (tenths < 0) "-" else ""
    return "$sign${abs(tenths) / 10}.${abs(tenths) % 10}"
}

@Composable
internal actual fun formatList(items: List<String>): String = NSListFormatter.localizedStringByJoiningStrings(items)

// NSCalendar counts weekdays from Sunday = 1; ISO from Monday = 1.
@Composable
actual fun firstDayOfWeek(): DayOfWeek = DayOfWeek((NSCalendar.currentCalendar.firstWeekday.toInt() + 5) % 7 + 1)

@Composable
actual fun narrowDayName(day: DayOfWeek): String {
    val symbols = NSDateFormatter().apply { locale = NSLocale.currentLocale }.veryShortWeekdaySymbols
    return symbols[day.isoDayNumber % 7] as String
}

private fun plain(value: Double, decimals: Int): String = NSNumberFormatter().apply {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    usesGroupingSeparator = false
    roundingMode = NSNumberFormatterRoundHalfUp
    minimumFractionDigits = 0u
    maximumFractionDigits = decimals.toULong()
}.stringFromNumber(NSNumber(double = value)) ?: value.toString()

// Weights and plate sizes have at most a few decimals; 10 keeps every one of them exact.
actual fun plainNumber(value: Double): String = plain(value, 10)

actual fun plainNumberRounded(value: Double, decimals: Int): String = plain(value, decimals)
