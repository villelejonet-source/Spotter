import { notFound } from "next/navigation";
import { activatePlan, deletePlan, duplicatePlan } from "@/app/actions/plans";
import { ConfirmButton } from "@/components/confirm-button";
import { PlanEditor } from "@/components/plan-editor";
import { BackLink, Badge, Button, PageHeader } from "@/components/ui";
import { getPlan } from "@/lib/data";
import { planVersions, type PlanDraft } from "@/lib/sync-writer";
import { exerciseOptions } from "../options";

export const metadata = { title: "Plan" };

export default async function PlanPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [plan, { exercises, unit }] = await Promise.all([getPlan(id), exerciseOptions()]);
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
      <BackLink href="/plans">Plans</BackLink>
      <PageHeader
        title={plan.name}
        subtitle={plan.is_active ? <Badge tone="primary">Active plan</Badge> : "Not active"}
        actions={
          <>
            {!plan.is_active && (
              <ConfirmButton
                action={activatePlan.bind(null, plan.sync_id)}
                variant="primary"
                title="Make this your active plan?"
                body="The app's Today tab switches to this plan. Your current plan stays in the list."
                confirmLabel="Make active"
              >
                Make active
              </ConfirmButton>
            )}
            <form action={duplicatePlan.bind(null, plan.sync_id)}>
              <Button type="submit">Duplicate</Button>
            </form>
            <ConfirmButton
              action={deletePlan.bind(null, plan.sync_id)}
              variant="danger"
              title={plan.is_active ? "Delete your active plan?" : "Delete this plan?"}
              body="Past workouts stay in your history. The plan is removed from the app at its next sync."
              confirmLabel="Delete plan"
            >
              Delete
            </ConfirmButton>
          </>
        }
      />
      {/* Keyed by the newest change in the plan, so edits synced from the phone reset the editor. */}
      <PlanEditor key={version} initial={draft} base={planVersions(plan)} exercises={exercises} unit={unit} isNew={false} />
    </>
  );
}
