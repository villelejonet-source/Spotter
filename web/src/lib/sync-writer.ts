// Builds sync_rows the way the Android app writes them (core/data LocalSyncStore), so
// plans edited on the web are pulled into the phone's Room database unchanged:
// - payload = every column of the Room entity except id/syncId/updatedAt; foreign keys
//   hold the parent's sync id. The phone inserts with ContentValues, so a missing NOT NULL
//   column would make the insert fail there.
// - sync_id = 32 lowercase hex chars, like SQLite's lower(hex(randomblob(16))).
// - updated_at decides last-write-wins, so it must move past the stored value.
// - A deletion is the row with deleted_at set and no payload. The phone cascades its own
//   children without writing tombstones, so the web tombstones children explicitly.
import { z } from "zod";
import {
  BODY_AREAS,
  COMPOUND_PATTERNS,
  DAYS_PER_WEEK,
  EQUIPMENT,
  EXPERIENCE_LEVELS,
  GOALS,
  LIMITATIONS,
  MOVEMENT_PATTERNS,
  MUSCLES,
  PROGRESSION_RULES,
  SESSION_LENGTHS,
  SEXES,
  SPLIT_TYPES,
  type PlanDayRow,
  type PlanExerciseRow,
  type PlanRow,
  type ProfileRow,
} from "./model";

export interface SyncRowInput {
  table_name: "plan" | "plan_day" | "plan_exercise" | "exercise" | "user_profile" | "body_weight_entry";
  sync_id: string;
  payload: Record<string, string | number | null> | null;
  updated_at: number;
  deleted_at: number | null;
}

export function newSyncId(): string {
  return crypto.randomUUID().replace(/-/g, "");
}

// ---- What the editor sends: a whole plan, positions implied by array order ----

const syncId = z.string().regex(/^[0-9a-f]{32}$/);
const int = (min: number, max: number) => z.number().int().min(min).max(max);

export const exerciseDraftSchema = z
  .object({
    sync_id: syncId,
    exercise_id: z.string().min(1).max(100),
    sets: int(1, 20),
    rep_min: int(1, 300),
    rep_max: int(1, 300),
    target_rir: int(0, 10).nullable(),
    rest_seconds: int(0, 900),
    progression_rule: z.enum(PROGRESSION_RULES),
    superset_group: int(1, 50).nullable(),
    starting_weight_kg: z.number().min(0).max(1000).nullable(),
  })
  .refine((e) => e.rep_min <= e.rep_max, { message: "Rep range minimum is above the maximum" });

export const dayDraftSchema = z.object({
  sync_id: syncId,
  name: z.string().trim().min(1).max(60),
  exercises: z.array(exerciseDraftSchema).max(30),
});

export const planDraftSchema = z.object({
  sync_id: syncId,
  name: z.string().trim().min(1).max(80),
  split_type: z.enum(SPLIT_TYPES),
  goal: z.enum(GOALS),
  days: z.array(dayDraftSchema).min(1).max(7),
});

export type ExerciseDraft = z.infer<typeof exerciseDraftSchema>;
export type DayDraft = z.infer<typeof dayDraftSchema>;
export type PlanDraft = z.infer<typeof planDraftSchema>;

/** The plan as it is on the server now (null for a new plan). */
export interface StoredPlan extends PlanRow {
  days: (PlanDayRow & { exercises: PlanExerciseRow[] })[];
}

// ---- Payloads (keys must match the Room entities; see sync-writer.test.ts) ----

export function planPayload(p: { name: string; split_type: string; goal: string; created_at: number; is_active: boolean }) {
  return { name: p.name, splitType: p.split_type, goal: p.goal, createdAt: p.created_at, isActive: p.is_active ? 1 : 0 };
}

export function planDayPayload(planId: string, position: number, name: string) {
  return { planId, position, name };
}

export function planExercisePayload(planDayId: string, position: number, e: ExerciseDraft) {
  return {
    planDayId,
    exerciseId: e.exercise_id,
    position,
    sets: e.sets,
    repMin: e.rep_min,
    repMax: e.rep_max,
    targetRir: e.target_rir,
    restSeconds: e.rest_seconds,
    progressionRule: e.progression_rule,
    supersetGroup: e.superset_group,
    startingWeightKg: e.starting_weight_kg,
  };
}

/** A timestamp later than [previous], so last-write-wins keeps this write. */
export function stamp(now: number, previous?: number): number {
  return previous != null && previous >= now ? previous + 1 : now;
}

function sameJson(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/**
 * The rows that turn [before] into [after]: new rows, changed rows, and tombstones for
 * days and exercises that were removed. Unchanged rows aren't sent, and kept rows keep
 * their sync ids (sessions link to plan exercises for progression).
 */
export function diffPlan(before: StoredPlan | null, after: PlanDraft, now: number, isActive: boolean): SyncRowInput[] {
  const rows: SyncRowInput[] = [];
  const upsert = (table: SyncRowInput["table_name"], id: string, payload: SyncRowInput["payload"], old?: { payload: unknown; updated_at: number }) => {
    if (old && sameJson(old.payload, payload)) return;
    rows.push({ table_name: table, sync_id: id, payload, updated_at: stamp(now, old?.updated_at), deleted_at: null });
  };
  const remove = (table: SyncRowInput["table_name"], id: string, previous: number) => {
    rows.push({ table_name: table, sync_id: id, payload: null, updated_at: stamp(now, previous), deleted_at: stamp(now, previous) });
  };

  const plan = planPayload({ ...after, created_at: before?.created_at ?? now, is_active: isActive });
  upsert("plan", after.sync_id, plan, before ? { payload: planPayload(before), updated_at: before.updated_at } : undefined);

  const oldDays = new Map(before?.days.map((d) => [d.sync_id, d]));
  const oldExercises = new Map(before?.days.flatMap((d) => d.exercises.map((e) => [e.sync_id, e] as const)));
  const keptExercises = new Set<string>();

  after.days.forEach((day, dayIndex) => {
    const old = oldDays.get(day.sync_id);
    upsert(
      "plan_day",
      day.sync_id,
      planDayPayload(after.sync_id, dayIndex, day.name),
      old && { payload: planDayPayload(old.plan_id, old.position, old.name), updated_at: old.updated_at },
    );
    day.exercises.forEach((e, i) => {
      keptExercises.add(e.sync_id);
      const prev = oldExercises.get(e.sync_id);
      upsert(
        "plan_exercise",
        e.sync_id,
        planExercisePayload(day.sync_id, i, e),
        prev && { payload: planExercisePayload(prev.plan_day_id, prev.position, prev), updated_at: prev.updated_at },
      );
    });
  });

  const keptDays = new Set(after.days.map((d) => d.sync_id));
  for (const [id, e] of oldExercises) if (!keptExercises.has(id)) remove("plan_exercise", id, e.updated_at);
  for (const [id, d] of oldDays) if (!keptDays.has(id)) remove("plan_day", id, d.updated_at);
  return rows;
}

/** Tombstones for a plan and everything under it, children first. */
export function deletePlanRows(plan: StoredPlan, now: number): SyncRowInput[] {
  const tomb = (table: SyncRowInput["table_name"], id: string, previous: number): SyncRowInput => ({
    table_name: table,
    sync_id: id,
    payload: null,
    updated_at: stamp(now, previous),
    deleted_at: stamp(now, previous),
  });
  return [
    ...plan.days.flatMap((d) => d.exercises.map((e) => tomb("plan_exercise", e.sync_id, e.updated_at))),
    ...plan.days.map((d) => tomb("plan_day", d.sync_id, d.updated_at)),
    tomb("plan", plan.sync_id, plan.updated_at),
  ];
}

/** Makes [activeId] the only active plan: one row per plan whose flag changes. */
export function activatePlanRows(plans: PlanRow[], activeId: string, now: number): SyncRowInput[] {
  return plans
    .filter((p) => p.is_active !== (p.sync_id === activeId))
    .map((p) => ({
      table_name: "plan" as const,
      sync_id: p.sync_id,
      payload: planPayload({ ...p, is_active: p.sync_id === activeId }),
      updated_at: stamp(now, p.updated_at),
      deleted_at: null,
    }));
}

/** A copy of a plan with fresh sync ids, ready to save as a new plan. */
export function copyPlan(plan: StoredPlan, name: string): PlanDraft {
  return {
    sync_id: newSyncId(),
    name,
    split_type: plan.split_type,
    goal: plan.goal,
    days: plan.days.map((d) => ({
      sync_id: newSyncId(),
      name: d.name,
      exercises: d.exercises.map((e) => ({
        sync_id: newSyncId(),
        exercise_id: e.exercise_id,
        sets: e.sets,
        rep_min: e.rep_min,
        rep_max: e.rep_max,
        target_rir: e.target_rir,
        rest_seconds: e.rest_seconds,
        progression_rule: e.progression_rule,
        superset_group: e.superset_group,
        starting_weight_kg: e.starting_weight_kg,
      })),
    })),
  };
}

/** Plan rows go in parent-first order so a device pulling mid-batch can resolve parents. */
export function orderForPush(rows: SyncRowInput[]): SyncRowInput[] {
  // Custom exercises come first: plan exercises may reference one created in the same batch.
  const rank = { exercise: 0, user_profile: 0, body_weight_entry: 0, plan: 1, plan_day: 2, plan_exercise: 3 } as const;
  const live = rows.filter((r) => r.deleted_at == null).sort((a, b) => rank[a.table_name] - rank[b.table_name]);
  const dead = rows.filter((r) => r.deleted_at != null).sort((a, b) => rank[b.table_name] - rank[a.table_name]);
  return [...live, ...dead];
}

// ---- Custom exercises (Room `exercise`, synced only when isCustom = 1) ----

/** Custom exercise ids are global already, and double as the sync id (ExerciseRepository). */
export function newCustomExerciseId(): string {
  return `custom-${crypto.randomUUID()}`;
}

export const customExerciseSchema = z.object({
  name: z.string().trim().min(1).max(60),
  primary: z.array(z.enum(MUSCLES)).min(1).max(3),
  secondary: z.array(z.enum(MUSCLES)).max(5),
  pattern: z.enum(MOVEMENT_PATTERNS),
  equipment: z.array(z.enum(EQUIPMENT)).max(5),
  unilateral: z.boolean(),
  instructions: z.string().trim().max(1000).nullable(),
});
export type CustomExerciseDraft = z.infer<typeof customExerciseSchema>;

export function customExercisePayload(id: string, e: CustomExerciseDraft) {
  return {
    id,
    name: e.name,
    primaryMuscles: e.primary.join(","),
    secondaryMuscles: e.secondary.filter((m) => !e.primary.includes(m)).join(","),
    movementPattern: e.pattern,
    equipment: e.equipment.join(","),
    mechanics: COMPOUND_PATTERNS.has(e.pattern) ? "COMPOUND" : "ISOLATION",
    difficulty: "BEGINNER",
    unilateral: e.unilateral ? 1 : 0,
    instructions: e.instructions || null,
    isCustom: 1,
  };
}

// ---- Training profile (Room `user_profile`, a singleton with id 1 and sync id "user-profile") ----

export const PROFILE_SYNC_ID = "user-profile";

export const profileSchema = z.object({
  sex: z.enum(SEXES),
  height_cm: z.number().min(100).max(250),
  birth_date_epoch_day: z.number().int().min(-25_000).max(20_000),
  body_weight_kg: z.number().min(25).max(300),
  units: z.enum(["KG", "LB"]),
  experience: z.enum(EXPERIENCE_LEVELS),
  goal: z.enum(GOALS),
  days_per_week: z.number().int().refine((d) => (DAYS_PER_WEEK as readonly number[]).includes(d)),
  session_length_minutes: z.number().int().refine((m) => (SESSION_LENGTHS as readonly number[]).includes(m)),
  equipment: z.array(z.enum(EQUIPMENT)),
  focus_areas: z.array(z.enum(BODY_AREAS)),
  limitations: z.array(z.enum(LIMITATIONS)),
});
export type ProfileDraft = z.infer<typeof profileSchema>;

/** The editable form of a profile as the views return it. */
export function profileDraftFrom(p: ProfileRow): ProfileDraft {
  const list = <T extends string>(v: string) => (v ? (v.split(",") as T[]) : []);
  return {
    sex: p.sex as ProfileDraft["sex"],
    height_cm: p.height_cm,
    birth_date_epoch_day: p.birth_date_epoch_day,
    body_weight_kg: p.body_weight_kg,
    units: p.units,
    experience: p.experience as ProfileDraft["experience"],
    goal: p.goal,
    days_per_week: p.days_per_week,
    session_length_minutes: p.session_length_minutes,
    equipment: list(p.equipment),
    focus_areas: list(p.focus_areas),
    limitations: list(p.limitations),
  };
}

export function profilePayload(p: ProfileDraft) {
  return {
    id: 1,
    sex: p.sex,
    heightCm: p.height_cm,
    birthDate: p.birth_date_epoch_day,
    bodyWeightKg: p.body_weight_kg,
    units: p.units,
    experience: p.experience,
    goal: p.goal,
    daysPerWeek: p.days_per_week,
    sessionLengthMinutes: p.session_length_minutes,
    equipment: p.equipment.join(","),
    focusAreas: p.focus_areas.join(","),
    limitations: p.limitations.join(","),
  };
}

// ---- Body weight (Room `body_weight_entry`, one per day) ----

export const bodyWeightSchema = z.object({
  epoch_day: z.number().int().min(0).max(40_000),
  weight_kg: z.number().min(25).max(300),
});

export function bodyWeightPayload(epochDay: number, weightKg: number) {
  return { date: epochDay, weightKg };
}
