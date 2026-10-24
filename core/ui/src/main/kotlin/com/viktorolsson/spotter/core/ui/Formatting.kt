package com.viktorolsson.spotter.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.TIMED_EXERCISE_IDS
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.label
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

// Numbers and units. Every user-facing string goes through a resource so wording
// and plurals live in one place.

/** "82.5 kg" */
@Composable
fun formatWeight(kg: Double, unit: WeightUnit): String =
    stringResource(R.string.weight_with_unit, unit.format(kg), unit.label)

/** "72.4 kg": one decimal, for averages. */
@Composable
fun formatWeightOneDecimal(kg: Double, unit: WeightUnit): String =
    stringResource(
        R.string.weight_with_unit,
        BigDecimal(unit.fromKg(kg)).setScale(1, RoundingMode.HALF_UP).toPlainString(),
        unit.label,
    )

/** "101 kg": a whole number, for estimated maxes. */
@Composable
fun formatOneRepMax(kg: Double, unit: WeightUnit): String =
    stringResource(R.string.weight_with_unit, unit.fromKg(kg).roundToLong().toString(), unit.label)

/** "4,520 kg": whole units with grouping, for totals like volume. */
@Composable
fun formatTotal(kg: Double, unit: WeightUnit): String =
    stringResource(
        R.string.weight_with_unit,
        NumberFormat.getIntegerInstance().format(unit.fromKg(kg).roundToLong()),
        unit.label,
    )

/** "RIR 2", or "RPE 8" on the RPE scale. */
@Composable
fun formatEffort(rir: Int, scale: EffortScale): String =
    if (scale == EffortScale.RPE) stringResource(R.string.effort_rpe, scale.display(rir)) else stringResource(R.string.effort_rir, rir)

/**
 * "82.5 kg × 6", or "12 reps" for bodyweight. A [timed] hold reads "45 s" or
 * "10 kg × 45 s". With [withUnit] off the unit is left out ("82.5 × 6"), for
 * columns whose header already names it. A set with no reps logged shows just its
 * weight, or "—" when it has neither.
 */
@Composable
fun formatSet(weightKg: Double?, reps: Int?, unit: WeightUnit, timed: Boolean = false, withUnit: Boolean = true): String {
    if (reps == null) {
        return when {
            weightKg != null && withUnit -> formatWeight(weightKg, unit)
            weightKg != null -> unit.format(weightKg)
            else -> stringResource(R.string.value_none)
        }
    }
    val r = reps
    val amount = if (timed) stringResource(R.string.seconds_short, r) else r.toString()
    return when {
        weightKg != null -> stringResource(
            R.string.set_weight_by_amount,
            if (withUnit) formatWeight(weightKg, unit) else unit.format(weightKg),
            amount,
        )
        timed -> amount
        else -> pluralStringResource(R.plurals.reps_count, r, r)
    }
}

/** A prescription: "3 × 8–12", or "3 × 30–60 s" for a [timed] hold. */
@Composable
fun formatTarget(sets: Int, repMin: Int, repMax: Int, timed: Boolean = false): String =
    stringResource(if (timed) R.string.target_sets_seconds else R.string.target_sets_reps, sets, repMin, repMax)

/** The headline value of a record: "101 kg", "82.5 kg × 9" or "2,080 kg". */
@Composable
fun formatRecordValue(record: PersonalRecord, unit: WeightUnit): String = when (record.type) {
    PersonalRecordType.ESTIMATED_1RM -> formatOneRepMax(record.value, unit)
    PersonalRecordType.REPS_AT_WEIGHT -> formatSet(record.weightKg, record.reps, unit, record.exerciseId in TIMED_EXERCISE_IDS)
    PersonalRecordType.VOLUME -> formatTotal(record.value, unit)
}

/** "3 exercises" */
@Composable
fun formatExerciseCount(count: Int): String = pluralStringResource(R.plurals.exercise_count, count, count)

/** "18 sets" */
@Composable
fun formatSetCount(count: Int): String = pluralStringResource(R.plurals.sets_count, count, count)

/** "4 sessions" */
@Composable
fun formatSessionCount(count: Int): String = pluralStringResource(R.plurals.sessions_count, count, count)

// Dates. Patterns come from the locale (via date-time skeletons), so "Sat 3 Oct" is
// "Sat, Oct 3" for a US phone without any screen caring.

fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

private fun forSkeleton(skeleton: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return formatters.getOrPut("$locale|$skeleton") {
        runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
            .getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    }
}

/** "Sat 3 Oct" */
fun formatShortDate(date: LocalDate): String = date.format(forSkeleton("EEEdMMM"))

/** "3 Oct 2026" */
fun formatMediumDate(date: LocalDate): String = date.format(forSkeleton("dMMMy"))

/** "3 Oct", for chart axes. */
fun formatAxisDate(date: LocalDate): String = date.format(forSkeleton("dMMM"))

/** "October 2026" */
fun formatMonthYear(month: YearMonth): String = month.format(forSkeleton("yMMMM"))

/** "Sat 3 Oct, 14:05" */
@Composable
fun formatShortDateTime(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    return stringResource(R.string.date_at_time, formatShortDate(local.toLocalDate()), time)
}
