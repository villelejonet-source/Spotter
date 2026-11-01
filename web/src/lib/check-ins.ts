// How a workout felt, as the app has it (core/model CheckIns.kt); pinned by
// core/model/fixtures/training-math.json ("checkIns", "feelSummary").

/** Check-ins run 1 (low) to 5 (great); 1 or 2 is a low score, before or after a workout. */
export const LOW_CHECK_IN = 2;

/** Days the "How training felt" card looks back over, as on the app's Progress tab. */
export const FEEL_WINDOW_DAYS = 28;

/**
 * A workout done as planned on a low day: the check-in before or the rating after was low. It
 * never counts against progress. A lighter session isn't a rough day: it's left out altogether.
 */
export function isRoughDay(lighter: boolean, readiness: number | null, feel: number | null): boolean {
  return !lighter && ((readiness != null && readiness <= LOW_CHECK_IN) || (feel != null && feel <= LOW_CHECK_IN));
}

/** Whether a workout counts when comparing progress over time: not lighter, and not a rough day. */
export function countsTowardsChange(lighter: boolean, readiness: number | null, feel: number | null): boolean {
  return !lighter && !isRoughDay(lighter, readiness, feel);
}

export interface FeelSummary {
  /** Workouts with a check-in, a rating, or both. */
  rated: number;
  averageReadiness: number | null;
  averageFeel: number | null;
  /** Workouts with a low check-in or rating, lighter sessions included. */
  lowDays: number;
}

const average = (values: number[]) => (values.length ? values.reduce((a, b) => a + b, 0) / values.length : null);

/** From each finished workout's check-in and rating; null when none was rated. */
export function feelSummary(checkIns: { readiness: number | null; feel: number | null }[]): FeelSummary | null {
  const rated = checkIns.filter((c) => c.readiness != null || c.feel != null);
  if (!rated.length) return null;
  return {
    rated: rated.length,
    averageReadiness: average(rated.flatMap((c) => (c.readiness != null ? [c.readiness] : []))),
    averageFeel: average(rated.flatMap((c) => (c.feel != null ? [c.feel] : []))),
    lowDays: rated.filter((c) => (c.readiness ?? 5) <= LOW_CHECK_IN || (c.feel ?? 5) <= LOW_CHECK_IN).length,
  };
}
