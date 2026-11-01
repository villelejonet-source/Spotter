/*
 * What the export (app/export/route.ts) writes, from rows the views return. Kept apart from
 * the route so it can be tested: the views hand sets back in sync-id order, which is random,
 * so everything here puts them back in the order they were done.
 */
import type { SessionExerciseRow, SessionSummaryRow, SetEntryRow } from "./model";

/** A logged set as v_set_history returns it: with its workout and exercise. */
export type ExportSet = SetEntryRow & { session_id: string; exercise_id: string };

const iso = (ms: number | null) => (ms == null ? null : new Date(ms).toISOString());

/**
 * Every workout with its exercises in the order they were done, each with its own details
 * and its sets in order. An exercise with no sets logged is kept.
 */
export function exportWorkouts(sessions: SessionSummaryRow[], exercises: SessionExerciseRow[], sets: ExportSet[], exerciseName: (id: string) => string) {
  const setsByExercise = groupBy(sets, (s) => s.session_exercise_id);
  const exercisesBySession = groupBy(exercises, (e) => e.session_id);
  return sessions.map((w) => ({
    ...w,
    started_at_iso: iso(w.started_at),
    ended_at_iso: iso(w.ended_at),
    exercises: [...(exercisesBySession.get(w.sync_id) ?? [])]
      .sort((a, b) => a.position - b.position)
      .map((e) => ({
        exercise_id: e.exercise_id,
        exercise: exerciseName(e.exercise_id),
        position: e.position,
        substituted_from_exercise_id: e.substituted_from_exercise_id,
        substituted_from: e.substituted_from_exercise_id == null ? null : exerciseName(e.substituted_from_exercise_id),
        superset_group: e.superset_group,
        rest_seconds: e.rest_seconds,
        progression_reason: e.progression_reason,
        notes: e.notes,
        sets: [...(setsByExercise.get(e.sync_id) ?? [])]
          .sort((a, b) => a.position - b.position)
          .map((x) => ({ position: x.position, set_type: x.set_type, weight_kg: x.weight_kg, reps: x.reps, rir: x.rir, rpe: x.rpe, completed_at: iso(x.completed_at), notes: x.notes, is_amrap: x.is_amrap })),
      })),
  }));
}

/** One CSV row per set: by workout, then the exercise's place in it, then the set's. */
export function exportCsvRows(sessions: SessionSummaryRow[], exercises: SessionExerciseRow[], sets: ExportSet[]) {
  const sessionById = new Map(sessions.map((s) => [s.sync_id, s]));
  const positionOf = new Map(exercises.map((e) => [e.sync_id, e.position]));
  return sets
    .filter((s) => sessionById.has(s.session_id))
    .map((set) => ({ session: sessionById.get(set.session_id)!, exercisePosition: positionOf.get(set.session_exercise_id) ?? Number.MAX_SAFE_INTEGER, set }))
    .sort((a, b) => a.session.started_at - b.session.started_at || a.session.sync_id.localeCompare(b.session.sync_id) || a.exercisePosition - b.exercisePosition || a.set.position - b.set.position);
}

/** Map.groupBy, which Node 20 (Vercel's default runtime) doesn't have; keeps insertion order. */
function groupBy<T, K>(items: T[], key: (item: T) => K): Map<K, T[]> {
  const groups = new Map<K, T[]>();
  for (const item of items) {
    const k = key(item);
    const group = groups.get(k);
    if (group) group.push(item);
    else groups.set(k, [item]);
  }
  return groups;
}
