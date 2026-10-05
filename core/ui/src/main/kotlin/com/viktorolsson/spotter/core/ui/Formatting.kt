package com.viktorolsson.spotter.core.ui

import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.TIMED_EXERCISE_IDS
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

/** "82.5 kg × 6", or "12 reps" for bodyweight. A [timed] hold reads "45 s" or "10 kg × 45 s". */
fun formatSet(weightKg: Double?, reps: Int?, unit: WeightUnit, timed: Boolean = false): String {
    val r = reps ?: 0
    val amount = if (timed) "$r s" else "$r"
    return weightKg?.let { "${unit.format(it)} ${unit.label} × $amount" } ?: if (timed) amount else "$r reps"
}

/** A prescription: "3 × 8–12", or "3 × 30–60 s" for a [timed] hold. */
fun formatTarget(sets: Int, repMin: Int, repMax: Int, timed: Boolean = false): String =
    "$sets × $repMin–$repMax" + if (timed) " s" else ""

/** The headline value of a record: "101 kg", "82.5 kg × 9" or "2,080 kg". */
fun formatRecordValue(record: PersonalRecord, unit: WeightUnit): String = when (record.type) {
    PersonalRecordType.ESTIMATED_1RM -> "${unit.format(record.value).substringBefore('.')} ${unit.label}"
    PersonalRecordType.REPS_AT_WEIGHT -> formatSet(record.weightKg, record.reps, unit, record.exerciseId in TIMED_EXERCISE_IDS)
    PersonalRecordType.VOLUME -> formatTotal(record.value, unit)
}

fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "Sat 3 Oct" */
fun formatShortDate(date: LocalDate): String = date.format(shortDate)

fun formatMediumDate(date: LocalDate): String = date.format(mediumDate)
