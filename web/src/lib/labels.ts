// User-facing names, copied from the Android app's string resources so both read the same.
// Sources: core/ui/src/commonMain/composeResources/values/labels.xml and strings.xml, and each feature's
// strings.xml (feature/*/src/commonMain/composeResources/values/, or src/main/res/values/ until it's ported).
// Enum values are the app's enum names (core/model); `label()` falls back to a readable
// form of the name so an enum value the web doesn't know yet still shows something.

import type { Badge, LevelTitle } from "./gamification";
import type { Goal, PersonalRecordType, PlanExerciseRow, PlanRow, ProgressionRule, SetType, SplitType } from "./model";

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

// ---- Plans (core/ui program_* strings; the two 5-day splits as the app names those plans) ----

export const SPLIT_LABELS: Record<SplitType, string> = {
  FULL_BODY: "Full body",
  UPPER_LOWER: "Upper / Lower",
  PUSH_PULL_LEGS: "Push / Pull / Legs",
  UPPER_LOWER_FULL: "Upper / Lower + Full body",
  PPL_UPPER_LOWER: "Push / Pull / Legs + Upper / Lower",
  CUSTOM: "Custom",
};

/** What kind of plan it is, as the app's program cards name it (core/ui program_*, `Plan.program()`): 5/3/1 is stored as a custom plan whose lifts have a training max. */
export function planKindLabel(plan: Pick<PlanRow, "split_type"> & { days: { exercises: Pick<PlanExerciseRow, "training_max_kg">[] }[] }): string {
  const fiveThreeOne = plan.days.some((d) => d.exercises.some((e) => e.training_max_kg != null));
  return fiveThreeOne ? FIVE_THREE_ONE_LABEL : label(SPLIT_LABELS, plan.split_type);
}

/** The app never names these to users; plain descriptions for the web's plan editor. */
export const PROGRESSION_RULE_LABELS: Record<ProgressionRule, string> = {
  DOUBLE_PROGRESSION: "Double progression",
  LINEAR: "Linear",
  // The app treats it as double progression, whose RIR checks every rule has.
  RIR_AUTOREGULATION: "Double progression (RIR)",
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
/** 5/3/1 in the plan editor's progression menu, and its weeks (app: target_531_week, five31_*). */
export const FIVE_THREE_ONE_LABEL = "5/3/1";
/** A 5/3/1 last set, done for as many reps as possible. */
export const AMRAP_LABEL = "AMRAP";
export function cycleWeekLabel(week: number): string {
  return week >= 4 ? "Week 4 (deload)" : `Week ${week}`;
}

/** A deload session: a whole lighter week, or one day chosen at the check-in (app: session_lighter). */
export const LIGHTER_SESSION = "Lighter session";
/** A workout kept as planned on a low day (app: exercise_log_low_day). */
export const LOW_DAY = "Low day";
/** Under a progress chart (app: exercise_chart_left_out). */
export const CHART_LEFT_OUT = "Lighter sessions and low days are left out of the chart, so they don't read as a drop.";

/** How a workout felt, 1 to 5 (app: core/ui feel_1–feel_5). */
export const FEEL_LABELS: Record<number, string> = { 1: "Very low", 2: "Low", 3: "Okay", 4: "Good", 5: "Great" };
export const READINESS_LABEL = "Before";
export const FEEL_LABEL = "After";
export const SESSION_EFFORT_LABEL = "Effort";

/** "Before: Low · After: Good · Effort 7/10", leaving out what wasn't rated; null when nothing was. */
export function formatCheckIn(readiness: number | null, feel: number | null, effort: number | null): string | null {
  const parts = [
    readiness != null && FEEL_LABELS[readiness] ? `${READINESS_LABEL}: ${FEEL_LABELS[readiness]}` : null,
    feel != null && FEEL_LABELS[feel] ? `${FEEL_LABEL}: ${FEEL_LABELS[feel]}` : null,
    effort != null ? `${SESSION_EFFORT_LABEL} ${effort}/10` : null,
  ].filter((p): p is string => p != null);
  return parts.length ? parts.join(" · ") : null;
}
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

// ---- Levels and badges (core/ui labels.xml) ----

export const LEVEL_TITLE_LABELS: Record<LevelTitle, string> = {
  FRESH_CHALK: "Fresh Chalk",
  BENCH_WARMER: "Bench Warmer",
  GYM_REGULAR: "Gym Regular",
  PLATE_COLLECTOR: "Plate Collector",
  IRON_ADDICT: "Iron Addict",
  BARBELL_BARON: "Barbell Baron",
  SQUAT_ROYALTY: "Squat Royalty",
  IRON_LEGEND: "Iron Legend",
  MYTHICAL_BEAST: "Mythical Beast",
};

export const BADGE_LABELS: Record<Badge, { name: string; how: string }> = {
  FIRST_REP: { name: "First Rep", how: "Finish your first workout" },
  TEN_DOWN: { name: "Ten Down", how: "Finish 10 workouts" },
  HALF_CENTURY: { name: "Half Century", how: "Finish 50 workouts" },
  CENTURION: { name: "Centurion", how: "Finish 100 workouts" },
  CLUB_250: { name: "250 Club", how: "Finish 250 workouts" },
  FIRST_PR: { name: "First PR", how: "Set a personal record" },
  PR_HUNTER: { name: "PR Hunter", how: "Set 10 personal records" },
  RECORD_MACHINE: { name: "Record Machine", how: "Set 50 personal records" },
  TRIPLE_THREAT: { name: "Triple Threat", how: "Set all three kinds of record in one workout" },
  STREAK_4: { name: "On a Roll", how: "Train 4 weeks in a row" },
  STREAK_12: { name: "Habit Formed", how: "Train 12 weeks in a row" },
  STREAK_26: { name: "Half a Year Strong", how: "Train 26 weeks in a row" },
  STREAK_52: { name: "Year of Iron", how: "Train 52 weeks in a row" },
  GRAND_PIANO: { name: "Piano Mover", how: "Lift a grand piano, all workouts added up" },
  CAR: { name: "Car Lifter", how: "Lift a car, all workouts added up" },
  ELEPHANT: { name: "Elephant Hauler", how: "Lift an elephant, all workouts added up" },
  BLUE_WHALE: { name: "Whale Wrangler", how: "Lift a blue whale, all workouts added up" },
  EIFFEL_TOWER: { name: "Tower Toppler", how: "Lift the Eiffel Tower, all workouts added up" },
  LISTENED_TO_YOUR_BODY: { name: "Listened to Your Body", how: "Take a lighter session on a low day" },
  COMEBACK_KID: { name: "Comeback Kid", how: "Train again after 3 weeks or more away" },
  EARLY_BIRD: { name: "Early Bird", how: "Start 5 workouts before 7 am" },
  NIGHT_OWL: { name: "Night Owl", how: "Start 5 workouts at 9 pm or later" },
  EXPLORER: { name: "Explorer", how: "Train 20 different exercises" },
};
