import { LOCALE, TIME_ZONE } from "./tz";
import { epley, isTimed, MUSCLE_GROUPS, muscleGroupOf, type SessionSummaryRow, type SetEntryRow } from "./model";

export const DAY_MS = 86_400_000;
export const WEEK_MS = 7 * DAY_MS;

// One formatter per zone; formatToParts is the expensive part, so results are memoised too.
const parts = new Intl.DateTimeFormat(LOCALE, { timeZone: TIME_ZONE, year: "numeric", month: "numeric", day: "numeric" });
const dateCache = new Map<number, number>();
const DATE_CACHE_MAX = 20_000;

/**
 * The calendar date of [ms] in the display time zone, as UTC midnight of that date. Day
 * and week maths runs on these "date values" so it doesn't depend on the server's zone.
 */
export function localDate(ms: number): number {
  const hit = dateCache.get(ms);
  if (hit != null) return hit;
  const p = Object.fromEntries(parts.formatToParts(ms).map((x) => [x.type, x.value]));
  const date = Date.UTC(Number(p.year), Number(p.month) - 1, Number(p.day));
  if (dateCache.size >= DATE_CACHE_MAX) dateCache.clear();
  dateCache.set(ms, date);
  return date;
}

/** Weekday of a date value, 0 = Monday … 6 = Sunday (the app's week starts on Monday). */
function weekdayOf(dateMs: number): number {
  return (new Date(dateMs).getUTCDay() + 6) % 7;
}

/** Monday of the week containing [ms], as a date value (see [localDate]). */
export function weekStart(ms: number): number {
  const date = localDate(ms);
  return date - weekdayOf(date) * DAY_MS;
}

function addWeeks(weekMs: number, n: number): number {
  return weekMs + n * WEEK_MS;
}

type Session = Pick<SessionSummaryRow, "started_at" | "ended_at"> & Partial<Pick<SessionSummaryRow, "volume_kg" | "completed_sets">>;

/** Finished sessions only; a running workout doesn't count yet. */
export function finished<T extends Pick<SessionSummaryRow, "ended_at">>(sessions: T[]): T[] {
  return sessions.filter((s) => s.ended_at != null);
}

export interface WeekBucket {
  weekStart: number;
  sessions: number;
  volumeKg: number;
  sets: number;
}

/** The last [weeks] weeks (oldest first, current week last), with totals. */
export function weeklyTotals(sessions: Session[], weeks: number, now = Date.now()): WeekBucket[] {
  const current = weekStart(now);
  const buckets: WeekBucket[] = Array.from({ length: weeks }, (_, i) => ({
    weekStart: addWeeks(current, i - weeks + 1),
    sessions: 0,
    volumeKg: 0,
    sets: 0,
  }));
  const index = new Map(buckets.map((b, i) => [b.weekStart, i]));
  for (const s of finished(sessions)) {
    const i = index.get(weekStart(s.started_at));
    if (i == null) continue;
    buckets[i].sessions++;
    buckets[i].volumeKg += Number(s.volume_kg ?? 0);
    buckets[i].sets += Number(s.completed_sets ?? 0);
  }
  return buckets;
}

/**
 * The change from [previous] to [current] in percent, rounded to a whole number, or null when
 * there's nothing to compare with (previous is 0): "+12%" on a stat tile.
 */
export function percentChange(current: number, previous: number): number | null {
  if (!previous) return null;
  return Math.round(((current - previous) / previous) * 100);
}

/** "+12%", "−8%" (a true minus sign), "±0%". */
export function formatChange(percent: number): string {
  if (percent === 0) return "±0%";
  return `${percent > 0 ? "+" : "\u2212"}${Math.abs(percent)}%`;
}

/** The start of the week [weeks] - 1 weeks before this one: the oldest week in a [weeklyTotals] window. */
export function windowStart(weeks: number, now = Date.now()): number {
  return addWeeks(weekStart(now), 1 - weeks);
}

/**
 * Weeks in a row, ending this week or last, with at least one finished session. This
 * week still counts as "in progress", so a streak isn't broken until the week is over.
 */
export function weekStreak(sessions: Pick<SessionSummaryRow, "started_at" | "ended_at">[], now = Date.now()): number {
  const weeks = new Set(finished(sessions).map((s) => weekStart(s.started_at)));
  let week = weekStart(now);
  if (!weeks.has(week)) week = addWeeks(week, -1);
  let streak = 0;
  while (weeks.has(week)) {
    streak++;
    week = addWeeks(week, -1);
  }
  return streak;
}

/** Calendar day key in the display time zone, "2026-10-23". */
export function dayKey(ms: number): string {
  return dateValueKey(localDate(ms));
}

/** "2026-10-23" for a date value (UTC midnight). */
export function dateValueKey(dateMs: number): string {
  return new Date(dateMs).toISOString().slice(0, 10);
}

export function epochDayToMs(epochDay: number): number {
  return epochDay * DAY_MS;
}

type LoggedSet = Pick<SetEntryRow, "weight_kg" | "reps" | "set_type" | "completed_at"> & { session_id: string };

export interface ExerciseSessionPoint {
  sessionId: string;
  date: number;
  /** Best estimated 1RM among working sets with a weight; null for bodyweight-only. */
  e1rmKg: number | null;
  /** Heaviest working set, then most reps at that weight. */
  topSet: { weightKg: number | null; reps: number | null } | null;
  volumeKg: number;
  /** Most reps (or seconds, for holds) in one working set. */
  bestReps: number;
}

/**
 * One point per session for an exercise, oldest first: the numbers the app's exercise
 * history shows (core/model ExerciseSessionLog). Warm-ups and unfinished sets are skipped.
 */
export function exerciseProgress(sets: LoggedSet[], exerciseId: string, sessionDates: Map<string, number>): ExerciseSessionPoint[] {
  const timed = isTimed(exerciseId);
  const bySession = new Map<string, LoggedSet[]>();
  for (const s of sets) {
    if (s.completed_at == null || s.set_type === "WARMUP" || s.reps == null) continue;
    const list = bySession.get(s.session_id) ?? [];
    list.push(s);
    bySession.set(s.session_id, list);
  }
  const points: ExerciseSessionPoint[] = [];
  for (const [sessionId, working] of bySession) {
    const date = sessionDates.get(sessionId);
    if (date == null) continue;
    const top = working.reduce<LoggedSet | null>((best, s) => {
      if (!best) return s;
      const w = s.weight_kg ?? 0;
      const bw = best.weight_kg ?? 0;
      return w > bw || (w === bw && (s.reps ?? 0) > (best.reps ?? 0)) ? s : best;
    }, null);
    let e1rm: number | null = null;
    let volume = 0;
    let bestReps = 0;
    for (const s of working) {
      const reps = s.reps ?? 0;
      bestReps = Math.max(bestReps, reps);
      if (timed || s.weight_kg == null) continue;
      volume += s.weight_kg * reps;
      e1rm = Math.max(e1rm ?? 0, epley(s.weight_kg, reps));
    }
    points.push({ sessionId, date, e1rmKg: e1rm, topSet: top ? { weightKg: top.weight_kg, reps: top.reps } : null, volumeKg: volume, bestReps });
  }
  return points.sort((a, b) => a.date - b.date);
}

/**
 * The plan day to do next: the one after the most recent session's day (wrapping
 * around), or the first day if no session used this plan yet.
 */
export function nextPlanDay<D extends { sync_id: string; position: number }>(
  days: D[],
  sessions: Pick<SessionSummaryRow, "plan_day_id" | "started_at">[],
): D | null {
  if (!days.length) return null;
  const ordered = [...days].sort((a, b) => a.position - b.position);
  const ids = new Set(ordered.map((d) => d.sync_id));
  const last = sessions
    .filter((s) => s.plan_day_id && ids.has(s.plan_day_id))
    .sort((a, b) => b.started_at - a.started_at)[0];
  if (!last) return ordered[0];
  const i = ordered.findIndex((d) => d.sync_id === last.plan_day_id);
  return ordered[(i + 1) % ordered.length];
}

type MuscleSet = Pick<SetEntryRow, "set_type" | "completed_at"> & { exercise_id: string };

export interface GroupVolume {
  group: string;
  /** Primary muscles count a full set, secondary-only groups half (core/engine MuscleBalance). */
  sets: number;
}

/**
 * Working sets per muscle group for the given sets. A set counts once for each group its
 * primary muscles belong to, and half for groups hit only by secondary muscles.
 */
export function muscleVolume(sets: MuscleSet[], muscles: (exerciseId: string) => { primary: string[]; secondary: string[] } | undefined): GroupVolume[] {
  const totals = new Map(Object.keys(MUSCLE_GROUPS).map((g) => [g, 0]));
  for (const s of sets) {
    if (s.completed_at == null || s.set_type === "WARMUP") continue;
    const m = muscles(s.exercise_id);
    if (!m) continue;
    const primary = new Set(m.primary.map(muscleGroupOf).filter((g): g is string => !!g));
    const secondary = new Set(m.secondary.map(muscleGroupOf).filter((g): g is string => !!g && !primary.has(g)));
    primary.forEach((g) => totals.set(g, totals.get(g)! + 1));
    secondary.forEach((g) => totals.set(g, totals.get(g)! + 0.5));
  }
  return [...totals].map(([group, sets]) => ({ group, sets }));
}

/** Finished workouts per calendar day (display time zone), keyed by [dayKey]. */
export function workoutsPerDay(sessions: Pick<SessionSummaryRow, "started_at" | "ended_at">[]): Map<string, number> {
  const days = new Map<string, number>();
  for (const s of finished(sessions)) {
    const key = dayKey(s.started_at);
    days.set(key, (days.get(key) ?? 0) + 1);
  }
  return days;
}
