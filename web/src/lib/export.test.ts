import { describe, expect, it } from "vitest";
import { exportCsvRows, exportWorkouts } from "./export";
import type { SessionExerciseRow, SessionSummaryRow } from "./model";

const name = (id: string) => `name of ${id}`;
const session = (sync_id: string, started_at: number): SessionSummaryRow => ({
  sync_id, started_at, ended_at: started_at + 3_600_000, plan_day_id: null, plan_day_name: "Push", plan_id: null,
  is_deload: false, notes: null, exercise_count: 2, completed_sets: 3, volume_kg: 0, readiness: null, feel: null, perceived_difficulty: null,
});
const exercise = (sync_id: string, session_id: string, exercise_id: string, position: number, extra: Partial<SessionExerciseRow> = {}): SessionExerciseRow => ({
  sync_id, session_id, exercise_id, position, substituted_from_exercise_id: null, superset_group: null, notes: null,
  rest_seconds: null, progression_reason: null, ...extra,
});
const set = (sync_id: string, session_exercise_id: string, session_id: string, exercise_id: string, position: number) => ({
  sync_id, session_exercise_id, session_id, exercise_id, position, weight_kg: 60, reps: 8, rir: null, rpe: null,
  set_type: "WORKING" as const, completed_at: 1, notes: null,
});

// Sets come back from the database ordered by sync id, which is random.
const sessions = [session("w1", 1_000)];
const exercises = [
  exercise("e-squat", "w1", "back-squat", 1),
  exercise("e-bench", "w1", "barbell-bench-press", 0, { notes: "Felt good", superset_group: 1, rest_seconds: 120, progression_reason: "ADD_REPS" }),
  exercise("e-row", "w1", "barbell-row", 2),
];
const sets = [
  set("s3", "e-squat", "w1", "back-squat", 0),
  set("s2", "e-bench", "w1", "barbell-bench-press", 1),
  set("s1", "e-bench", "w1", "barbell-bench-press", 0),
];

describe("exportWorkouts", () => {
  it("lists a workout's exercises in their order, each with its own details and sets", () => {
    const [workout] = exportWorkouts(sessions, exercises, sets, name);
    expect(workout.exercises.map((e) => e.exercise_id)).toEqual(["barbell-bench-press", "back-squat", "barbell-row"]);
    const bench = workout.exercises[0];
    expect(bench).toMatchObject({ position: 0, notes: "Felt good", superset_group: 1, rest_seconds: 120, progression_reason: "ADD_REPS" });
    expect(bench.sets.map((s) => s.position)).toEqual([0, 1]);
  });

  it("keeps an exercise with no sets logged", () => {
    const [workout] = exportWorkouts(sessions, exercises, sets, name);
    expect(workout.exercises[2]).toMatchObject({ exercise_id: "barbell-row", sets: [] });
  });
});

describe("exportCsvRows", () => {
  it("orders rows by workout, then exercise, then set, and says which exercise of the workout each is", () => {
    const rows = exportCsvRows(sessions, exercises, sets);
    expect(rows.map((r) => [r.exercisePosition, r.set.position])).toEqual([[0, 0], [0, 1], [1, 0]]);
  });
});
