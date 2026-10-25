// Reads from the Supabase views, written against a minimal client interface so the
// filters each page sends can be unit-tested with a mock (queries.test.ts). data.ts wraps
// these with the real client and React's per-request cache.
//
// Every query filters on the signed-in user's id, even though RLS already limits the rows:
// once a trainer may read a linked client's rows, RLS alone would mix the client's data into
// the trainer's own pages. The filter is applied by ownedBy(), and every query takes the
// OwnClient it returns, so a query can't be run with an unfiltered client.
import type { CustomExerciseRow } from "./catalog";
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
  SessionTimeRow,
  SetEntryRow,
} from "./model";

// ---- The slice of PostgREST's query builder that the web uses ----

interface QueryError {
  message: string;
}

interface QueryBuilder<T = Record<string, unknown>> extends PromiseLike<{ data: T[] | null; error: QueryError | null; count?: number | null }> {
  eq(column: string, value: unknown): QueryBuilder<T>;
  gt(column: string, value: unknown): QueryBuilder<T>;
  gte(column: string, value: unknown): QueryBuilder<T>;
  lt(column: string, value: unknown): QueryBuilder<T>;
  lte(column: string, value: unknown): QueryBuilder<T>;
  in(column: string, values: readonly unknown[]): QueryBuilder<T>;
  is(column: string, value: null | boolean): QueryBuilder<T>;
  not(column: string, operator: string, value: unknown): QueryBuilder<T>;
  or(filters: string): QueryBuilder<T>;
  order(column: string, options?: { ascending?: boolean }): QueryBuilder<T>;
  limit(count: number): QueryBuilder<T>;
  maybeSingle(): PromiseLike<{ data: T | null; error: QueryError | null }>;
}

export interface QueryClient {
  from(view: string): { select(columns?: string, options?: { count?: "exact"; head?: boolean }): QueryBuilder };
}

/** A client whose every select starts with user_id = [owner]. Only ownedBy() makes one. */
export interface OwnClient extends QueryClient {
  readonly owner: string;
}

export function ownedBy(client: QueryClient, userId: string): OwnClient {
  if (!userId) throw new Error("ownedBy: no user id");
  return {
    owner: userId,
    from(view) {
      const table = client.from(view);
      return { select: (...args) => table.select(...args).eq("user_id", userId) };
    },
  };
}

type Filter = (q: QueryBuilder) => QueryBuilder;

const PAGE = 1000;

function fail(view: string, error: QueryError): never {
  throw new Error(`${view}: ${error.message}`);
}

/**
 * Every matching row of a view, paging past PostgREST's row limit by key (each page
 * continues after the last key, so deep pages stay as cheap as the first). When [key] can
 * repeat (started_at), pass a unique [tiebreak] column: it joins the order and the cursor,
 * so rows that tie across a page boundary aren't skipped. Both must be in [columns].
 */
export async function selectAll<T>(
  client: OwnClient,
  view: string,
  { filter, key = "sync_id", tiebreak, columns = "*" }: { filter?: Filter; key?: string; tiebreak?: string; columns?: string } = {},
): Promise<T[]> {
  const rows: T[] = [];
  let after: Record<string, unknown> | null = null;
  for (;;) {
    let q = client.from(view).select(columns);
    if (filter) q = filter(q);
    if (after != null) {
      q = tiebreak ? q.or(`${key}.gt.${after[key]},and(${key}.eq.${after[key]},${tiebreak}.gt.${after[tiebreak]})`) : q.gt(key, after[key]);
    }
    q = q.order(key);
    if (tiebreak) q = q.order(tiebreak);
    const { data, error } = await q.limit(PAGE);
    if (error) fail(view, error);
    const page = (data ?? []) as (T & Record<string, unknown>)[];
    rows.push(...page);
    if (page.length < PAGE) return rows;
    after = page[page.length - 1];
  }
}

/** One page of a view, ordered on the server. */
async function selectPage<T>(
  client: OwnClient,
  view: string,
  { filter, order, descending = false, limit, columns = "*" }: { filter?: Filter; order: string; descending?: boolean; limit: number; columns?: string },
): Promise<T[]> {
  let q = client.from(view).select(columns);
  if (filter) q = filter(q);
  const { data, error } = await q.order(order, { ascending: !descending }).limit(limit);
  if (error) fail(view, error);
  return (data ?? []) as T[];
}

/** How many rows match, without fetching them. */
async function count(client: OwnClient, view: string, filter?: Filter): Promise<number> {
  let q = client.from(view).select("sync_id", { count: "exact", head: true });
  if (filter) q = filter(q);
  const { count: n, error } = await q;
  if (error) fail(view, error);
  return n ?? 0;
}

async function single<T>(client: OwnClient, view: string, column: string, value: unknown): Promise<T | null> {
  const { data, error } = await client.from(view).select("*").eq(column, value).maybeSingle();
  if (error) fail(view, error);
  return data as T | null;
}

// ---- Profile and catalog ----

export async function profile(client: OwnClient): Promise<ProfileRow | null> {
  const { data, error } = await client.from("v_profile").select("*").maybeSingle();
  if (error) fail("v_profile", error);
  return data as ProfileRow | null;
}

/** The user's own exercises; data.ts merges them into the seeded catalog. */
export function customExercises(client: OwnClient): Promise<CustomExerciseRow[]> {
  return selectAll<CustomExerciseRow>(client, "v_custom_exercise", { key: "id" });
}

// ---- Workouts ----

export interface SessionsQuery {
  /** Started at or after this epoch ms. */
  since?: number;
  /** Started before this epoch ms. */
  until?: number;
  /** At most this many, newest first; without it, every match. */
  limit?: number;
}

const sessionFilter =
  ({ since, until }: SessionsQuery): Filter =>
  (q) => {
    let f = q;
    if (since != null) f = f.gte("started_at", since);
    if (until != null) f = f.lt("started_at", until);
    return f;
  };

/** Sessions newest first. Pass [limit] for a list, [since]/[until] for a window. */
export async function sessions(client: OwnClient, query: SessionsQuery = {}): Promise<SessionSummaryRow[]> {
  if (query.limit != null) {
    return selectPage<SessionSummaryRow>(client, "v_session_summary", { filter: sessionFilter(query), order: "started_at", descending: true, limit: query.limit });
  }
  const rows = await selectAll<SessionSummaryRow>(client, "v_session_summary", { filter: sessionFilter(query), key: "started_at", tiebreak: "sync_id" });
  return rows.reverse();
}

/** When each session happened (three columns), oldest first: for streaks, heatmaps and counts. */
export async function sessionTimes(client: OwnClient, since?: number): Promise<SessionTimeRow[]> {
  return selectAll<SessionTimeRow>(client, "v_session_summary", { filter: sessionFilter({ since }), key: "started_at", tiebreak: "sync_id", columns: "sync_id,started_at,ended_at" });
}

/** Finished workouts, all time. */
export function finishedSessionCount(client: OwnClient): Promise<number> {
  return count(client, "v_session_summary", (q) => q.not("ended_at", "is", null));
}

export interface SessionDetail {
  session: SessionSummaryRow;
  exercises: (SessionExerciseRow & { sets: SetEntryRow[] })[];
}

export async function sessionDetail(client: OwnClient, syncId: string): Promise<SessionDetail | null> {
  const [session, exercises, sets] = await Promise.all([
    single<SessionSummaryRow>(client, "v_session_summary", "sync_id", syncId),
    selectAll<SessionExerciseRow>(client, "v_session_exercise", { filter: (q) => q.eq("session_id", syncId) }),
    selectAll<SetEntryRow>(client, "v_set_history", { filter: (q) => q.eq("session_id", syncId) }),
  ]);
  if (!session) return null;
  const setsByExercise = new Map<string, SetEntryRow[]>();
  for (const s of sets) {
    const list = setsByExercise.get(s.session_exercise_id) ?? [];
    list.push(s);
    setsByExercise.set(s.session_exercise_id, list);
  }
  return {
    session,
    exercises: exercises
      .sort((a, b) => a.position - b.position)
      .map((e) => ({ ...e, sets: (setsByExercise.get(e.sync_id) ?? []).sort((a, b) => a.position - b.position) })),
  };
}

/** A logged set with its exercise and workout (v_set_history). */
export interface SetWithExercise extends SetEntryRow {
  exercise_id: string;
  session_id: string;
  session_started_at: number;
  session_finished: boolean;
}

/** The columns the progress pages need from v_set_history. */
export type ProgressSetRow = Pick<SetWithExercise, "session_id" | "exercise_id" | "session_started_at" | "weight_kg" | "reps" | "set_type" | "completed_at">;
export const PROGRESS_SET_COLUMNS = "sync_id,session_id,exercise_id,session_started_at,weight_kg,reps,set_type,completed_at";

interface SetHistoryQuery {
  exerciseId?: string;
  /** Workouts started at or after this epoch ms. */
  since?: number;
  /** Include sets from a workout that's still running (the export does). */
  includeRunning?: boolean;
  columns?: string;
}

/** Logged sets from finished workouts, filtered on the server. With no filter it's every set, which only the export needs. */
export async function setHistory<T = SetWithExercise>(client: OwnClient, { exerciseId, since, includeRunning = false, columns = "*" }: SetHistoryQuery = {}): Promise<T[]> {
  return selectAll<T>(client, "v_set_history", {
    columns,
    filter: (q) => {
      let f = includeRunning ? q : q.eq("session_finished", true);
      if (exerciseId) f = f.eq("exercise_id", exerciseId);
      if (since != null) f = f.gte("session_started_at", since);
      return f;
    },
  });
}

/** Distinct workouts each exercise was done in (v_exercise_usage, grouped on the server). */
export async function exerciseUsage(client: OwnClient): Promise<Map<string, number>> {
  const rows = await selectAll<{ exercise_id: string; session_count: number }>(client, "v_exercise_usage", { key: "exercise_id", columns: "exercise_id,session_count" });
  return new Map(rows.map((r) => [r.exercise_id, Number(r.session_count)]));
}

// ---- Records ----

export interface RecordsQuery {
  exerciseId?: string;
  /** Achieved at or after this epoch ms. */
  since?: number;
  /** At most this many, newest first; without it, every match. */
  limit?: number;
}

const recordFilter =
  ({ exerciseId, since }: RecordsQuery): Filter =>
  (q) => {
    let f = q;
    if (exerciseId) f = f.eq("exercise_id", exerciseId);
    if (since != null) f = f.gte("achieved_at", since);
    return f;
  };

/** Personal records, newest first. */
export async function personalRecords(client: OwnClient, query: RecordsQuery = {}): Promise<PersonalRecordRow[]> {
  if (query.limit != null) {
    return selectPage<PersonalRecordRow>(client, "v_personal_record", { filter: recordFilter(query), order: "achieved_at", descending: true, limit: query.limit });
  }
  // Ordered by sync id on the server (achieved_at can repeat), then by date here.
  const rows = await selectAll<PersonalRecordRow>(client, "v_personal_record", { filter: recordFilter(query) });
  return rows.sort((a, b) => b.achieved_at - a.achieved_at);
}

export function recordCount(client: OwnClient): Promise<number> {
  return count(client, "v_personal_record");
}

// ---- Body weight and schedule ----

/** Body weight entries, oldest first (one per day). */
export function bodyWeight(client: OwnClient): Promise<BodyWeightRow[]> {
  return selectAll<BodyWeightRow>(client, "v_body_weight", { key: "epoch_day" });
}

export interface ScheduledQuery {
  /** Epoch day, inclusive. */
  fromDay?: number;
  /** Epoch day, inclusive. */
  toDay?: number;
  /** Only workouts not yet started in the app. */
  open?: boolean;
  /** At most this many, soonest first; without it, every match. */
  limit?: number;
}

/** Scheduled workouts by date, soonest first. */
export async function scheduled(client: OwnClient, { fromDay, toDay, open, limit }: ScheduledQuery = {}): Promise<ScheduledRow[]> {
  const filter: Filter = (q) => {
    let f = q;
    if (fromDay != null) f = f.gte("epoch_day", fromDay);
    if (toDay != null) f = f.lte("epoch_day", toDay);
    if (open) f = f.is("session_id", null);
    return f;
  };
  if (limit != null) return selectPage<ScheduledRow>(client, "v_scheduled_session", { filter, order: "epoch_day", limit });
  const rows = await selectAll<ScheduledRow>(client, "v_scheduled_session", { filter });
  return rows.sort((a, b) => a.epoch_day - b.epoch_day);
}

// ---- Plans ----

export interface PlanTree extends PlanRow {
  days: (PlanDayRow & { exercises: PlanExerciseRow[] })[];
}

/**
 * Every plan-related sync id the user has ever had, deleted ones included, so the editor
 * can't reuse an id that belongs to another plan or a deleted row.
 */
export async function planSyncIds(client: OwnClient): Promise<Set<string>> {
  const rows = await selectAll<{ sync_id: string }>(client, "sync_rows", {
    columns: "sync_id",
    filter: (q) => q.in("table_name", ["plan", "plan_day", "plan_exercise"]),
  });
  return new Set(rows.map((r) => r.sync_id));
}

function assemble(plans: PlanRow[], days: PlanDayRow[], exercises: PlanExerciseRow[]): PlanTree[] {
  const exercisesByDay = new Map<string, PlanExerciseRow[]>();
  for (const e of exercises) exercisesByDay.set(e.plan_day_id, [...(exercisesByDay.get(e.plan_day_id) ?? []), e]);
  const daysByPlan = new Map<string, PlanDayRow[]>();
  for (const d of days) daysByPlan.set(d.plan_id, [...(daysByPlan.get(d.plan_id) ?? []), d]);
  return plans
    .map((p) => ({
      ...p,
      days: (daysByPlan.get(p.sync_id) ?? [])
        .sort((a, b) => a.position - b.position)
        .map((d) => ({ ...d, exercises: (exercisesByDay.get(d.sync_id) ?? []).sort((a, b) => a.position - b.position) })),
    }))
    .sort((a, b) => Number(b.is_active) - Number(a.is_active) || b.created_at - a.created_at);
}

/** All plans with their days and exercises, active plan first. */
export async function plans(client: OwnClient): Promise<PlanTree[]> {
  const [p, d, e] = await Promise.all([
    selectAll<PlanRow>(client, "v_plan"),
    selectAll<PlanDayRow>(client, "v_plan_day"),
    selectAll<PlanExerciseRow>(client, "v_plan_exercise"),
  ]);
  return assemble(p, d, e);
}

/** The days and exercises of the given plans only. */
async function trees(client: OwnClient, rows: PlanRow[]): Promise<PlanTree[]> {
  if (!rows.length) return [];
  const days = await selectAll<PlanDayRow>(client, "v_plan_day", { filter: (q) => q.in("plan_id", rows.map((p) => p.sync_id)) });
  const exercises = days.length
    ? await selectAll<PlanExerciseRow>(client, "v_plan_exercise", { filter: (q) => q.in("plan_day_id", days.map((d) => d.sync_id)) })
    : [];
  return assemble(rows, days, exercises);
}

export interface PlanOutline {
  sync_id: string;
  name: string;
  is_active: boolean;
  days: Pick<PlanDayRow, "sync_id" | "position" | "name">[];
}

/** Every plan's name and its days' names and order, without exercises (the calendar's labels and planner). Active first. */
export async function planOutlines(client: OwnClient): Promise<PlanOutline[]> {
  const [p, d] = await Promise.all([
    selectAll<Pick<PlanRow, "sync_id" | "name" | "is_active" | "created_at">>(client, "v_plan", { columns: "sync_id,name,is_active,created_at" }),
    selectAll<Pick<PlanDayRow, "sync_id" | "plan_id" | "position" | "name">>(client, "v_plan_day", { columns: "sync_id,plan_id,position,name" }),
  ]);
  const daysByPlan = new Map<string, PlanOutline["days"]>();
  for (const day of d) daysByPlan.set(day.plan_id, [...(daysByPlan.get(day.plan_id) ?? []), { sync_id: day.sync_id, position: day.position, name: day.name }]);
  return p
    .sort((a, b) => Number(b.is_active) - Number(a.is_active) || b.created_at - a.created_at)
    .map((plan) => ({ sync_id: plan.sync_id, name: plan.name, is_active: plan.is_active, days: (daysByPlan.get(plan.sync_id) ?? []).sort((a, b) => a.position - b.position) }));
}

/** One plan with its days and exercises, or null. */
export async function plan(client: OwnClient, syncId: string): Promise<PlanTree | null> {
  const row = await single<PlanRow>(client, "v_plan", "sync_id", syncId);
  return row ? (await trees(client, [row]))[0] : null;
}

/** The active plan with its days and exercises, or null. */
export async function activePlan(client: OwnClient): Promise<PlanTree | null> {
  const rows = await selectPage<PlanRow>(client, "v_plan", { filter: (q) => q.eq("is_active", true), order: "created_at", descending: true, limit: 1 });
  return rows.length ? (await trees(client, rows))[0] : null;
}

/** One plan day with its exercises (for a scheduled workout from any plan), or null. */
export async function planDay(client: OwnClient, syncId: string): Promise<(PlanDayRow & { exercises: PlanExerciseRow[] }) | null> {
  const day = await single<PlanDayRow>(client, "v_plan_day", "sync_id", syncId);
  if (!day) return null;
  const exercises = await selectAll<PlanExerciseRow>(client, "v_plan_exercise", { filter: (q) => q.eq("plan_day_id", syncId) });
  return { ...day, exercises: exercises.sort((a, b) => a.position - b.position) };
}
