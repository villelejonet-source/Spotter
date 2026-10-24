// Display formatting for dates, in one locale (LOCALE) and the display zone. Sessions are
// stored as epoch ms and shown in TIME_ZONE; "date values" (localDate / weekStart / epoch
// days) are UTC midnights and are formatted in UTC so the day never shifts.
import { DAY_MS, localDate } from "./stats";
import { LOCALE, TIME_ZONE } from "./tz";

const inZone = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(LOCALE, { timeZone: TIME_ZONE, ...options });
const inUtc = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(LOCALE, { timeZone: "UTC", ...options });

const dayMonth = inZone({ day: "numeric", month: "short" });
const full = inZone({ weekday: "short", day: "numeric", month: "short", year: "numeric" });
const time = inZone({ hour: "2-digit", minute: "2-digit" });
const monthYear = inZone({ month: "long", year: "numeric" });

/** "3 Oct" */
export const formatDayMonth = (ms: number) => dayMonth.format(ms);
/** "Sat 3 Oct 2026": the app's formatShortDate ("Sat 3 Oct") plus the year, so no comma after the weekday. */
export const formatFullDate = (ms: number) => full.format(ms).replace(",", "");
/** "14:05" */
export const formatTime = (ms: number) => time.format(ms);
/** "October 2026" */
export const formatMonthYear = (ms: number) => monthYear.format(ms);

const utcDate = inUtc({ day: "numeric", month: "short" });
const utcFullDate = inUtc({ day: "numeric", month: "short", year: "numeric" });
const utcMonthYear = inUtc({ month: "long", year: "numeric" });
const utcMonth = inUtc({ month: "short" });
const utcWeekday = inUtc({ weekday: "short" });

/** "3 Oct" for a date value. */
export const formatDateValue = (dateMs: number) => utcDate.format(dateMs);
/** "October 2026" for a date value. */
export const formatDateValueMonth = (dateMs: number) => utcMonthYear.format(dateMs);
/** "Oct" for a date value. */
export const formatDateValueMonthShort = (dateMs: number) => utcMonth.format(dateMs);
/** "3 Oct 2026" for an epoch day (body weight entries, scheduled workouts). */
export const formatEpochDay = (epochDay: number) => utcFullDate.format(epochDay * DAY_MS);

/**
 * Short weekday names in the locale's order, starting on its first day of the week
 * (Intl.Locale.getWeekInfo), Monday when the runtime can't say. en-GB weeks start on
 * Monday, which is also how [weekStart] and the app count weeks.
 */
export function weekdayLabels(): string[] {
  const locale = new Intl.Locale(LOCALE) as Intl.Locale & { getWeekInfo?: () => { firstDay: number }; weekInfo?: { firstDay: number } };
  const firstDay = locale.getWeekInfo?.().firstDay ?? locale.weekInfo?.firstDay ?? 1; // 1 = Monday … 7 = Sunday
  // 1970-01-05 was a Monday.
  return Array.from({ length: 7 }, (_, i) => utcWeekday.format(Date.UTC(1970, 0, 5 + ((firstDay - 1 + i) % 7))));
}

/** "Today", "Yesterday", "3 days ago", or the date. */
export function formatRelative(ms: number, now = Date.now()): string {
  const days = Math.round((localDate(now) - localDate(ms)) / DAY_MS);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days} days ago`;
  return formatDayMonth(ms);
}
