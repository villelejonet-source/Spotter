package com.viktorolsson.spotter.core.model

/** Check-ins run 1 (low) to 5 (great); 1 or 2 is a low score, before or after a workout. */
const val LOW_CHECK_IN = 2

/**
 * A workout done as planned on a low day: the check-in before it or the rating after it was
 * low. A rough day can count towards a weight increase but never against progress (a miss, a
 * hold, a plateau or a drop). A lighter session isn't a rough day: it's left out altogether.
 */
fun isRoughDay(lighter: Boolean, readiness: Int?, feel: Int?): Boolean =
    !lighter && ((readiness != null && readiness <= LOW_CHECK_IN) || (feel != null && feel <= LOW_CHECK_IN))

/** Whether a workout counts when comparing progress over time: not lighter, and not a rough day. */
fun countsTowardsChange(lighter: Boolean, readiness: Int?, feel: Int?): Boolean =
    !lighter && !isRoughDay(lighter, readiness, feel)

/** Days the "How training felt" card looks back over, on the Progress tab and the website. */
const val FEEL_WINDOW_DAYS = 28

/** How recent workouts felt: average check-in before and rating after, and how many were low days. */
data class FeelSummary(
    /** Workouts with a check-in, a rating, or both. */
    val rated: Int,
    /** Average check-in before (1–5), null when none was given. */
    val averageReadiness: Double?,
    /** Average rating after (1–5), null when none was given. */
    val averageFeel: Double?,
    /** Workouts with a low check-in or rating (1–2), lighter sessions included. */
    val lowDays: Int,
) {
    companion object {
        /** From each finished workout's (readiness, feel); null when none was rated. */
        fun of(checkIns: List<Pair<Int?, Int?>>): FeelSummary? {
            val rated = checkIns.filter { (readiness, feel) -> readiness != null || feel != null }
            if (rated.isEmpty()) return null
            return FeelSummary(
                rated = rated.size,
                averageReadiness = rated.mapNotNull { it.first }.takeIf { it.isNotEmpty() }?.average(),
                averageFeel = rated.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average(),
                lowDays = rated.count { (readiness, feel) -> (readiness ?: 5) <= LOW_CHECK_IN || (feel ?: 5) <= LOW_CHECK_IN },
            )
        }
    }
}
