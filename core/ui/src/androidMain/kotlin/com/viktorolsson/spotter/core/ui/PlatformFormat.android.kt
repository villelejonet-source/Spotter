package com.viktorolsson.spotter.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaLocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import kotlin.time.toJavaInstant

actual fun String.localFormat(vararg args: Any): String = format(*args)

actual fun String.uppercaseLocal(): String = uppercase(Locale.getDefault())

private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

private fun forSkeleton(skeleton: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return formatters.getOrPut("$locale|$skeleton") {
        runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
            .getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    }
}

internal actual fun formatDateSkeleton(date: LocalDate, skeleton: String): String =
    date.toJavaLocalDate().format(forSkeleton(skeleton))

internal actual fun formatShortTime(instant: Instant): String =
    instant.toJavaInstant().atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

internal actual fun formatGroupedInteger(value: Long): String = NumberFormat.getIntegerInstance().format(value)

internal actual fun formatOneDecimalHalfUp(value: Double): String =
    BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toPlainString()

@Composable
internal actual fun formatList(items: List<String>): String =
    android.icu.text.ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(items)

@Composable
actual fun firstDayOfWeek(): DayOfWeek {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value) }
}

@Composable
actual fun narrowDayName(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.isoDayNumber).getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0])

actual fun plainNumber(value: Double): String = BigDecimal(value).stripTrailingZeros().toPlainString()

actual fun plainNumberRounded(value: Double, decimals: Int): String =
    BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
