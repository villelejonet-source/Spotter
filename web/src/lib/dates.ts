// Display formatting for dates. Sessions are stored as epoch ms and shown in TIME_ZONE.
import { localDate } from "./stats";
import { TIME_ZONE } from "./tz";

const fmt = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat("en-GB", { timeZone: TIME_ZONE, ...options });

const dayMonth = fmt({ day: "numeric", month: "short" });
const full = fmt({ weekday: "short", day: "numeric", month: "short", year: "numeric" });
const time = fmt({ hour: "2-digit", minute: "2-digit" });
const monthYear = fmt({ month: "long", year: "numeric" });

export const formatDayMonth = (ms: number) => dayMonth.format(ms);
export const formatFullDate = (ms: number) => full.format(ms);
export const formatTime = (ms: number) => time.format(ms);
export const formatMonthYear = (ms: number) => monthYear.format(ms);

const utcDate = new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", day: "numeric", month: "short" });
const utcWeekday = new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", weekday: "short" });
const utcMonthYear = new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", month: "long", year: "numeric" });

/** Labels for the date values from stats.localDate / weekStart (UTC midnight). */
export const formatDateValue = (dateMs: number) => utcDate.format(dateMs);
export const formatDateValueWeekday = (dateMs: number) => utcWeekday.format(dateMs);
export const formatDateValueMonth = (dateMs: number) => utcMonthYear.format(dateMs);

/** "Today", "Yesterday", "3 days ago", or the date. */
export function formatRelative(ms: number, now = Date.now()): string {
  const days = Math.round((localDate(now) - localDate(ms)) / 86_400_000);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days} days ago`;
  return formatDayMonth(ms);
}
