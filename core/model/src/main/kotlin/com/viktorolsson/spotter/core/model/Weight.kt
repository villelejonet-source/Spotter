package com.viktorolsson.spotter.core.model

import java.math.BigDecimal
import java.math.RoundingMode

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
fun WeightUnit.format(kg: Double): String =
    BigDecimal(fromKg(kg)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Parses user input ("82,5" or "82.5") in [unit] to kg; blank or invalid gives null. */
fun WeightUnit.parseToKg(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let(::toKg)
