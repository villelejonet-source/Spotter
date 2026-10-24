import { describe, expect, it } from "vitest";
import { formatDateValue, formatEpochDay, formatFullDate, formatMonthYear, formatRelative, formatTime, weekdayLabels } from "./dates";
import { DAY_MS, weekStart } from "./stats";

// Thursday 2026-10-22, 14:00 in Stockholm (12:00 UTC).
const NOW = Date.UTC(2026, 9, 22, 12);

describe("display formats", () => {
  it("show the Stockholm day and time in one locale, written like the app", () => {
    const lateEvening = Date.UTC(2026, 9, 22, 22, 30); // 00:30 on the 23rd in Stockholm
    expect(formatFullDate(lateEvening)).toBe("Fri 23 Oct 2026");
    expect(formatTime(lateEvening)).toBe("00:30");
    expect(formatMonthYear(lateEvening)).toBe("October 2026");
  });

  it("format date values and epoch days without a zone shift", () => {
    expect(formatDateValue(Date.UTC(2026, 9, 3))).toBe("3 Oct");
    expect(formatEpochDay(Date.UTC(2026, 9, 3) / DAY_MS)).toBe("3 Oct 2026");
  });
});

describe("formatRelative", () => {
  it("counts days in the display zone", () => {
    expect(formatRelative(NOW, NOW)).toBe("Today");
    expect(formatRelative(Date.UTC(2026, 9, 21, 20), NOW)).toBe("Yesterday");
    expect(formatRelative(NOW - 3 * DAY_MS, NOW)).toBe("3 days ago");
    expect(formatRelative(NOW - 10 * DAY_MS, NOW)).toBe("12 Oct");
  });
});

describe("weekdays", () => {
  it("start the week on Monday, matching weekStart", () => {
    expect(weekdayLabels()).toEqual(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]);
    expect(formatFullDate(weekStart(NOW) + 12 * 3_600_000)).toMatch(/^Mon /);
  });
});
