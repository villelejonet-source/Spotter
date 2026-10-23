import Link from "next/link";
import { notFound } from "next/navigation";
import { activatePlan, duplicatePlan } from "@/app/actions/plans";
import { PlanEditor } from "@/components/plan-editor";
import { Badge, Button, PageHeader } from "@/components/ui";
import { getPlans } from "@/lib/data";
import { planVersions, type PlanDraft } from "@/lib/sync-writer";
import { exerciseOptions } from "../options";
import { DeletePlan } from "./delete-plan";

export default async function PlanPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [plans, { exercises, unit }] = await Promise.all([getPlans(), exerciseOptions()]);
  const plan = plans.find((p) => p.sync_id === id);
  if (!plan) notFound();

  const draft: PlanDraft = {
    sync_id: plan.sync_id,
    name: plan.name,
    split_type: plan.split_type,
    goal: plan.goal,
    days: plan.days.map((d) => ({
      sync_id: d.sync_id,
      name: d.name,
      exercises: d.exercises.map((e) => ({
        sync_id: e.sync_id,
        exercise_id: e.exercise_id,
        sets: e.sets,
        rep_min: e.rep_min,
        rep_max: e.rep_max,
        target_rir: e.target_rir,
        rest_seconds: e.rest_seconds,
        progression_rule: e.progression_rule,
        superset_group: e.superset_group,
        starting_weight_kg: e.starting_weight_kg,
      })),
    })),
  };

  const version = Math.max(plan.updated_at, ...plan.days.flatMap((d) => [d.updated_at, ...d.exercises.map((e) => e.updated_at)]));

  return (
    <>
      <Link href="/plans" className="text-sm text-muted hover:text-foreground">← Plans</Link>
      <div className="mt-2">
        <PageHeader
          title={plan.name}
          subtitle={plan.is_active ? <Badge tone="primary">Active plan</Badge> : "Not active"}
          actions={
            <>
              {!plan.is_active && (
                <form action={activatePlan.bind(null, plan.sync_id)}>
                  <Button variant="primary">Make active</Button>
                </form>
              )}
              <form action={duplicatePlan.bind(null, plan.sync_id)}>
                <Button>Duplicate</Button>
              </form>
              <DeletePlan planId={plan.sync_id} isActive={plan.is_active} />
            </>
          }
        />
      </div>
      {/* Keyed by the newest change in the plan, so edits synced from the phone reset the editor. */}
      <PlanEditor key={version} initial={draft} base={planVersions(plan)} exercises={exercises} unit={unit} isNew={false} />
    </>
  );
}
