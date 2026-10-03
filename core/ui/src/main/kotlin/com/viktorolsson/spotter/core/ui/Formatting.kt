package com.viktorolsson.spotter.core.ui

import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.label
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

/** "4,520 kg": whole units with grouping, for totals like volume. */
fun formatTotal(kg: Double, unit: WeightUnit): String =
    "${NumberFormat.getIntegerInstance().format(unit.fromKg(kg).roundToLong())} ${unit.label}"

/** "82.5 kg × 6", or "12 reps" for bodyweight. */
fun formatSet(weightKg: Double?, reps: Int?, unit: WeightUnit): String {
    val r = reps ?: 0
    return weightKg?.let { "${unit.format(it)} ${unit.label} × $r" } ?: "$r reps"
}

/** The headline value of a record: "101 kg", "82.5 kg × 9" or "2,080 kg". */
fun formatRecordValue(record: PersonalRecord, unit: WeightUnit): String = when (record.type) {
    PersonalRecordType.ESTIMATED_1RM -> "${unit.format(record.value).substringBefore('.')} ${unit.label}"
    PersonalRecordType.REPS_AT_WEIGHT -> formatSet(record.weightKg, record.reps, unit)
    PersonalRecordType.VOLUME -> formatTotal(record.value, unit)
}

fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "Sat 3 Oct" */
fun formatShortDate(date: LocalDate): String = date.format(shortDate)

fun formatMediumDate(date: LocalDate): String = date.format(mediumDate)
