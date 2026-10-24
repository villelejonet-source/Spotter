"use server";

import { redirect } from "next/navigation";
import { z } from "zod";
import { getCatalog, getPlans, getPlanSyncIds } from "@/lib/data";
import { push, saveErrorMessage } from "@/lib/push";
import { requireUser } from "@/lib/supabase/server";
import {
  activatePlanRows,
  copyPlan,
  deletePlanRows,
  diffPlan,
  planConflict,
  planDraftSchema,
  syncIdSchema,
  type PlanDraft,
  type PlanVersions,
} from "@/lib/sync-writer";

export type ActionResult = { ok: true } | { ok: false; error: string };

/** The editor sent something that isn't a plan: a bug or a tampered request, not a user mistake. */
const RELOAD = "Reload the page and try again.";

/**
 * The versions the editor loaded: sync id → updated_at. A plan has at most 1 + 7 + 7 × 30
 * rows, so the cap is generous; it only stops a request from carrying a huge object.
 */
const versionsSchema = z
  .record(syncIdSchema, z.number().int().nonnegative())
  .refine((v) => Object.keys(v).length <= 500, RELOAD);

/**
 * Creates or updates a plan from the editor. [base] is the version of every row the editor
 * loaded, so changes made on the phone in the meantime are never overwritten. A user's
 * first plan becomes the active one.
 */
export async function savePlan(input: PlanDraft, base: PlanVersions): Promise<ActionResult> {
  await requireUser();
  const parsed = planDraftSchema.safeParse(input, { error: () => RELOAD });
  const parsedBase = versionsSchema.safeParse(base);
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? RELOAD };
  if (!parsedBase.success) return { ok: false, error: RELOAD };
  const draft = parsed.data;

  const [catalog, plans, knownIds] = await Promise.all([getCatalog(), getPlans(), getPlanSyncIds()]);
  // The phone's plan_exercise.exerciseId is a foreign key: an id it doesn't know would
  // fail its sync, so only catalog and the user's custom exercises are allowed.
  const unknown = draft.days.flatMap((d) => d.exercises).find((e) => !catalog.has(e.exercise_id));
  if (unknown) return { ok: false, error: "One of the exercises no longer exists. Remove it and try again." };

  const before = plans.find((p) => p.sync_id === draft.sync_id) ?? null;
  const conflict = planConflict(before, draft, parsedBase.data, knownIds);
  if (conflict) return { ok: false, error: conflict };

  const isActive = before ? before.is_active : !plans.some((p) => p.is_active);
  try {
    await push(diffPlan(before, draft, Date.now(), isActive));
  } catch (e) {
    return { ok: false, error: saveErrorMessage(e) };
  }
  return { ok: true };
}

// The three below run as form actions, so they have no result to show: a bad id is ignored
// and a failed push surfaces through the route's error boundary with push's own message.

export async function activatePlan(planId: string) {
  await requireUser();
  if (!syncIdSchema.safeParse(planId).success) return;
  const plans = await getPlans();
  if (!plans.some((p) => p.sync_id === planId)) return;
  await push(activatePlanRows(plans, planId, Date.now()));
}

export async function deletePlan(planId: string) {
  await requireUser();
  if (!syncIdSchema.safeParse(planId).success) redirect("/plans");
  const plan = (await getPlans()).find((p) => p.sync_id === planId);
  if (plan) await push(deletePlanRows(plan, Date.now()));
  redirect("/plans");
}

export async function duplicatePlan(planId: string) {
  await requireUser();
  if (!syncIdSchema.safeParse(planId).success) redirect("/plans");
  const plan = (await getPlans()).find((p) => p.sync_id === planId);
  if (!plan) redirect("/plans");
  const copy = copyPlan(plan, `${plan.name} (copy)`.slice(0, 80));
  await push(diffPlan(null, copy, Date.now(), false));
  redirect(`/plans/${copy.sync_id}`);
}
