"use server";

import { z } from "zod";
import { getPlans, getScheduled, getSessions } from "@/lib/data";
import { push, saveErrorMessage } from "@/lib/push";
import { localDate, nextPlanDay } from "@/lib/stats";
import { requireUser } from "@/lib/supabase/server";
import {
  MAX_SCHEDULE_DAYS_AHEAD,
  newSyncId,
  planWeeksAhead,
  scheduledPayload,
  scheduleSchema,
  stamp,
  syncIdSchema,
  type ScheduleDraft,
} from "@/lib/sync-writer";
import type { ActionResult } from "./plans";

const DAY_MS = 86_400_000;
const RELOAD = "Reload the page and try again.";

/** Today as an epoch day, in the display time zone. */
function today(): number {
  return localDate(Date.now()) / DAY_MS;
}

/** Plans a workout on a date: a plan day (any plan), or a free workout when plan_day_id is null. */
export async function scheduleWorkout(input: ScheduleDraft): Promise<ActionResult> {
  await requireUser();
  const parsed = scheduleSchema.safeParse(input);
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? "Check the workout and try again." };
  const draft = parsed.data;
  if (draft.epoch_day < today() || draft.epoch_day > today() + MAX_SCHEDULE_DAYS_AHEAD) {
    return { ok: false, error: `Pick a date from today up to ${MAX_SCHEDULE_DAYS_AHEAD} days ahead.` };
  }
  // The phone links planDayId by sync id; an unknown or deleted day would arrive as a free workout.
  if (draft.plan_day_id && !(await getPlans()).some((p) => p.days.some((d) => d.sync_id === draft.plan_day_id))) {
    return { ok: false, error: "That plan day no longer exists. Reload and try again." };
  }
  try {
    await push([{ table_name: "scheduled_session", sync_id: newSyncId(), payload: scheduledPayload(draft), updated_at: Date.now(), deleted_at: null }]);
  } catch (e) {
    return { ok: false, error: saveErrorMessage(e) };
  }
  return { ok: true };
}

export async function removeScheduled(syncId: string): Promise<ActionResult> {
  await requireUser();
  if (!syncIdSchema.safeParse(syncId).success) return { ok: false, error: RELOAD };
  const entry = (await getScheduled()).find((s) => s.sync_id === syncId);
  if (!entry) return { ok: true };
  const at = stamp(Date.now(), entry.updated_at);
  try {
    await push([{ table_name: "scheduled_session", sync_id: syncId, payload: null, updated_at: at, deleted_at: at }]);
  } catch (e) {
    return { ok: false, error: saveErrorMessage(e) };
  }
  return { ok: true };
}

const planWeeksSchema = z.object({
  weekdays: z.array(z.number().int().min(0).max(6)).min(1).max(7),
  weeks: z.number().int().min(1).max(4),
});

/**
 * Fills the chosen weekdays for the next [weeks] weeks with the active plan's days in order,
 * picking up the rotation where the user is. Days that already have a workout are skipped.
 */
export async function planWeeks(input: z.infer<typeof planWeeksSchema>): Promise<ActionResult & { added?: number }> {
  await requireUser();
  const parsed = planWeeksSchema.safeParse(input);
  if (!parsed.success) return { ok: false, error: "Pick at least one weekday." };
  const [plans, sessions, scheduled] = await Promise.all([getPlans(), getSessions(), getScheduled()]);
  const active = plans.find((p) => p.is_active);
  if (!active?.days.length) return { ok: false, error: "You need an active plan with days first." };

  const next = nextPlanDay(active.days, sessions);
  const rows = planWeeksAhead({
    fromDay: today(),
    weeks: parsed.data.weeks,
    weekdays: parsed.data.weekdays,
    dayIds: active.days.map((d) => d.sync_id),
    nextDayIndex: Math.max(0, active.days.findIndex((d) => d.sync_id === next?.sync_id)),
    taken: new Set(scheduled.map((s) => s.epoch_day)),
  });
  if (!rows.length) return { ok: true, added: 0 };
  const now = Date.now();
  try {
    await push(
      rows.map((r) => ({
        table_name: "scheduled_session" as const,
        sync_id: newSyncId(),
        payload: scheduledPayload({ ...r, notes: null }),
        updated_at: now,
        deleted_at: null,
      })),
    );
  } catch (e) {
    return { ok: false, error: saveErrorMessage(e) };
  }
  return { ok: true, added: rows.length };
}
