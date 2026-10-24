// The server's view of the user's data: queries.ts run with the signed-in client, each
// memoised per request with React's cache() so sections of a page that need the same rows
// share one fetch. Every function takes only what its page shows (a window, a limit, an
// id); the no-argument forms return everything and exist for the export.
import "server-only";
import { cache } from "react";
import { buildCatalog } from "./catalog";
import type { WeightUnit } from "./model";
import * as q from "./queries";
import { createClient } from "./supabase/server";

export type { PlanTree, ProgressSetRow, SessionDetail, SetWithExercise } from "./queries";

const client = cache(async () => (await createClient()) as unknown as q.QueryClient);

export const getProfile = cache(async () => q.profile(await client()));

export async function getUnit(): Promise<WeightUnit> {
  return (await getProfile())?.units ?? "KG";
}

/** The seeded catalog plus the user's custom exercises, by id. */
export const getCatalog = cache(async () => buildCatalog(await q.customExercises(await client())));

// cache() compares arguments with Object.is, so the memoised functions take primitives and
// the exported ones unpack their option objects.

const sessionsCached = cache(async (since?: number, until?: number, limit?: number) => q.sessions(await client(), { since, until, limit }));

/** Sessions newest first; a window and/or a limit, or everything with no arguments. */
export const getSessions = (query: q.SessionsQuery = {}) => sessionsCached(query.since, query.until, query.limit);

/** When each session happened, oldest first, from [since] (epoch ms) or all time. */
export const getSessionTimes = cache(async (since?: number) => q.sessionTimes(await client(), since));

export const getFinishedSessionCount = cache(async () => q.finishedSessionCount(await client()));

export const getSessionDetail = cache(async (syncId: string) => q.sessionDetail(await client(), syncId));

/** Logged sets from finished workouts (or running ones too, for the export), filtered on the server. */
export const getSetHistory = cache(async (exerciseId?: string, since?: number, includeRunning = false) =>
  q.setHistory(await client(), { exerciseId, since, includeRunning }),
);

/** The columns the progress overview needs, for workouts started at or after [since]. */
export const getProgressSets = cache(async (since?: number) => q.setHistory<q.ProgressSetRow>(await client(), { since, columns: q.PROGRESS_SET_COLUMNS }));

export const getExerciseUsage = cache(async () => q.exerciseUsage(await client()));

const recordsCached = cache(async (exerciseId?: string, since?: number, limit?: number) => q.personalRecords(await client(), { exerciseId, since, limit }));

/** Personal records newest first; by exercise, since a date and/or capped, or everything with no arguments. */
export const getPersonalRecords = (query: q.RecordsQuery = {}) => recordsCached(query.exerciseId, query.since, query.limit);

export const getRecordCount = cache(async () => q.recordCount(await client()));

/** Body weight entries, oldest first. */
export const getBodyWeight = cache(async () => q.bodyWeight(await client()));

const scheduledCached = cache(async (fromDay?: number, toDay?: number, open?: boolean, limit?: number) => q.scheduled(await client(), { fromDay, toDay, open, limit }));

/** Scheduled workouts soonest first, within a day range and/or only the open ones. */
export const getScheduled = (query: q.ScheduledQuery = {}) => scheduledCached(query.fromDay, query.toDay, query.open, query.limit);

export const getPlanSyncIds = async () => q.planSyncIds(await client());

/** All plans with their days and exercises, active plan first. */
export const getPlans = cache(async () => q.plans(await client()));

/** Plan and day names only, no exercises. */
export const getPlanOutlines = cache(async () => q.planOutlines(await client()));

export const getPlan = cache(async (syncId: string) => q.plan(await client(), syncId));

export const getActivePlan = cache(async () => q.activePlan(await client()));

export const getPlanDay = cache(async (syncId: string) => q.planDay(await client(), syncId));
