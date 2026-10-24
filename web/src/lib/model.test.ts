import { describe, expect, it } from "vitest";
import { GOAL_LABELS, label, PR_LABELS, progressionReasonLabel, SPLIT_LABELS } from "./labels";
import { formatDuration, formatRecord, formatSet, formatSetEffort, formatTarget, formatVolume, formatWeight, formatWeightValue } from "./model";

describe("formatWeight", () => {
  it("drops trailing zeros and keeps up to two decimals, like the app", () => {
    expect(formatWeight(80, "KG")).toBe("80 kg");
    expect(formatWeight(82.5, "KG")).toBe("82.5 kg");
    expect(formatWeight(102.0625, "KG")).toBe("102.06 kg");
    expect(formatWeightValue(100, "LB")).toBe("220.46");
  });
});

describe("formatVolume", () => {
  it("groups thousands in whole units", () => {
    expect(formatVolume(4520.4, "KG")).toBe("4,520 kg");
    expect(formatVolume(1000, "LB")).toBe("2,205 lb");
  });
});

describe("formatSet", () => {
  it("reads like the app for loaded, bodyweight and timed sets", () => {
    expect(formatSet({ weight_kg: 82.5, reps: 6 }, "barbell-bench-press", "KG")).toBe("82.5 kg × 6");
    expect(formatSet({ weight_kg: null, reps: 12 }, "pull-up", "KG")).toBe("12 reps");
    expect(formatSet({ weight_kg: null, reps: 45 }, "plank", "KG")).toBe("45 s");
    expect(formatSet({ weight_kg: 10, reps: 45 }, "plank", "KG")).toBe("10 kg × 45 s");
    expect(formatSet({ weight_kg: 50, reps: null }, "squat", "KG")).toBe("50 kg × 0");
  });
});

describe("formatRecord", () => {
  it("formats each record type as formatRecordValue does", () => {
    expect(formatRecord({ type: "ESTIMATED_1RM", value: 101.7, weight_kg: null, reps: null, exercise_id: "squat" }, "KG")).toBe("101 kg");
    expect(formatRecord({ type: "REPS_AT_WEIGHT", value: 9, weight_kg: 82.5, reps: 9, exercise_id: "squat" }, "KG")).toBe("82.5 kg × 9");
    expect(formatRecord({ type: "REPS_AT_WEIGHT", value: 60, weight_kg: null, reps: 60, exercise_id: "plank" }, "KG")).toBe("60 s");
    expect(formatRecord({ type: "VOLUME", value: 2080, weight_kg: null, reps: null, exercise_id: "squat" }, "KG")).toBe("2,080 kg");
  });
});

describe("formatTarget and effort", () => {
  it("writes prescriptions and RIR like the app", () => {
    expect(formatTarget(3, 8, 12, "squat")).toBe("3 × 8–12");
    expect(formatTarget(3, 10, 10, "squat")).toBe("3 × 10");
    expect(formatTarget(3, 30, 60, "plank")).toBe("3 × 30–60 s");
    expect(formatSetEffort({ rir: 2, rpe: null })).toBe("RIR 2");
    expect(formatSetEffort({ rir: null, rpe: 8 })).toBe("RPE 8");
    expect(formatSetEffort({ rir: null, rpe: null })).toBeNull();
  });
});

describe("formatDuration", () => {
  it("rounds to minutes and splits hours", () => {
    expect(formatDuration(44.6 * 60_000)).toBe("45 min");
    expect(formatDuration(65 * 60_000)).toBe("1 h 5 min");
  });
});

describe("labels", () => {
  it("uses the app's words", () => {
    expect(GOAL_LABELS.STRENGTH).toBe("Get stronger");
    expect(GOAL_LABELS.FAT_LOSS).toBe("Lose fat, keep muscle");
    expect(SPLIT_LABELS.PUSH_PULL_LEGS).toBe("Push / Pull / Legs");
    expect(PR_LABELS.ESTIMATED_1RM).toBe("Est. 1RM");
    expect(PR_LABELS.REPS_AT_WEIGHT).toBe("Rep record");
  });

  it("falls back to a readable name for unknown values", () => {
    expect(label({}, "FRONT_DELTS")).toBe("Front delts");
    expect(label({ A: "Known" }, "A")).toBe("Known");
    expect(label({ A: "Known" }, "SOME_NEW_VALUE")).toBe("Some new value");
    expect(label({}, null)).toBe("");
  });

  it("explains progression reasons in the app's sentences, with hold variants", () => {
    expect(progressionReasonLabel("INCREASE_WEIGHT")).toBe("Weight up: you hit the target on every set last time.");
    expect(progressionReasonLabel("ADD_REPS")).toBe("Same weight: aim for one more rep per set.");
    expect(progressionReasonLabel("ADD_REPS", true)).toBe("Hold each set 5 seconds longer than last time.");
    expect(progressionReasonLabel("FIRST_TIME", true)).toBe("First time: hold for the bottom of the target time.");
    expect(progressionReasonLabel("NEW_REASON")).toBe("New reason");
  });
});
