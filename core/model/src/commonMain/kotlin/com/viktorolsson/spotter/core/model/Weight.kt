package com.viktorolsson.spotter.core.model

import kotlin.math.abs
import kotlin.math.floor

private const val LB_PER_KG = 2.2046226218

fun WeightUnit.fromKg(kg: Double): Double = when (this) {
    WeightUnit.KG -> kg
    WeightUnit.LB -> kg * LB_PER_KG
}

fun WeightUnit.toKg(value: Double): Double = when (this) {
    WeightUnit.KG -> value
    WeightUnit.LB -> value / LB_PER_KG
}

val WeightUnit.label: String
    get() = when (this) {
        WeightUnit.KG -> "kg"
        WeightUnit.LB -> "lb"
    }

/** Formats a kg value in [unit] with at most two decimals and no trailing zeros: 80, 82.5, 102.06. */
fun WeightUnit.format(kg: Double): String = formatTwoDecimals(fromKg(kg))

/** [value] rounded half-up to two decimals, without trailing zeros or exponent: 102.06, 82.5, 80. */
internal fun formatTwoDecimals(value: Double): String {
    val hundredths = floor(abs(value) * 100 + 0.5).toLong()
    val sign = if (value < 0 && hundredths != 0L) "-" else ""
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

/** Parses user input ("82,5" or "82.5") in [unit] to kg; blank or invalid gives null. See [NumberInput]. */
fun WeightUnit.parseToKg(text: String): Double? = NumberInput.decimalOrNull(text)?.let(::toKg)
