"use server";

import { redirect } from "next/navigation";
import { getCatalog, getPlans } from "@/lib/data";
import { push } from "@/lib/push";
import { activatePlanRows, copyPlan, deletePlanRows, diffPlan, planDraftSchema, type PlanDraft } from "@/lib/sync-writer";

export type ActionResult = { ok: true } | { ok: false; error: string };

/** Creates or updates a plan from the editor. A user's first plan becomes the active one. */
export async function savePlan(input: PlanDraft): Promise<ActionResult> {
  const parsed = planDraftSchema.safeParse(input);
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? "Invalid plan" };
  const draft = parsed.data;

  // The phone's plan_exercise.exerciseId is a foreign key: an id it doesn't know would
  // fail the whole sync there, so only catalog and the user's custom exercises are allowed.
  const catalog = await getCatalog();
  const unknown = draft.days.flatMap((d) => d.exercises).find((e) => !catalog.has(e.exercise_id));
  if (unknown) return { ok: false, error: `Unknown exercise: ${unknown.exercise_id}` };

  const plans = await getPlans();
  const before = plans.find((p) => p.sync_id === draft.sync_id) ?? null;
  const isActive = before ? before.is_active : !plans.some((p) => p.is_active);
  try {
    await push(diffPlan(before, draft, Date.now(), isActive));
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
  return { ok: true };
}

export async function activatePlan(planId: string) {
  const plans = await getPlans();
  await push(activatePlanRows(plans, planId, Date.now()));
}

export async function deletePlan(planId: string) {
  const plan = (await getPlans()).find((p) => p.sync_id === planId);
  if (plan) await push(deletePlanRows(plan, Date.now()));
  redirect("/plans");
}

export async function duplicatePlan(planId: string) {
  const plan = (await getPlans()).find((p) => p.sync_id === planId);
  if (!plan) redirect("/plans");
  const copy = copyPlan(plan, `${plan.name} (copy)`.slice(0, 80));
  await push(diffPlan(null, copy, Date.now(), false));
  redirect(`/plans/${copy.sync_id}`);
}
