// Levels and badges, as the app has them (core/model and core/engine Gamification.kt); pinned by
// core/model/fixtures/training-math.json ("gamification"). Worked out from the history every
// time, never stored.

import { LOW_CHECK_IN } from "./check-ins";
import type { PersonalRecordType } from "./model";
import type { SessionPlayRow } from "./queries";
import { LOCALE, TIME_ZONE } from "./tz";

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
  /** The first week with quests (a Monday), so the update didn't pay for weeks already trained. */
  questsFrom: "2026-11-09",
  questXp: 50,
  questWorkouts: 3,
  questCheckIns: 3,
  questSets: 40,
  /** The year in review shows through this day of January. */
  reviewUntilJanuary: 15,
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
  /** Weekly quests this workout finished. */
  quests: number;
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
  /** The quests of the week containing the day asked about; null before quests began. */
  thisWeek: WeekQuests | null;
}

export type QuestKind = "WORKOUTS" | "NEW_EXERCISE" | "CHECK_INS" | "RECORDS" | "SETS";

export interface Quest {
  kind: QuestKind;
  target: number;
}

export interface QuestProgress {
  quest: Quest;
  /** Never past the target. */
  current: number;
  /** The workout that finished it, null while open. */
  doneBy: string | null;
}

export interface WeekQuests {
  /** "2026-11-09", a Monday. */
  weekStart: string;
  quests: QuestProgress[];
}

const DAY_MS = 86_400_000;

const startParts = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

/** A row from v_session_play, with its start in the display time zone (as the app uses the phone's). */
export function playedSession(row: SessionPlayRow): PlayedSession {
  const p = Object.fromEntries(startParts.formatToParts(row.started_at).map((x) => [x.type, x.value]));
  return {
    id: row.sync_id,
    start: `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`,
    minutes: Math.floor((row.ended_at - row.started_at) / 60_000),
    lighter: row.is_deload,
    readiness: row.readiness,
    feel: row.feel,
    workingSets: row.working_sets,
    volumeKg: Number(row.volume_kg),
    exerciseIds: row.exercise_ids,
    records: row.record_types,
  };
}

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

const ROTATING_QUESTS: QuestKind[] = ["NEW_EXERCISE", "CHECK_INS", "RECORDS", "SETS"];

const questTarget = (kind: QuestKind): number =>
  ({ WORKOUTS: RULES.questWorkouts, NEW_EXERCISE: 1, RECORDS: 1, CHECK_INS: RULES.questCheckIns, SETS: RULES.questSets })[kind];

/**
 * The week's quests (as the app's Gamification.questsFor): the workouts one, then two of the
 * others in turn, moving on one each week. Null before quests began.
 */
export function questsFor(weekStart: string, daysPerWeek: number | null): Quest[] | null {
  if (weekStart < RULES.questsFrom) return null;
  const turn = Math.floor(dateValue(weekStart) / DAY_MS / 7);
  const workouts = daysPerWeek != null && daysPerWeek >= 1 && daysPerWeek <= 7 ? daysPerWeek : RULES.questWorkouts;
  const pick = (kind: QuestKind): Quest => ({ kind, target: questTarget(kind) });
  return [
    { kind: "WORKOUTS", target: workouts },
    pick(ROTATING_QUESTS[turn % ROTATING_QUESTS.length]),
    pick(ROTATING_QUESTS[(turn + 1) % ROTATING_QUESTS.length]),
  ];
}

const openQuests = (weekStart: string, daysPerWeek: number | null): QuestProgress[] | null =>
  questsFor(weekStart, daysPerWeek)?.map((quest) => ({ quest, current: 0, doneBy: null })) ?? null;

/** "2026-10-26" for a date value. */
const isoDate = (date: number) => new Date(date).toISOString().slice(0, 10);

/** Monday of the week containing a "2026-10-26" date. */
export function weekOf(date: string): string {
  return isoDate(mondayOf(dateValue(date)));
}

export function counts(session: PlayedSession): boolean {
  return session.minutes >= RULES.minMinutes && session.workingSets > 0;
}

/**
 * [daysPerWeek]: the profile's weekly goal; null or 0 means no goal bonus. [today]
 * ("2026-11-12"): the day whose week thisWeek shows; null leaves it out.
 */
export function playerProgress(sessions: PlayedSession[], daysPerWeek: number | null, today: string | null = null): PlayerProgress {
  const values = new Map<BadgeMetric, number>(BADGES.map(([, metric]) => [metric, 0]));
  const unlocked = new Map<Badge, { by: string; on: string }>();
  const bump = (metric: BadgeMetric, by: number) => values.set(metric, values.get(metric)! + by);
  const exercises = new Set<string>();
  const xpSessionsOnDay = new Map<string, number>();
  const daysInWeek = new Map<number, Set<string>>();
  const weeks = new Map<string, QuestProgress[] | null>();
  const seenExercises = new Set<string>();
  const xp: SessionXp[] = [];
  let lastDate: number | null = null;
  let lastWeek: number | null = null;
  let run = 0;

  const ordered = [...sessions].sort((a, b) => (a.start < b.start ? -1 : a.start > b.start ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  for (const s of ordered) {
    if (!counts(s)) {
      xp.push({ sessionId: s.id, session: 0, sets: 0, records: 0, checkIn: 0, weeklyGoal: 0, quests: 0, total: 0 });
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

    const newExercise = s.exerciseIds.some((id) => !seenExercises.has(id));
    for (const id of s.exerciseIds) seenExercises.add(id);
    const weekStart = isoDate(week);
    if (!weeks.has(weekStart)) weeks.set(weekStart, openQuests(weekStart, daysPerWeek));
    let questXp = 0;
    for (const progress of weeks.get(weekStart) ?? []) {
      if (progress.doneBy != null) continue;
      const add = {
        WORKOUTS: 1,
        NEW_EXERCISE: newExercise ? 1 : 0,
        CHECK_INS: s.readiness != null || s.feel != null ? 1 : 0,
        RECORDS: s.records.length,
        SETS: s.workingSets,
      }[progress.quest.kind];
      progress.current = Math.min(progress.current + add, progress.quest.target);
      if (progress.current >= progress.quest.target) {
        progress.doneBy = s.id;
        questXp += RULES.questXp;
      }
    }

    const entry = {
      sessionId: s.id,
      session: earnsBase ? RULES.sessionXp : 0,
      sets: earnsBase ? RULES.setXp * Math.min(s.workingSets, RULES.maxXpSets) : 0,
      records: RULES.recordXp * s.records.length,
      checkIn: earnsBase && (s.readiness != null || s.feel != null) ? RULES.checkInXp : 0,
      weeklyGoal: goalHit ? RULES.weeklyGoalXp : 0,
      quests: questXp,
    };
    xp.push({ ...entry, total: entry.session + entry.sets + entry.records + entry.checkIn + entry.weeklyGoal + entry.quests });

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
  const thisWeekStart = today == null ? null : weekOf(today);
  const thisWeekQuests = thisWeekStart == null ? null : (weeks.get(thisWeekStart) ?? openQuests(thisWeekStart, daysPerWeek));
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
    thisWeek: thisWeekStart != null && thisWeekQuests != null ? { weekStart: thisWeekStart, quests: thisWeekQuests } : null,
  };
}

// ---- Lift mastery ----

export type MasteryLift = "SQUAT" | "BENCH_PRESS" | "DEADLIFT" | "OVERHEAD_PRESS";
export type MasteryTier = "BRONZE" | "SILVER" | "GOLD";
export type Sex = "FEMALE" | "MALE";

const TIERS: MasteryTier[] = ["BRONZE", "SILVER", "GOLD"];

/** The app's MasteryLift: each lift's tier comes from the best 1RM of any of its exercises. */
export const MASTERY_LIFTS: [MasteryLift, string[]][] = [
  ["SQUAT", ["back-squat", "paused-squat"]],
  ["BENCH_PRESS", ["barbell-bench-press", "paused-bench-press"]],
  ["DEADLIFT", ["deadlift", "sumo-deadlift", "paused-deadlift"]],
  ["OVERHEAD_PRESS", ["overhead-press"]],
];

/** Bronze, silver and gold, as best estimated 1RM ÷ body weight (the app's GamificationRules.MASTERY). */
export const MASTERY: Record<Sex, Record<MasteryLift, [number, number, number]>> = {
  MALE: { SQUAT: [1.0, 1.5, 2.0], BENCH_PRESS: [0.75, 1.0, 1.5], DEADLIFT: [1.25, 1.75, 2.25], OVERHEAD_PRESS: [0.5, 0.75, 1.0] },
  FEMALE: { SQUAT: [0.75, 1.0, 1.5], BENCH_PRESS: [0.5, 0.75, 1.0], DEADLIFT: [1.0, 1.25, 1.75], OVERHEAD_PRESS: [0.35, 0.5, 0.65] },
};

export interface LiftMastery {
  lift: MasteryLift;
  bestKg: number | null;
  ratio: number | null;
  tier: MasteryTier | null;
  /** The next tier and the 1RM it needs; null at gold. */
  nextTier: MasteryTier | null;
  nextKg: number | null;
}

/** Null without a body weight to rate against. */
export function mastery(bestKg: Partial<Record<MasteryLift, number>>, sex: string | null, bodyWeightKg: number | null): LiftMastery[] | null {
  if ((sex !== "MALE" && sex !== "FEMALE") || bodyWeightKg == null || bodyWeightKg <= 0) return null;
  return MASTERY_LIFTS.map(([lift]) => {
    const thresholds = MASTERY[sex][lift];
    const best = (bestKg[lift] ?? 0) > 0 ? bestKg[lift]! : null;
    const ratio = best == null ? null : best / bodyWeightKg;
    const reached = ratio == null ? -1 : thresholds.findLastIndex((t) => ratio >= t);
    const next = TIERS[reached + 1] ?? null;
    return {
      lift,
      bestKg: best,
      ratio,
      tier: TIERS[reached] ?? null,
      nextTier: next,
      nextKg: next == null ? null : thresholds[reached + 1] * bodyWeightKg,
    };
  });
}

// ---- Year in review ----

export interface YearInReview {
  year: number;
  workouts: number;
  minutes: number;
  volumeKg: number;
  records: number;
  xp: number;
  levelStart: number;
  levelEnd: number;
  /** Unlocked during the year, in badge order. */
  badges: Badge[];
  /** 1–12, the month with the most volume (the earliest on a tie). */
  strongestMonth: number | null;
  /** The exercise in the most workouts (the first id on a tie). */
  favouriteExercise: string | null;
}

/** The year to look back on ("2026-12-01" → 2026, until January 15 the year before), else null. */
export function reviewYear(today: string): number | null {
  const year = Number(today.slice(0, 4));
  const month = Number(today.slice(5, 7));
  const day = Number(today.slice(8, 10));
  if (month === 12) return year;
  if (month === 1 && day <= RULES.reviewUntilJanuary) return year - 1;
  return null;
}

/** [progress] is playerProgress over the same [sessions]. Null when no workout that year counted. */
export function yearInReview(year: number, sessions: PlayedSession[], progress: PlayerProgress): YearInReview | null {
  const counted = sessions.filter(counts);
  const yearOf = (s: PlayedSession) => Number(s.start.slice(0, 4));
  const inYear = counted.filter((s) => yearOf(s) === year);
  if (inYear.length === 0) return null;
  const xpOf = new Map(progress.sessions.map((s) => [s.sessionId, s.total]));
  const sumXp = (list: PlayedSession[]) => list.reduce((sum, s) => sum + (xpOf.get(s.id) ?? 0), 0);
  const xpBefore = sumXp(counted.filter((s) => yearOf(s) < year));
  const xp = sumXp(inYear);

  const volumeByMonth = new Map<number, number>();
  const usage = new Map<string, number>();
  for (const s of inYear) {
    const month = Number(s.start.slice(5, 7));
    volumeByMonth.set(month, (volumeByMonth.get(month) ?? 0) + s.volumeKg);
    for (const id of new Set(s.exerciseIds)) usage.set(id, (usage.get(id) ?? 0) + 1);
  }
  const top = <K extends number | string>(entries: [K, number][]): K | null =>
    entries.filter(([, v]) => v > 0).sort(([ka, a], [kb, b]) => b - a || (ka < kb ? -1 : ka > kb ? 1 : 0))[0]?.[0] ?? null;

  return {
    year,
    workouts: inYear.length,
    minutes: inYear.reduce((sum, s) => sum + s.minutes, 0),
    volumeKg: inYear.reduce((sum, s) => sum + s.volumeKg, 0),
    records: inYear.reduce((sum, s) => sum + s.records.length, 0),
    xp,
    levelStart: levelFor(xpBefore).number,
    levelEnd: levelFor(xpBefore + xp).number,
    badges: progress.badges.filter((b) => b.unlockedOn != null && Number(b.unlockedOn.slice(0, 4)) === year).map((b) => b.badge),
    strongestMonth: top([...volumeByMonth]),
    favouriteExercise: top([...usage]),
  };
}
