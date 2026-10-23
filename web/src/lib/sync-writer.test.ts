import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  activatePlanRows,
  CHANGED_ELSEWHERE,
  planConflict,
  planVersions,
  bodyWeightPayload,
  customExercisePayload,
  newCustomExerciseId,
  profilePayload,
  deletePlanRows,
  diffPlan,
  newSyncId,
  orderForPush,
  planDayPayload,
  planDraftSchema,
  planExercisePayload,
  planPayload,
  stamp,
  type ExerciseDraft,
  type PlanDraft,
  type StoredPlan,
} from "./sync-writer";

// The Room schema the phone runs; the newest exported version wins.
function roomColumns(table: string, keepsId = false): { name: string; notNull: boolean }[] {
  const dir = resolve(__dirname, "../../../core/data/schemas/com.viktorolsson.spotter.core.data.db.SpotterDatabase");
  const latest = Math.max(...readdirSync(dir).map((f) => parseInt(f)));
  const schema = JSON.parse(readFileSync(resolve(dir, `${latest}.json`), "utf8"));
  const entity = schema.database.entities.find((e: { tableName: string }) => e.tableName === table);
  return entity.fields
    .filter((f: { columnName: string }) => !["syncId", "updatedAt", ...(keepsId ? [] : ["id"])].includes(f.columnName))
    .map((f: { columnName: string; notNull?: boolean }) => ({ name: f.columnName, notNull: !!f.notNull }));
}

const exercise: ExerciseDraft = {
  sync_id: newSyncId(),
  exercise_id: "barbell-bench-press",
  sets: 3,
  rep_min: 6,
  rep_max: 10,
  target_rir: 2,
  rest_seconds: 120,
  progression_rule: "DOUBLE_PROGRESSION",
  superset_group: null,
  starting_weight_kg: null,
};

describe("payloads match the Room entities", () => {
  it.each([
    ["plan", planPayload({ name: "P", split_type: "FULL_BODY", goal: "STRENGTH", created_at: 1, is_active: true })],
    ["plan_day", planDayPayload(newSyncId(), 0, "Day A")],
    ["plan_exercise", planExercisePayload(newSyncId(), 0, exercise)],
    [
      "exercise",
      customExercisePayload(newCustomExerciseId(), {
        name: "Band pull-apart",
        primary: ["REAR_DELTS"],
        secondary: ["UPPER_BACK"],
        pattern: "REAR_DELT_FLY",
        equipment: ["RESISTANCE_BAND"],
        unilateral: false,
        instructions: null,
      }),
    ],
    [
      "user_profile",
      profilePayload({
        sex: "FEMALE",
        height_cm: 170,
        birth_date_epoch_day: 11000,
        body_weight_kg: 65,
        units: "KG",
        experience: "NEW",
        goal: "STRENGTH",
        days_per_week: 3,
        session_length_minutes: 45,
        equipment: [],
        focus_areas: [],
        limitations: [],
      }),
    ],
    ["body_weight_entry", bodyWeightPayload(20000, 70)],
  ])("%s", (table, payload) => {
    // Custom exercises and the profile keep their own id column (LocalSyncStore.keepsLocalId).
    const columns = roomColumns(table, table === "exercise" || table === "user_profile");
    expect(Object.keys(payload).sort()).toEqual(columns.map((c) => c.name).sort());
    for (const c of columns.filter((c) => c.notNull)) {
      expect(payload[c.name as keyof typeof payload], c.name).not.toBeNull();
    }
  });
});

describe("custom exercise payload", () => {
  it("drops secondary muscles that are also primary and derives mechanics like the app", () => {
    const p = customExercisePayload("custom-x", {
      name: "Landmine press",
      primary: ["FRONT_DELTS"],
      secondary: ["FRONT_DELTS", "TRICEPS"],
      pattern: "VERTICAL_PUSH",
      equipment: ["LANDMINE", "BARBELL"],
      unilateral: true,
      instructions: "",
    });
    expect(p).toMatchObject({ secondaryMuscles: "TRICEPS", mechanics: "COMPOUND", equipment: "LANDMINE,BARBELL", unilateral: 1, instructions: null });
  });
});

describe("newSyncId", () => {
  it("looks like the phone's ids", () => {
    expect(newSyncId()).toMatch(/^[0-9a-f]{32}$/);
  });
});

describe("stamp", () => {
  it("moves past a stored value from a clock that runs ahead", () => {
    expect(stamp(100, 50)).toBe(100);
    expect(stamp(100, 200)).toBe(201);
  });
});

const draft = (): PlanDraft => ({
  sync_id: newSyncId(),
  name: "Upper / lower",
  split_type: "UPPER_LOWER",
  goal: "HYPERTROPHY",
  days: [
    { sync_id: newSyncId(), name: "Upper", exercises: [exercise] },
    { sync_id: newSyncId(), name: "Lower", exercises: [{ ...exercise, sync_id: newSyncId(), exercise_id: "back-squat" }] },
  ],
});

/** What the views would return after [d] was saved at [at]. */
function stored(d: PlanDraft, at: number, active = true): StoredPlan {
  return {
    sync_id: d.sync_id,
    name: d.name,
    split_type: d.split_type,
    goal: d.goal,
    created_at: at,
    is_active: active,
    updated_at: at,
    days: d.days.map((day, i) => ({
      sync_id: day.sync_id,
      plan_id: d.sync_id,
      position: i,
      name: day.name,
      updated_at: at,
      exercises: day.exercises.map((e, j) => ({ ...e, plan_day_id: day.sync_id, position: j, updated_at: at })),
    })),
  };
}

describe("diffPlan", () => {
  it("writes every row of a new plan", () => {
    const rows = diffPlan(null, draft(), 1000, true);
    expect(rows.map((r) => r.table_name)).toEqual(["plan", "plan_day", "plan_exercise", "plan_day", "plan_exercise"]);
    expect(rows[0].payload).toMatchObject({ isActive: 1, createdAt: 1000 });
  });

  it("sends nothing when nothing changed", () => {
    const d = draft();
    expect(diffPlan(stored(d, 1000), d, 2000, true)).toEqual([]);
  });

  it("sends only the changed exercise and tombstones a removed day with its exercises", () => {
    const d = draft();
    const edited: PlanDraft = { ...d, days: [{ ...d.days[0], exercises: [{ ...exercise, sets: 4 }] }] };
    const rows = diffPlan(stored(d, 1000), edited, 2000, true);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toMatchObject({ table_name: "plan_exercise", sync_id: exercise.sync_id, deleted_at: null });
    expect(rows[0].payload).toMatchObject({ sets: 4 });
    const dead = rows.filter((r) => r.deleted_at != null);
    expect(dead.map((r) => r.sync_id).sort()).toEqual([d.days[1].sync_id, d.days[1].exercises[0].sync_id].sort());
    expect(dead.every((r) => r.payload === null)).toBe(true);
  });

  it("re-positions on reorder", () => {
    const d = draft();
    const rows = diffPlan(stored(d, 1000), { ...d, days: [d.days[1], d.days[0]] }, 2000, true);
    expect(rows.map((r) => [r.sync_id, r.payload?.position])).toEqual([
      [d.days[1].sync_id, 0],
      [d.days[0].sync_id, 1],
    ]);
  });
});

describe("deletePlanRows", () => {
  it("tombstones children before the plan", () => {
    const rows = deletePlanRows(stored(draft(), 1000), 2000);
    expect(rows.map((r) => r.table_name)).toEqual(["plan_exercise", "plan_exercise", "plan_day", "plan_day", "plan"]);
    expect(rows.every((r) => r.deleted_at === 2000 && r.payload === null)).toBe(true);
  });
});

describe("activatePlanRows", () => {
  it("only touches plans whose flag changes", () => {
    const a = stored(draft(), 1000, true);
    const b = stored(draft(), 1000, false);
    const rows = activatePlanRows([a, b], b.sync_id, 2000);
    expect(rows.map((r) => [r.sync_id, r.payload?.isActive])).toEqual([
      [a.sync_id, 0],
      [b.sync_id, 1],
    ]);
  });
});

describe("orderForPush", () => {
  it("puts parents first for upserts and children first for deletes", () => {
    const d = draft();
    const rows = orderForPush([...deletePlanRows(stored(d, 1), 2), ...diffPlan(null, draft(), 3, false)].reverse());
    const live = rows.filter((r) => !r.deleted_at).map((r) => r.table_name);
    expect(live[0]).toBe("plan");
    expect(rows.at(-1)?.table_name).toBe("plan");
  });
});

describe("planDraftSchema", () => {
  it("rejects an inverted rep range and bad ids", () => {
    const d = draft();
    d.days[0].exercises[0] = { ...exercise, rep_min: 12, rep_max: 8 };
    expect(planDraftSchema.safeParse(d).success).toBe(false);
    expect(planDraftSchema.safeParse({ ...draft(), sync_id: "nope" }).success).toBe(false);
    expect(planDraftSchema.safeParse(draft()).success).toBe(true);
  });
});

describe("planConflict", () => {
  it("allows saving over the version the editor loaded", () => {
    const d = draft();
    const current = stored(d, 1000);
    expect(planConflict(current, { ...d, name: "Renamed" }, planVersions(current), new Set())).toBeNull();
  });

  it("refuses when the phone changed a row since loading", () => {
    const d = draft();
    const loaded = stored(d, 1000);
    const now = stored(d, 1000);
    now.days[0].exercises[0].updated_at = 2000; // edited on the phone
    expect(planConflict(now, { ...d, name: "Renamed" }, planVersions(loaded), new Set())).toBe(CHANGED_ELSEWHERE);
  });

  it("refuses to bring back a row the phone deleted", () => {
    const d = draft();
    const loaded = stored(d, 1000);
    const now = stored({ ...d, days: [d.days[0]] }, 1000); // phone removed the second day
    expect(planConflict(now, d, planVersions(loaded), new Set())).toBe(CHANGED_ELSEWHERE);
  });

  it("refuses to remove a row the phone changed", () => {
    const d = draft();
    const loaded = stored(d, 1000);
    const now = stored(d, 1000);
    now.days[1].updated_at = 2000;
    expect(planConflict(now, { ...d, days: [d.days[0]] }, planVersions(loaded), new Set())).toBe(CHANGED_ELSEWHERE);
  });

  it("refuses new rows whose ids already exist elsewhere, and duplicates", () => {
    const d = draft();
    const foreign = newSyncId();
    const withForeign: PlanDraft = { ...d, days: [...d.days, { sync_id: foreign, name: "Stolen", exercises: [] }] };
    expect(planConflict(null, withForeign, {}, new Set([foreign]))).toMatch(/belongs elsewhere/);
    const dup: PlanDraft = { ...d, days: [d.days[0], d.days[0]] };
    expect(planConflict(null, dup, {}, new Set())).toMatch(/duplicated/);
  });

  it("allows a brand-new plan", () => {
    expect(planConflict(null, draft(), {}, new Set())).toBeNull();
  });
});
