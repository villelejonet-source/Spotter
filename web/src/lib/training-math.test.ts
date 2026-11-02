// The cases in core/model/fixtures/training-math.json, which the app's tests check too
// (core/engine TrainingMathFixturesTest), so the website and the app can't drift apart.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { countsTowardsChange, feelSummary, isRoughDay } from "./check-ins";
import { levelFor, playerProgress, type PlayedSession } from "./gamification";
import { epley, isTimed, type PersonalRecordType } from "./model";
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
  checkIns: { name: string; lighter: boolean; readiness: number | null; feel: number | null; roughDay: boolean; countsTowardsChange: boolean }[];
  feelSummary: { name: string; checkIns: [number | null, number | null][]; summary: { rated: number; averageReadiness: number | null; averageFeel: number | null; lowDays: number } | null }[];
  gamification: {
    levels: { xp: number; level: number; title: string; xpIntoLevel: number; xpForNext: number }[];
    histories: {
      name: string;
      daysPerWeek: number | null;
      sessions: (Pick<PlayedSession, "start" | "minutes" | "workingSets"> & { id: number } & Partial<Omit<PlayedSession, "id" | "records">> & { records?: PersonalRecordType[] })[];
      xp: { id: number; session: number; sets: number; records: number; checkIn: number; weeklyGoal: number }[];
      totalXp: number;
      level: number;
      unlocked: Record<string, number>;
      progress: Record<string, number>;
    }[];
  };
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

  it.each(fixtures.checkIns)("check-ins: $name", ({ lighter, readiness, feel, roughDay, countsTowardsChange: counts }) => {
    expect(isRoughDay(lighter, readiness, feel)).toBe(roughDay);
    expect(countsTowardsChange(lighter, readiness, feel)).toBe(counts);
  });

  it.each(fixtures.feelSummary)("feel summary: $name", ({ checkIns, summary }) => {
    const actual = feelSummary(checkIns.map(([readiness, feel]) => ({ readiness, feel })));
    if (summary == null) return expect(actual).toBeNull();
    expect(actual?.rated).toBe(summary.rated);
    expect(actual?.lowDays).toBe(summary.lowDays);
    for (const key of ["averageReadiness", "averageFeel"] as const) {
      if (summary[key] == null) expect(actual?.[key]).toBeNull();
      else expect(actual?.[key]).toBeCloseTo(summary[key]!, 4);
    }
  });

  it.each(fixtures.gamification.levels)("level %j", ({ xp, level, title, xpIntoLevel, xpForNext }) => {
    expect(levelFor(xp)).toEqual({ number: level, title, xpIntoLevel, xpForNext });
  });

  it.each(fixtures.gamification.histories)("gamification: $name", ({ daysPerWeek, sessions, xp, totalXp, level, unlocked, progress }) => {
    const played: PlayedSession[] = sessions.map((s) => ({
      lighter: false,
      readiness: null,
      feel: null,
      volumeKg: 0,
      exerciseIds: [],
      records: [],
      ...s,
      id: String(s.id),
    }));
    const actual = playerProgress(played, daysPerWeek);
    expect(actual.sessions).toEqual(
      xp.map(({ id, ...x }) => ({ sessionId: String(id), ...x, total: x.session + x.sets + x.records + x.checkIn + x.weeklyGoal })),
    );
    expect(actual.totalXp).toBe(totalXp);
    expect(actual.level.number).toBe(level);
    expect(Object.fromEntries(actual.badges.filter((b) => b.unlockedBy != null).map((b) => [b.badge, Number(b.unlockedBy)]))).toEqual(unlocked);
    for (const [badge, current] of Object.entries(progress)) {
      expect(actual.badges.find((b) => b.badge === badge)?.current, badge).toBeCloseTo(current, 4);
    }
  });
});
