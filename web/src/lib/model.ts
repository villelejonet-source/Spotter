// Mirrors the Android app's domain types (core/model) as far as the web needs them.
// Enum values are stored by name in the synced payloads, so these names must match.

export const SPLIT_TYPES = ["FULL_BODY", "UPPER_LOWER", "PUSH_PULL_LEGS", "UPPER_LOWER_FULL", "PPL_UPPER_LOWER", "CUSTOM"] as const;
export type SplitType = (typeof SPLIT_TYPES)[number];

export const GOALS = ["STRENGTH", "HYPERTROPHY", "GENERAL_FITNESS", "FAT_LOSS"] as const;
export type Goal = (typeof GOALS)[number];

export const PROGRESSION_RULES = ["DOUBLE_PROGRESSION", "LINEAR", "RIR_AUTOREGULATION", "NONE"] as const;
export type ProgressionRule = (typeof PROGRESSION_RULES)[number];

export type SetType = "WARMUP" | "WORKING" | "DROP" | "FAILURE";
export type PersonalRecordType = "ESTIMATED_1RM" | "REPS_AT_WEIGHT" | "VOLUME";
export type WeightUnit = "KG" | "LB";

export const MUSCLES = [
  "CHEST", "FRONT_DELTS", "SIDE_DELTS", "REAR_DELTS", "TRICEPS", "BICEPS", "FOREARMS",
  "LATS", "UPPER_BACK", "TRAPS", "LOWER_BACK", "ABS", "OBLIQUES",
  "GLUTES", "QUADS", "HAMSTRINGS", "ADDUCTORS", "ABDUCTORS", "CALVES",
] as const;
export type Muscle = (typeof MUSCLES)[number];

export const EQUIPMENT = [
  "BARBELL", "EZ_BAR", "TRAP_BAR", "DUMBBELL", "KETTLEBELL", "CABLE", "MACHINE", "SMITH_MACHINE",
  "BENCH", "SQUAT_RACK", "PULL_UP_BAR", "DIP_STATION", "RESISTANCE_BAND", "LANDMINE",
] as const;
export type Equipment = (typeof EQUIPMENT)[number];

/** Holds logged in seconds rather than reps; the seconds go in the set's reps field. */
export const TIMED_EXERCISE_IDS = new Set(["plank", "side-plank", "copenhagen-plank", "hollow-body-hold"]);

export const SPLIT_LABELS: Record<SplitType, string> = {
  FULL_BODY: "Full body",
  UPPER_LOWER: "Upper / lower",
  PUSH_PULL_LEGS: "Push / pull / legs",
  UPPER_LOWER_FULL: "Upper / lower / full",
  PPL_UPPER_LOWER: "PPL + upper / lower",
  CUSTOM: "Custom",
};

export const GOAL_LABELS: Record<Goal, string> = {
  STRENGTH: "Strength",
  HYPERTROPHY: "Muscle growth",
  GENERAL_FITNESS: "General fitness",
  FAT_LOSS: "Fat loss",
};

export const PROGRESSION_LABELS: Record<ProgressionRule, string> = {
  DOUBLE_PROGRESSION: "Double progression",
  LINEAR: "Linear",
  RIR_AUTOREGULATION: "RIR autoregulation",
  NONE: "None",
};

export const PR_LABELS: Record<PersonalRecordType, string> = {
  ESTIMATED_1RM: "Estimated 1RM",
  REPS_AT_WEIGHT: "Reps at weight",
  VOLUME: "Volume",
};

/** "FRONT_DELTS" → "Front delts". */
export function enumLabel(value: string): string {
  const s = value.toLowerCase().replace(/_/g, " ");
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Enum lists are stored comma-joined (core/data Converters). */
export function splitEnumList(value: string | null | undefined): string[] {
  return value ? value.split(",").filter(Boolean) : [];
}

// ---- Rows as returned by the views in supabase/migrations/20261023000000_web_views.sql ----

export interface ProfileRow {
  sync_id: string;
  sex: string;
  height_cm: number;
  birth_date_epoch_day: number;
  body_weight_kg: number;
  units: WeightUnit;
  experience: string;
  goal: Goal;
  days_per_week: number;
  session_length_minutes: number;
  equipment: string;
  focus_areas: string;
  limitations: string;
  updated_at: number;
}

export interface PlanRow {
  sync_id: string;
  name: string;
  split_type: SplitType;
  goal: Goal;
  created_at: number;
  is_active: boolean;
  updated_at: number;
}

export interface PlanDayRow {
  sync_id: string;
  plan_id: string;
  position: number;
  name: string;
  updated_at: number;
}

export interface PlanExerciseRow {
  sync_id: string;
  plan_day_id: string;
  exercise_id: string;
  position: number;
  sets: number;
  rep_min: number;
  rep_max: number;
  target_rir: number | null;
  rest_seconds: number;
  progression_rule: ProgressionRule;
  superset_group: number | null;
  starting_weight_kg: number | null;
  updated_at: number;
}

export interface SessionSummaryRow {
  sync_id: string;
  started_at: number;
  ended_at: number | null;
  plan_day_id: string | null;
  plan_day_name: string | null;
  plan_id: string | null;
  is_deload: boolean;
  notes: string | null;
  exercise_count: number;
  completed_sets: number;
  volume_kg: number;
}

export interface SessionExerciseRow {
  sync_id: string;
  session_id: string;
  exercise_id: string;
  position: number;
  substituted_from_exercise_id: string | null;
  superset_group: number | null;
  notes: string | null;
  rest_seconds: number | null;
  progression_reason: string | null;
}

export interface SetEntryRow {
  sync_id: string;
  session_exercise_id: string;
  position: number;
  weight_kg: number | null;
  reps: number | null;
  rir: number | null;
  rpe: number | null;
  set_type: SetType;
  completed_at: number | null;
  notes: string | null;
}

export interface PersonalRecordRow {
  sync_id: string;
  exercise_id: string;
  type: PersonalRecordType;
  value: number;
  weight_kg: number | null;
  reps: number | null;
  achieved_at: number;
  session_id: string | null;
}

/** Row of v_scheduled_session: a workout planned on a date (app schema 7). */
export interface ScheduledRow {
  sync_id: string;
  epoch_day: number;
  /** Null for a free workout; may point at a deleted plan day. */
  plan_day_id: string | null;
  notes: string | null;
  /** Set once the workout was started in the app. */
  session_id: string | null;
  updated_at: number;
}

export interface BodyWeightRow {
  sync_id: string;
  epoch_day: number;
  weight_kg: number;
  updated_at: number;
}

// ---- Formatting ----

const LB_PER_KG = 2.2046226218;

export function toUnit(kg: number, unit: WeightUnit): number {
  return unit === "LB" ? kg * LB_PER_KG : kg;
}

export function fromUnit(value: number, unit: WeightUnit): number {
  return unit === "LB" ? value / LB_PER_KG : value;
}

export function formatWeight(kg: number | null | undefined, unit: WeightUnit, digits = 1): string {
  if (kg == null) return "BW";
  const v = toUnit(kg, unit);
  return `${Number(v.toFixed(digits))} ${unit.toLowerCase()}`;
}

export function formatVolume(kg: number, unit: WeightUnit): string {
  const v = Math.round(toUnit(kg, unit));
  return `${v.toLocaleString("en-US")} ${unit.toLowerCase()}`;
}

export function formatDuration(ms: number): string {
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

/** A set as text: "80 kg × 8", "BW × 12", or "45 s" for holds. */
export function formatSet(set: Pick<SetEntryRow, "weight_kg" | "reps">, exerciseId: string, unit: WeightUnit): string {
  if (TIMED_EXERCISE_IDS.has(exerciseId)) {
    const load = set.weight_kg ? `${formatWeight(set.weight_kg, unit)} · ` : "";
    return `${load}${set.reps ?? "–"} s`;
  }
  return `${formatWeight(set.weight_kg, unit)} × ${set.reps ?? "–"}`;
}

/** Epley estimated one-rep max; a single is its own 1RM (core/model Progress.kt). */
export function epley(weightKg: number, reps: number): number {
  return reps <= 1 ? weightKg : weightKg * (1 + reps / 30);
}

// ---- Profile and exercise enums (core/model UserProfile.kt, BodyArea.kt, Exercise.kt) ----

export const SEXES = ["FEMALE", "MALE"] as const;
export const EXPERIENCE_LEVELS = ["NEW", "INTERMEDIATE", "ADVANCED"] as const;
export const LIMITATIONS = ["SHOULDER", "KNEE", "LOWER_BACK", "WRIST"] as const;
export const BODY_AREAS = ["CHEST", "BACK", "SHOULDERS", "ARMS", "LEGS", "GLUTES", "CORE"] as const;
export const DAYS_PER_WEEK = [2, 3, 4, 5, 6] as const;
export const SESSION_LENGTHS = [30, 45, 60, 75] as const;

export const MOVEMENT_PATTERNS = [
  "HORIZONTAL_PUSH", "VERTICAL_PUSH", "HORIZONTAL_PULL", "VERTICAL_PULL",
  "SQUAT", "HINGE", "LUNGE", "HIP_THRUST", "CARRY",
  "CHEST_FLY", "LATERAL_RAISE", "REAR_DELT_FLY", "SHRUG",
  "ELBOW_FLEXION", "ELBOW_EXTENSION",
  "KNEE_EXTENSION", "KNEE_FLEXION", "HIP_ABDUCTION", "HIP_ADDUCTION", "CALF_RAISE",
  "CORE_ANTI_EXTENSION", "CORE_FLEXION", "CORE_ROTATION",
] as const;

/** Patterns the app treats as compound when creating a custom exercise (ExerciseRepository). */
export const COMPOUND_PATTERNS = new Set<string>([
  "HORIZONTAL_PUSH", "VERTICAL_PUSH", "HORIZONTAL_PULL", "VERTICAL_PULL",
  "SQUAT", "HINGE", "LUNGE", "HIP_THRUST", "CARRY",
]);

/** Display groups for weekly volume (core/model Progress.kt MuscleGroup). */
export const MUSCLE_GROUPS: Record<string, readonly Muscle[]> = {
  Chest: ["CHEST"],
  Back: ["LATS", "UPPER_BACK", "TRAPS", "LOWER_BACK"],
  Shoulders: ["FRONT_DELTS", "SIDE_DELTS", "REAR_DELTS"],
  Biceps: ["BICEPS", "FOREARMS"],
  Triceps: ["TRICEPS"],
  Quads: ["QUADS"],
  Hamstrings: ["HAMSTRINGS"],
  Glutes: ["GLUTES", "ABDUCTORS", "ADDUCTORS"],
  Calves: ["CALVES"],
  Core: ["ABS", "OBLIQUES"],
};

export function muscleGroupOf(muscle: string): string | undefined {
  return Object.keys(MUSCLE_GROUPS).find((g) => (MUSCLE_GROUPS[g] as readonly string[]).includes(muscle));
}

/** Weekly working sets per muscle group, by goal (core/engine MuscleBalance.target). */
export function volumeTarget(goal: string | null | undefined): [number, number] {
  return goal === "HYPERTROPHY" ? [10, 20] : [8, 12];
}
