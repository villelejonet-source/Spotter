package com.viktorolsson.spotter.core.model

/**
 * Numbers people type: the one parser for every field in the app. Accepts a decimal comma
 * ("62,5", as Swedish keyboards type it) or point ("62.5"), and spaces around the number;
 * nothing else (no sign, exponent, grouping, "NaN" or "Infinity").
 */
sealed interface NumberInput {
    /** Nothing typed: the field is being cleared. */
    data object Blank : NumberInput

    /** Typed but not a number, such as "." on its own or "1,2,3": leave the stored value alone. */
    data object Invalid : NumberInput

    data class Value(val value: Double) : NumberInput

    companion object {
        private val DECIMAL = Regex("""\d+([.,]\d*)?|[.,]\d+""")
        private val WHOLE = Regex("""\d+""")

        /** A non-negative decimal number. */
        fun decimal(text: String): NumberInput {
            val trimmed = text.trim()
            return when {
                trimmed.isEmpty() -> Blank
                !DECIMAL.matches(trimmed) -> Invalid
                else -> trimmed.replace(',', '.').toDoubleOrNull()?.let(::Value) ?: Invalid
            }
        }

        /** A non-negative whole number; "8,0" or "8.5" are invalid, not rounded. */
        fun whole(text: String): NumberInput {
            val trimmed = text.trim()
            return when {
                trimmed.isEmpty() -> Blank
                !WHOLE.matches(trimmed) -> Invalid
                else -> trimmed.toIntOrNull()?.let { Value(it.toDouble()) } ?: Invalid
            }
        }

        /** [decimal] as a number, or null for blank or invalid text. */
        fun decimalOrNull(text: String): Double? = (decimal(text) as? Value)?.value

        /** [whole] as an Int, or null for blank or invalid text. */
        fun wholeOrNull(text: String): Int? = (whole(text) as? Value)?.value?.toInt()
    }
}
