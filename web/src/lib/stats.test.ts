import { describe, expect, it } from "vitest";
import { dayKey, exerciseProgress, nextPlanDay, weekStart, weekStreak, weeklyTotals } from "./stats";

// Thursday 2026-10-22, 14:00 in Stockholm.
const NOW = Date.UTC(2026, 9, 22, 12);
const day = (y: number, m: number, d: number) => Date.UTC(y, m - 1, d, 16);
const session = (ms: number, volume = 1000) => ({ started_at: ms, ended_at: ms + 3_600_000, volume_kg: volume, completed_sets: 10 });

describe("weekStart", () => {
  it("is Monday midnight", () => {
    expect(weekStart(NOW)).toBe(Date.UTC(2026, 9, 19));
    expect(weekStart(day(2026, 10, 25))).toBe(weekStart(NOW)); // Sunday belongs to the same week
  });
});

describe("dayKey", () => {
  it("uses the Stockholm calendar day, not UTC", () => {
    expect(dayKey(Date.UTC(2026, 9, 22, 22, 30))).toBe("2026-10-23"); // 00:30 CEST
  });
});

describe("weeklyTotals", () => {
  it("buckets finished sessions into weeks, current week last", () => {
    const weeks = weeklyTotals([session(day(2026, 10, 20), 500), session(day(2026, 10, 21), 700), session(day(2026, 10, 13))], 3, NOW);
    expect(weeks.map((w) => w.sessions)).toEqual([0, 1, 2]);
    expect(weeks[2].volumeKg).toBe(1200);
  });

  it("ignores a running workout", () => {
    const running = { ...session(day(2026, 10, 21)), ended_at: null };
    expect(weeklyTotals([running], 1, NOW)[0].sessions).toBe(0);
  });
});

describe("weekStreak", () => {
  it("counts back from last week when this week has no session yet", () => {
    expect(weekStreak([session(day(2026, 10, 14)), session(day(2026, 10, 7))], NOW)).toBe(2);
  });

  it("breaks on an empty week", () => {
    expect(weekStreak([session(day(2026, 10, 20)), session(day(2026, 10, 6))], NOW)).toBe(1);
  });
});

describe("exerciseProgress", () => {
  const set = (session_id: string, weight_kg: number | null, reps: number, set_type: "WARMUP" | "WORKING" = "WORKING") => ({
    session_id, weight_kg, reps, set_type, completed_at: 1,
  });
  const dates = new Map([["a", 1], ["b", 2]]);

  it("uses working sets only and picks the heaviest top set", () => {
    const [a, b] = exerciseProgress(
      [set("a", 40, 10, "WARMUP"), set("a", 60, 8), set("a", 60, 10), set("b", 62.5, 6)],
      "barbell-bench-press",
      dates,
    );
    expect(a.topSet).toEqual({ weightKg: 60, reps: 10 });
    expect(a.e1rmKg).toBeCloseTo(80);
    expect(a.volumeKg).toBe(1080);
    expect(b.date).toBe(2);
  });

  it("treats holds as seconds without volume or 1RM", () => {
    const [p] = exerciseProgress([set("a", null, 45), set("a", null, 60)], "plank", dates);
    expect(p.e1rmKg).toBeNull();
    expect(p.volumeKg).toBe(0);
    expect(p.bestReps).toBe(60);
  });
});

describe("nextPlanDay", () => {
  const days = [{ sync_id: "d1", position: 0 }, { sync_id: "d2", position: 1 }];

  it("starts at the first day", () => {
    expect(nextPlanDay(days, [])?.sync_id).toBe("d1");
  });

  it("follows the latest session and wraps around", () => {
    expect(nextPlanDay(days, [{ plan_day_id: "d1", started_at: 1 }, { plan_day_id: "d2", started_at: 2 }])?.sync_id).toBe("d1");
  });
});
