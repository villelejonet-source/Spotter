import { describe, expect, it } from "vitest";
import { parseAdminStats } from "./admin";

describe("parseAdminStats", () => {
  it("reads admin_stats()'s JSON", () => {
    const stats = parseAdminStats({
      generated_at: "2026-10-25T12:00:00+00:00",
      weeks: 12,
      totals: { accounts: 5, confirmed: 4, active_7d: 2, active_30d: 3, signed_in_30d: 1, with_backup: 3, workouts: 40, devices: 4 },
      by_week: [{ week: "2026-10-19", signups: 1, workouts: 6, active_users: 2 }],
      versions: [{ version_code: 7, devices: 2, accounts: 2 }],
      active_30d_without_check_in: 1,
      users: [
        {
          email: "a@example.test",
          created_at: "2026-10-01T08:00:00+00:00",
          last_sign_in_at: null,
          last_active_at: "2026-10-24T18:30:00.123+00:00",
          workouts: 12,
          devices: 1,
          version_code: 7,
        },
      ],
    });
    expect(stats.generatedAt).toBe(Date.UTC(2026, 9, 25, 12));
    expect(stats.totals).toEqual({ accounts: 5, confirmed: 4, active7d: 2, active30d: 3, signedIn30d: 1, withBackup: 3, workouts: 40, devices: 4 });
    expect(stats.byWeek).toEqual([{ week: Date.UTC(2026, 9, 19), signups: 1, workouts: 6, activeUsers: 2 }]);
    expect(stats.versions).toEqual([{ versionCode: 7, devices: 2, accounts: 2 }]);
    expect(stats.activeWithoutCheckIn).toBe(1);
    expect(stats.users).toEqual([
      {
        email: "a@example.test",
        createdAt: Date.UTC(2026, 9, 1, 8),
        lastSignInAt: null,
        lastActiveAt: Date.UTC(2026, 9, 24, 18, 30, 0, 123),
        workouts: 12,
        devices: 1,
        versionCode: 7,
      },
    ]);
  });

  it("reads missing or malformed fields as zero or null", () => {
    const stats = parseAdminStats({ totals: "x", by_week: {}, users: [{ workouts: "3", version_code: null, created_at: "never" }] });
    expect(stats.totals.accounts).toBe(0);
    expect(stats.byWeek).toEqual([]);
    expect(stats.users[0]).toMatchObject({ email: null, workouts: 0, versionCode: null, createdAt: null });
    expect(parseAdminStats(null).users).toEqual([]);
  });
});
