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
  SessionExerciseRow,
  SessionSummaryRow,
  SetEntryRow,
  WeightUnit,
} from "./model";
import { createClient } from "./supabase/server";

type Client = Awaited<ReturnType<typeof createClient>>;

const PAGE = 1000;

/** Every row of a view, paging past PostgREST's row limit. RLS limits it to the user. */
async function selectAll<T>(
  client: Client,
  view: string,
  build?: (q: ReturnType<ReturnType<Client["from"]>["select"]>) => typeof q,
  key = "sync_id",
): Promise<T[]> {
  const rows: T[] = [];
  for (let from = 0; ; from += PAGE) {
    let q = client.from(view).select("*");
    if (build) q = build(q);
    const { data, error } = await q.order(key).range(from, from + PAGE - 1);
    if (error) throw new Error(`${view}: ${error.message}`);
    rows.push(...(data as T[]));
    if (data.length < PAGE) return rows;
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
  return buildCatalog(await selectAll<CustomExerciseRow>(client, "v_custom_exercise", undefined, "id"));
});

/** All sessions, newest first. */
export const getSessions = cache(async (): Promise<SessionSummaryRow[]> => {
  const client = await createClient();
  const rows = await selectAll<SessionSummaryRow>(client, "v_session_summary");
  return rows.sort((a, b) => b.started_at - a.started_at);
});

export async function getSessionDetail(syncId: string) {
  const client = await createClient();
  const { data: session, error } = await client.from("v_session_summary").select("*").eq("sync_id", syncId).maybeSingle();
  if (error) throw new Error(error.message);
  if (!session) return null;
  const exercises = await selectAll<SessionExerciseRow>(client, "v_session_exercise", (q) => q.eq("session_id", syncId));
  const sets = exercises.length
    ? await selectAll<SetEntryRow>(client, "v_set_entry", (q) => q.in("session_exercise_id", exercises.map((e) => e.sync_id)))
    : [];
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

/** Every logged set with its exercise and session, for progress charts. */
export const getSetHistory = cache(async () => {
  const client = await createClient();
  const [exercises, sets] = await Promise.all([
    selectAll<SessionExerciseRow>(client, "v_session_exercise"),
    selectAll<SetEntryRow>(client, "v_set_entry"),
  ]);
  const byId = new Map(exercises.map((e) => [e.sync_id, e]));
  return sets.flatMap((s) => {
    const e = byId.get(s.session_exercise_id);
    return e ? [{ ...s, exercise_id: e.exercise_id, session_id: e.session_id }] : [];
  });
});
export type SetWithExercise = Awaited<ReturnType<typeof getSetHistory>>[number];

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
  return plans
    .map((p) => ({
      ...p,
      days: days
        .filter((d) => d.plan_id === p.sync_id)
        .sort((a, b) => a.position - b.position)
        .map((d) => ({
          ...d,
          exercises: exercises.filter((e) => e.plan_day_id === d.sync_id).sort((a, b) => a.position - b.position),
        })),
    }))
    .sort((a, b) => Number(b.is_active) - Number(a.is_active) || b.created_at - a.created_at);
});
