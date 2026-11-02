// Levels and badges, as the app has them (core/model and core/engine Gamification.kt); pinned by
// core/model/fixtures/training-math.json ("gamification"). Worked out from the history every
// time, never stored.

import { LOW_CHECK_IN } from "./check-ins";
import type { PersonalRecordType } from "./model";

/** Every number levels and badges use; the app's GamificationRules. */
export const RULES = {
  minMinutes: 10,
  sessionXp: 100,
  setXp: 5,
  maxXpSets: 25,
  recordXp: 50,
  checkInXp: 25,
  weeklyGoalXp: 150,
  xpSessionsPerDay: 2,
  comebackDays: 21,
  earlyBeforeHour: 7,
  lateFromHour: 21,
} as const;

const RECORD_TYPES: PersonalRecordType[] = ["ESTIMATED_1RM", "REPS_AT_WEIGHT", "VOLUME"];

/** A finished workout, reduced to what levels and badges count. */
export interface PlayedSession {
  id: string;
  /** Local start in the display time zone, "2026-10-26T18:00". */
  start: string;
  minutes: number;
  lighter: boolean;
  readiness: number | null;
  feel: number | null;
  /** Completed sets that aren't warm-ups. */
  workingSets: number;
  volumeKg: number;
  /** Exercises with at least one completed working set. */
  exerciseIds: string[];
  /** One entry per personal record set in this workout. */
  records: PersonalRecordType[];
}

export interface SessionXp {
  sessionId: string;
  session: number;
  sets: number;
  records: number;
  checkIn: number;
  weeklyGoal: number;
  total: number;
}

export const LEVEL_TITLES = [
  ["FRESH_CHALK", 1],
  ["BENCH_WARMER", 3],
  ["GYM_REGULAR", 5],
  ["PLATE_COLLECTOR", 8],
  ["IRON_ADDICT", 12],
  ["BARBELL_BARON", 16],
  ["SQUAT_ROYALTY", 20],
  ["IRON_LEGEND", 25],
  ["MYTHICAL_BEAST", 30],
] as const;

export type LevelTitle = (typeof LEVEL_TITLES)[number][0];

export interface Level {
  number: number;
  title: LevelTitle;
  xpIntoLevel: number;
  xpForNext: number;
}

export type BadgeMetric =
  | "WORKOUTS"
  | "RECORDS"
  | "TRIPLE_THREATS"
  | "WEEK_STREAK"
  | "VOLUME_KG"
  | "LISTENED"
  | "COMEBACKS"
  | "EARLY_STARTS"
  | "LATE_STARTS"
  | "EXERCISES";

/** In the app's Badge order; ids are its enum names. */
export const BADGES = [
  ["FIRST_REP", "WORKOUTS", 1],
  ["TEN_DOWN", "WORKOUTS", 10],
  ["HALF_CENTURY", "WORKOUTS", 50],
  ["CENTURION", "WORKOUTS", 100],
  ["CLUB_250", "WORKOUTS", 250],
  ["FIRST_PR", "RECORDS", 1],
  ["PR_HUNTER", "RECORDS", 10],
  ["RECORD_MACHINE", "RECORDS", 50],
  ["TRIPLE_THREAT", "TRIPLE_THREATS", 1],
  ["STREAK_4", "WEEK_STREAK", 4],
  ["STREAK_12", "WEEK_STREAK", 12],
  ["STREAK_26", "WEEK_STREAK", 26],
  ["STREAK_52", "WEEK_STREAK", 52],
  ["GRAND_PIANO", "VOLUME_KG", 500],
  ["CAR", "VOLUME_KG", 1_500],
  ["ELEPHANT", "VOLUME_KG", 6_000],
  ["BLUE_WHALE", "VOLUME_KG", 150_000],
  ["EIFFEL_TOWER", "VOLUME_KG", 7_300_000],
  ["LISTENED_TO_YOUR_BODY", "LISTENED", 1],
  ["COMEBACK_KID", "COMEBACKS", 1],
  ["EARLY_BIRD", "EARLY_STARTS", 5],
  ["NIGHT_OWL", "LATE_STARTS", 5],
  ["EXPLORER", "EXERCISES", 20],
] as const satisfies readonly (readonly [string, BadgeMetric, number])[];

export type Badge = (typeof BADGES)[number][0];

export interface BadgeProgress {
  badge: Badge;
  /** How far the metric has got, never past the target. */
  current: number;
  target: number;
  /** The workout that unlocked it, null while locked. */
  unlockedBy: string | null;
  /** "2026-10-26", null while locked. */
  unlockedOn: string | null;
}

export interface PlayerProgress {
  totalXp: number;
  level: Level;
  /** Every workout's XP, oldest first. */
  sessions: SessionXp[];
  badges: BadgeProgress[];
}

const DAY_MS = 86_400_000;

/** "2026-10-26" as UTC midnight, so date maths is plain arithmetic. */
const dateValue = (date: string) => Date.parse(`${date}T00:00:00Z`);
/** Monday of the week containing a date value. */
const mondayOf = (date: number) => date - ((new Date(date).getUTCDay() + 6) % 7) * DAY_MS;

/** Total XP needed to reach [level]: 50 · n · (n + 1), level 1 from nothing. */
export function xpToReach(level: number): number {
  return level <= 1 ? 0 : 50 * level * (level + 1);
}

export function levelFor(totalXp: number): Level {
  let n = 1;
  while (xpToReach(n + 1) <= totalXp) n++;
  const from = xpToReach(n);
  const title = LEVEL_TITLES.filter(([, fromLevel]) => fromLevel <= n).at(-1)![0];
  return { number: n, title, xpIntoLevel: totalXp - from, xpForNext: xpToReach(n + 1) - from };
}

export function counts(session: PlayedSession): boolean {
  return session.minutes >= RULES.minMinutes && session.workingSets > 0;
}

/** [daysPerWeek]: the profile's weekly goal; null or 0 means no goal bonus. */
export function playerProgress(sessions: PlayedSession[], daysPerWeek: number | null): PlayerProgress {
  const values = new Map<BadgeMetric, number>(BADGES.map(([, metric]) => [metric, 0]));
  const unlocked = new Map<Badge, { by: string; on: string }>();
  const bump = (metric: BadgeMetric, by: number) => values.set(metric, values.get(metric)! + by);
  const exercises = new Set<string>();
  const xpSessionsOnDay = new Map<string, number>();
  const daysInWeek = new Map<number, Set<string>>();
  const xp: SessionXp[] = [];
  let lastDate: number | null = null;
  let lastWeek: number | null = null;
  let run = 0;

  const ordered = [...sessions].sort((a, b) => (a.start < b.start ? -1 : a.start > b.start ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  for (const s of ordered) {
    if (!counts(s)) {
      xp.push({ sessionId: s.id, session: 0, sets: 0, records: 0, checkIn: 0, weeklyGoal: 0, total: 0 });
      continue;
    }
    const day = s.start.slice(0, 10);
    const date = dateValue(day);
    const week = mondayOf(date);
    const hour = Number(s.start.slice(11, 13));

    const earnsBase = (xpSessionsOnDay.get(day) ?? 0) < RULES.xpSessionsPerDay;
    if (earnsBase) xpSessionsOnDay.set(day, (xpSessionsOnDay.get(day) ?? 0) + 1);

    let days = daysInWeek.get(week);
    if (!days) daysInWeek.set(week, (days = new Set()));
    const goalHit = daysPerWeek != null && daysPerWeek > 0 && !days.has(day) && days.size + 1 === daysPerWeek;
    days.add(day);

    const entry = {
      sessionId: s.id,
      session: earnsBase ? RULES.sessionXp : 0,
      sets: earnsBase ? RULES.setXp * Math.min(s.workingSets, RULES.maxXpSets) : 0,
      records: RULES.recordXp * s.records.length,
      checkIn: earnsBase && (s.readiness != null || s.feel != null) ? RULES.checkInXp : 0,
      weeklyGoal: goalHit ? RULES.weeklyGoalXp : 0,
    };
    xp.push({ ...entry, total: entry.session + entry.sets + entry.records + entry.checkIn + entry.weeklyGoal });

    run = lastWeek === week ? run : lastWeek === week - 7 * DAY_MS ? run + 1 : 1;
    const comeback = lastDate != null && (date - lastDate) / DAY_MS >= RULES.comebackDays;
    lastWeek = week;
    lastDate = date;
    for (const id of s.exerciseIds) exercises.add(id);

    bump("WORKOUTS", 1);
    bump("RECORDS", s.records.length);
    if (RECORD_TYPES.every((t) => s.records.includes(t))) bump("TRIPLE_THREATS", 1);
    values.set("WEEK_STREAK", Math.max(values.get("WEEK_STREAK")!, run));
    bump("VOLUME_KG", s.volumeKg);
    const lowDay = (s.readiness ?? 5) <= LOW_CHECK_IN || (s.feel ?? 5) <= LOW_CHECK_IN;
    if (s.lighter && lowDay) bump("LISTENED", 1);
    if (comeback) bump("COMEBACKS", 1);
    if (hour < RULES.earlyBeforeHour) bump("EARLY_STARTS", 1);
    if (hour >= RULES.lateFromHour) bump("LATE_STARTS", 1);
    values.set("EXERCISES", exercises.size);

    for (const [badge, metric, target] of BADGES) {
      if (!unlocked.has(badge) && values.get(metric)! >= target) unlocked.set(badge, { by: s.id, on: day });
    }
  }

  const totalXp = xp.reduce((sum, s) => sum + s.total, 0);
  return {
    totalXp,
    level: levelFor(totalXp),
    sessions: xp,
    badges: BADGES.map(([badge, metric, target]) => ({
      badge,
      current: Math.min(values.get(metric)!, target),
      target,
      unlockedBy: unlocked.get(badge)?.by ?? null,
      unlockedOn: unlocked.get(badge)?.on ?? null,
    })),
  };
}
