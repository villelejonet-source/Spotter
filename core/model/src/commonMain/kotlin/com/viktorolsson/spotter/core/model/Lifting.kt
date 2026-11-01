package com.viktorolsson.spotter.core.model

/*
 * The training arithmetic, defined once for the app. The website repeats it in TypeScript
 * (web/src/lib/model.ts `epley`, web/src/lib/stats.ts) and the SQL views repeat the volume
 * rule (v_session_summary); core/model/fixtures/training-math.json holds the cases all of
 * them must agree on, and both test suites check it.
 *
 * Rules:
 * - Warm-ups never count, and neither does a set with no reps (0, a failed attempt, or left
 *   blank): it lifted nothing, so it has no 1RM, isn't the best set and sets no record.
 * - Volume is weight × reps; a bodyweight set (null weight) adds nothing. Holds (timed
 *   exercises, whose reps are seconds) have no volume.
 * - The estimated 1RM is Epley's, and a single is its own 1RM. Bodyweight sets and holds have none.
 * - The best set is the heaviest, then the most reps at that weight.
 */

/** A set that lifted something: at least one rep (see the rules above). */
fun isLift(reps: Int?): Boolean = reps != null && reps > 0

/** Epley estimated one-rep max; a single is its own 1RM. Only for sets that are [isLift]. */
fun epley(weightKg: Double, reps: Int): Double = if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

/** kg moved in one set: weight × reps, with bodyweight (null weight) and missing reps as 0. */
fun setVolumeKg(weightKg: Double?, reps: Int?): Double = (weightKg ?: 0.0) * (reps ?: 0)

/** Orders sets by load: heavier first counts as better, then more reps (for maxWith). */
fun <T> bestSetOrder(weightKg: (T) -> Double?, reps: (T) -> Int?): Comparator<T> =
    compareBy<T>({ weightKg(it) ?: 0.0 }, { reps(it) ?: 0 })
