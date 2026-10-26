// User-facing names, copied from the Android app's string resources so both read the same.
// Sources: core/ui/src/commonMain/composeResources/values/labels.xml and strings.xml, feature/*/src/main/res/values/strings.xml.
// Enum values are the app's enum names (core/model); `label()` falls back to a readable
// form of the name so an enum value the web doesn't know yet still shows something.

import type { Goal, PersonalRecordType, ProgressionRule, SetType, SplitType } from "./model";

/** "FRONT_DELTS" → "Front delts": the fallback for an unknown enum value. */
function enumLabel(value: string): string {
  const s = value.toLowerCase().replace(/_/g, " ");
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** The label for [value] in [labels], or a readable fallback. */
export function label(labels: Readonly<Record<string, string>>, value: string | null | undefined): string {
  if (value == null) return "";
  return labels[value] ?? enumLabel(value);
}

// ---- Profile (feature/onboarding strings.xml) ----

export const GOAL_LABELS: Record<Goal, string> = {
  STRENGTH: "Get stronger",
  HYPERTROPHY: "Build muscle",
  GENERAL_FITNESS: "General fitness",
  FAT_LOSS: "Lose fat, keep muscle",
};

export const EXPERIENCE_LABELS: Record<string, string> = {
  NEW: "New",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
};

export const SEX_LABELS: Record<string, string> = {
  FEMALE: "Female",
  MALE: "Male",
};

export const LIMITATION_LABELS: Record<string, string> = {
  SHOULDER: "Shoulder",
  KNEE: "Knee",
  LOWER_BACK: "Lower back",
  WRIST: "Wrist",
};

export const BODY_AREA_LABELS: Record<string, string> = {
  CHEST: "Chest",
  BACK: "Back",
  SHOULDERS: "Shoulders",
  ARMS: "Arms",
  LEGS: "Legs",
  GLUTES: "Glutes",
  CORE: "Core",
};

// ---- Plans (feature/onboarding SummaryStep; the two 5-day splits have no app label yet) ----

export const SPLIT_LABELS: Record<SplitType, string> = {
  FULL_BODY: "Full body",
  UPPER_LOWER: "Upper / Lower",
  PUSH_PULL_LEGS: "Push / Pull / Legs",
  UPPER_LOWER_FULL: "Upper / Lower / Full",
  PPL_UPPER_LOWER: "Push / Pull / Legs + Upper / Lower",
  CUSTOM: "Custom",
};

/** The app never names these to users; plain descriptions for the web's plan editor. */
export const PROGRESSION_RULE_LABELS: Record<ProgressionRule, string> = {
  DOUBLE_PROGRESSION: "Double progression",
  LINEAR: "Linear",
  RIR_AUTOREGULATION: "RIR autoregulation",
  NONE: "None",
};

// ---- Exercises (core/ui labels.xml) ----

export const MUSCLE_LABELS: Record<string, string> = {
  CHEST: "Chest",
  FRONT_DELTS: "Front delts",
  SIDE_DELTS: "Side delts",
  REAR_DELTS: "Rear delts",
  TRICEPS: "Triceps",
  BICEPS: "Biceps",
  FOREARMS: "Forearms",
  LATS: "Lats",
  UPPER_BACK: "Upper back",
  TRAPS: "Traps",
  LOWER_BACK: "Lower back",
  ABS: "Abs",
  OBLIQUES: "Obliques",
  GLUTES: "Glutes",
  QUADS: "Quads",
  HAMSTRINGS: "Hamstrings",
  ADDUCTORS: "Adductors",
  ABDUCTORS: "Abductors",
  CALVES: "Calves",
};

export const EQUIPMENT_LABELS: Record<string, string> = {
  BARBELL: "Barbell",
  EZ_BAR: "EZ bar",
  TRAP_BAR: "Trap bar",
  DUMBBELL: "Dumbbell",
  KETTLEBELL: "Kettlebell",
  CABLE: "Cable",
  MACHINE: "Machine",
  SMITH_MACHINE: "Smith machine",
  BENCH: "Bench",
  SQUAT_RACK: "Squat rack",
  PULL_UP_BAR: "Pull-up bar",
  DIP_STATION: "Dip station",
  RESISTANCE_BAND: "Resistance band",
  LANDMINE: "Landmine",
};

export const PATTERN_LABELS: Record<string, string> = {
  HORIZONTAL_PUSH: "Horizontal push",
  VERTICAL_PUSH: "Vertical push",
  HORIZONTAL_PULL: "Horizontal pull",
  VERTICAL_PULL: "Vertical pull",
  SQUAT: "Squat",
  HINGE: "Hinge",
  LUNGE: "Lunge",
  HIP_THRUST: "Hip thrust / bridge",
  CARRY: "Carry",
  CHEST_FLY: "Chest fly",
  LATERAL_RAISE: "Shoulder raise",
  REAR_DELT_FLY: "Rear delt fly",
  SHRUG: "Shrug",
  ELBOW_FLEXION: "Biceps curl",
  ELBOW_EXTENSION: "Triceps extension",
  KNEE_EXTENSION: "Leg extension",
  KNEE_FLEXION: "Leg curl",
  HIP_ABDUCTION: "Hip abduction",
  HIP_ADDUCTION: "Hip adduction",
  CALF_RAISE: "Calf raise",
  CORE_ANTI_EXTENSION: "Core: anti-extension",
  CORE_FLEXION: "Core: flexion",
  CORE_ROTATION: "Core: rotation / anti-rotation",
};

export const MECHANICS_LABELS: Record<string, string> = {
  COMPOUND: "Compound",
  ISOLATION: "Isolation",
};

/** An exercise with no equipment (picker_bodyweight / swap_bodyweight). */
export const BODYWEIGHT = "Bodyweight";

// ---- Workouts and sets (feature/session, feature/history, feature/today) ----

/** A logged session without a plan day (history_workout). */
export const WORKOUT = "Workout";
/** A workout planned on the calendar without a plan day (today_free_workout). */
export const FREE_WORKOUT = "Free workout";
/** A deload (today_deload_title). */
export const LIGHTER_WEEK = "Lighter week";
/** Where cloud backup lives in the app (settings backup_section). */
export const APP_BACKUP_PATH = "Profile → Backup & sync";

export const SET_TYPE_LABELS: Record<SetType, string> = {
  WARMUP: "Warm-up",
  WORKING: "Working set",
  DROP: "Drop set",
  FAILURE: "To failure",
};

/** One-letter set markers shown in the app's set list (set_warmup_short and friends). */
export const SET_TYPE_SHORT: Record<SetType, string> = {
  WARMUP: "W",
  WORKING: "",
  DROP: "D",
  FAILURE: "F",
};

export const PR_LABELS: Record<PersonalRecordType, string> = {
  ESTIMATED_1RM: "Est. 1RM",
  REPS_AT_WEIGHT: "Rep record",
  VOLUME: "Volume",
};

/**
 * The app's effort scale (RIR, RPE or off) is a phone preference and isn't synced, so the
 * web always shows RIR (settings_effort_rir). Sets logged on the RPE scale are stored as
 * RIR anyway, so the number is right either way.
 */
export const EFFORT_LABEL = "RIR";

/**
 * Why the app suggested a weight (ExerciseCard.kt progressionReason). Holds have their own
 * wording for the first time and for adding time.
 */
export function progressionReasonLabel(reason: string, timed = false): string {
  switch (reason) {
    case "CALIBRATION":
      return "First time: estimated weight. Adjust to what feels right.";
    case "FIRST_TIME":
      return timed ? "First time: hold for the bottom of the target time." : "First time: pick a weight you can do for the target reps.";
    case "INCREASE_WEIGHT":
      return "Weight up: you hit the target on every set last time.";
    case "INCREASE_TOO_EASY":
      return "Weight up: last time felt easier than planned.";
    case "ADD_REPS":
      return timed ? "Hold each set 5 seconds longer than last time." : "Same weight: aim for one more rep per set.";
    case "HOLD_TOO_HARD":
      return "Holding the weight: last time was harder than planned.";
    case "REPEAT":
      return "Same weight again: aim for the target reps.";
    case "RESET_AFTER_MISSES":
      return "10% lighter after two tough sessions. Build back up.";
    case "BODYWEIGHT_TOP_OF_RANGE":
      return "Top of the range: add weight or try a harder variation.";
    case "DELOAD":
      return "Lighter week: fewer sets, about 10% less weight. Recover and come back stronger.";
    default:
      return enumLabel(reason);
  }
}
