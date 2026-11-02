import { describe, expect, it } from "vitest";
import { playedSession, playerProgress } from "./gamification";
import type { SessionPlayRow } from "./queries";

const row = (startedAt: string, minutes: number, extra: Partial<SessionPlayRow> = {}): SessionPlayRow => ({
  sync_id: startedAt,
  started_at: Date.parse(startedAt),
  ended_at: Date.parse(startedAt) + minutes * 60_000,
  is_deload: false,
  readiness: null,
  feel: null,
  working_sets: 10,
  volume_kg: 0,
  exercise_ids: [],
  record_types: [],
  ...extra,
});

describe("playedSession", () => {
  it("reads the start in the display time zone (Stockholm)", () => {
    // 05:30 UTC on 26 Oct is 06:30 in Stockholm (winter time): an early start.
    expect(playedSession(row("2026-10-26T05:30:00Z", 45)).start).toBe("2026-10-26T06:30");
    // 22:30 UTC on 1 Sep is half past midnight the next day in Stockholm (summer time).
    expect(playedSession(row("2026-09-01T22:30:00Z", 45)).start).toBe("2026-09-02T00:30");
  });

  it("counts whole minutes and keeps the totals", () => {
    const s = playedSession(row("2026-10-26T17:00:00Z", 9.9, { volume_kg: 1250.5, record_types: ["VOLUME"], is_deload: true }));
    expect(s.minutes).toBe(9);
    expect(s.volumeKg).toBe(1250.5);
    expect(s.records).toEqual(["VOLUME"]);
    expect(s.lighter).toBe(true);
  });

  it("feeds the rules: a short workout earns nothing", () => {
    const progress = playerProgress([row("2026-10-26T17:00:00Z", 9.9), row("2026-10-27T17:00:00Z", 45)].map(playedSession), null);
    expect(progress.sessions.map((s) => s.total)).toEqual([0, 150]);
  });
});
