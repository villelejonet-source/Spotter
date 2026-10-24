import "server-only";
import { cache } from "react";
import { buildCatalog, type CustomExerciseRow } from "./catalog";
import type {
  BodyWeightRow,
  PersonalRecordRow,
  PlanDayRow,
  PlanExerciseRow,
  PlanRow,
  ProfileRow,
  ScheduledRow,
  SessionExerciseRow,
  SessionSummaryRow,
  SetEntryRow,
  WeightUnit,
} from "./model";
import { createClient } from "./supabase/server";

type Client = Awaited<ReturnType<typeof createClient>>;
type Query = ReturnType<ReturnType<Client["from"]>["select"]>;

const PAGE = 1000;

/**
 * Every matching row of a view, paging past PostgREST's row limit by key (each page
 * continues after the last key, so deep pages stay as cheap as the first). RLS limits it
 * to the signed-in user.
 */
async function selectAll<T>(
  client: Client,
  view: string,
  { filter, key = "sync_id", columns = "*" }: { filter?: (q: Query) => Query; key?: string; columns?: string } = {},
): Promise<T[]> {
  const rows: T[] = [];
  let after: string | null = null;
  for (;;) {
    // Typed as "*": the views aren't in generated types, so every column list returns loose rows anyway.
    let q = client.from(view).select(columns as "*");
    if (filter) q = filter(q);
    if (after != null) q = q.gt(key, after);
    const { data, error } = await q.order(key).limit(PAGE);
    if (error) throw new Error(`${view}: ${error.message}`);
    const page = data as unknown as (T & Record<string, unknown>)[];
    rows.push(...page);
    if (page.length < PAGE) return rows;
    after = String(page[page.length - 1][key]);
  }
}

export const getProfile = cache(async (): Promise<ProfileRow | null> => {
  const client = await createClient();
  const { data, error } = await client.from("v_profile").select("*").maybeSingle();
  if (error) throw new Error(error.message);
  return data as ProfileRow | null;
});

export async function getUnit(): Promise<WeightUnit> {
  return (await getProfile())?.units ?? "KG";
}

export const getCatalog = cache(async () => {
  const client = await createClient();
  return buildCatalog(await selectAll<CustomExerciseRow>(client, "v_custom_exercise", { key: "id" }));
});

/** All sessions, newest first. */
export const getSessions = cache(async (): Promise<SessionSummaryRow[]> => {
  const client = await createClient();
  const rows = await selectAll<SessionSummaryRow>(client, "v_session_summary");
  return rows.sort((a, b) => b.started_at - a.started_at);
});

export async function getSessionDetail(syncId: string) {
  const client = await createClient();
  const [{ data: session, error }, exercises, sets] = await Promise.all([
    client.from("v_session_summary").select("*").eq("sync_id", syncId).maybeSingle(),
    selectAll<SessionExerciseRow>(client, "v_session_exercise", { filter: (q) => q.eq("session_id", syncId) }),
    selectAll<SetEntryRow>(client, "v_set_history", { filter: (q) => q.eq("session_id", syncId) }),
  ]);
  if (error) throw new Error(error.message);
  if (!session) return null;
  return {
    session: session as SessionSummaryRow,
    exercises: exercises
      .sort((a, b) => a.position - b.position)
      .map((e) => ({
        ...e,
        sets: sets.filter((s) => s.session_exercise_id === e.sync_id).sort((a, b) => a.position - b.position),
      })),
  };
}

/** A logged set with its exercise and workout (v_set_history). */
export interface SetWithExercise extends SetEntryRow {
  exercise_id: string;
  session_id: string;
  session_started_at: number;
  session_finished: boolean;
}

/**
 * Logged sets from finished workouts (or running ones too, for the export), filtered on
 * the server: by exercise, and/or workouts started at or after [since] (epoch ms). With
 * no filter it's every set, which only the progress overview and the export need.
 */
export const getSetHistory = cache(async (exerciseId?: string, since?: number, includeRunning = false): Promise<SetWithExercise[]> => {
  const client = await createClient();
  return selectAll<SetWithExercise>(client, "v_set_history", {
    filter: (q) => {
      let f = includeRunning ? q : q.eq("session_finished", true);
      if (exerciseId) f = f.eq("exercise_id", exerciseId);
      if (since != null) f = f.gte("session_started_at", since);
      return f;
    },
  });
});

/** Distinct workouts each exercise was done in, for the exercise library. */
export const getExerciseUsage = cache(async (): Promise<Map<string, number>> => {
  const client = await createClient();
  const rows = await selectAll<{ sync_id: string; exercise_id: string; session_id: string }>(client, "v_session_exercise", {
    columns: "sync_id,exercise_id,session_id",
  });
  const sessions = new Map<string, Set<string>>();
  for (const r of rows) {
    const set = sessions.get(r.exercise_id) ?? new Set<string>();
    set.add(r.session_id);
    sessions.set(r.exercise_id, set);
  }
  return new Map([...sessions].map(([id, s]) => [id, s.size]));
});

export const getPersonalRecords = cache(async (): Promise<PersonalRecordRow[]> => {
  const client = await createClient();
  const rows = await selectAll<PersonalRecordRow>(client, "v_personal_record");
  return rows.sort((a, b) => b.achieved_at - a.achieved_at);
});

export const getBodyWeight = cache(async (): Promise<BodyWeightRow[]> => {
  const client = await createClient();
  const rows = await selectAll<BodyWeightRow>(client, "v_body_weight");
  return rows.sort((a, b) => a.epoch_day - b.epoch_day);
});

/** Scheduled workouts, by date. */
export const getScheduled = cache(async (): Promise<ScheduledRow[]> => {
  const client = await createClient();
  const rows = await selectAll<ScheduledRow>(client, "v_scheduled_session");
  return rows.sort((a, b) => a.epoch_day - b.epoch_day);
});

/**
 * Every plan-related sync id the user has ever had, deleted ones included, so the editor
 * can't reuse an id that belongs to another plan or a deleted row.
 */
export async function getPlanSyncIds(): Promise<Set<string>> {
  const client = await createClient();
  const rows = await selectAll<{ sync_id: string }>(client, "sync_rows", {
    columns: "sync_id",
    filter: (q) => q.in("table_name", ["plan", "plan_day", "plan_exercise"]),
  });
  return new Set(rows.map((r) => r.sync_id));
}

export interface PlanTree extends PlanRow {
  days: (PlanDayRow & { exercises: PlanExerciseRow[] })[];
}

/** All plans with their days and exercises, active plan first. */
export const getPlans = cache(async (): Promise<PlanTree[]> => {
  const client = await createClient();
  const [plans, days, exercises] = await Promise.all([
    selectAll<PlanRow>(client, "v_plan"),
    selectAll<PlanDayRow>(client, "v_plan_day"),
    selectAll<PlanExerciseRow>(client, "v_plan_exercise"),
  ]);
  const exercisesByDay = Map.groupBy(exercises, (e) => e.plan_day_id);
  const daysByPlan = Map.groupBy(days, (d) => d.plan_id);
  return plans
    .map((p) => ({
      ...p,
      days: (daysByPlan.get(p.sync_id) ?? [])
        .sort((a, b) => a.position - b.position)
        .map((d) => ({ ...d, exercises: (exercisesByDay.get(d.sync_id) ?? []).sort((a, b) => a.position - b.position) })),
    }))
    .sort((a, b) => Number(b.is_active) - Number(a.is_active) || b.created_at - a.created_at);
});
