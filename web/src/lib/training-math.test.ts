// The cases in core/model/fixtures/training-math.json, which the app's tests check too
// (core/engine TrainingMathFixturesTest), so the website and the app can't drift apart.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { epley, isTimed } from "./model";
import { parseDecimal } from "./number-input";
import { exerciseProgress } from "./stats";

interface FixtureSet { weightKg: number | null; reps: number | null; warmup?: boolean }

const fixtures: {
  timedExerciseIds: string[];
  decimalInput: { text: string; value?: number; result?: "blank" | "invalid" }[];
  epley: { weightKg: number; reps: number; oneRepMaxKg: number }[];
  exerciseSessions: { name: string; exerciseId: string; sets: FixtureSet[]; volumeKg: number; oneRepMaxKg: number | null; bestSet: FixtureSet | null }[];
} = JSON.parse(readFileSync(resolve(__dirname, "../../../core/model/fixtures/training-math.json"), "utf8"));

describe("training-math.json", () => {
  it("timed exercises", () => {
    for (const id of fixtures.timedExerciseIds) expect(isTimed(id), id).toBe(true);
    expect(isTimed("barbell-bench-press")).toBe(false);
  });

  it.each(fixtures.decimalInput)("decimal input %j", ({ text, value, result }) => {
    expect(parseDecimal(text)).toEqual(result ? { kind: result } : { kind: "value", value });
  });

  it.each(fixtures.epley)("epley %j", ({ weightKg, reps, oneRepMaxKg }) => {
    expect(epley(weightKg, reps)).toBeCloseTo(oneRepMaxKg, 4);
  });

  it.each(fixtures.exerciseSessions)("$name", ({ exerciseId, sets, volumeKg, oneRepMaxKg, bestSet }) => {
    const rows = sets.map((s) => ({
      session_id: "s",
      weight_kg: s.weightKg,
      reps: s.reps,
      set_type: s.warmup ? ("WARMUP" as const) : ("WORKING" as const),
      completed_at: 1,
    }));
    const [point] = exerciseProgress(rows, exerciseId, new Map([["s", 1]]));
    expect(point.volumeKg).toBeCloseTo(volumeKg, 4);
    if (oneRepMaxKg == null) expect(point.e1rmKg).toBeNull();
    else expect(point.e1rmKg).toBeCloseTo(oneRepMaxKg, 4);
    expect(point.topSet).toEqual(bestSet && { weightKg: bestSet.weightKg, reps: bestSet.reps });
  });
});
