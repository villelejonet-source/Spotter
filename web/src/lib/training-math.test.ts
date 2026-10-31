// The cases in core/model/fixtures/training-math.json, which the app's tests check too
// (core/engine TrainingMathFixturesTest), so the website and the app can't drift apart.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { epley, isTimed } from "./model";
import { parseDecimal } from "./number-input";
import { exerciseProgress, formatChange, percentChange, weekStreak } from "./stats";

interface FixtureSet { weightKg: number | null; reps: number | null; warmup?: boolean }

const fixtures: {
  timedExerciseIds: string[];
  decimalInput: { text: string; value?: number; result?: "blank" | "invalid" }[];
  epley: { weightKg: number; reps: number; oneRepMaxKg: number }[];
  exerciseSessions: { name: string; exerciseId: string; sets: FixtureSet[]; volumeKg: number; oneRepMaxKg: number | null; bestSet: FixtureSet | null }[];
  percentChange: { current: number; previous: number; percent: number | null; text?: string }[];
  weekStreak: { name: string; today: string; dates: string[]; streak: number }[];
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

  it.each(fixtures.percentChange)("percent change %j", ({ current, previous, percent, text }) => {
    expect(percentChange(current, previous)).toBe(percent);
    if (percent != null) expect(formatChange(percent)).toBe(text);
  });

  // Noon UTC is the same calendar date in the display time zone.
  const noon = (date: string) => Date.parse(`${date}T12:00:00Z`);

  it.each(fixtures.weekStreak)("week streak: $name", ({ today, dates, streak }) => {
    const sessions = dates.map((d) => ({ started_at: noon(d), ended_at: noon(d) + 3_600_000 }));
    expect(weekStreak(sessions, noon(today))).toBe(streak);
  });
});
