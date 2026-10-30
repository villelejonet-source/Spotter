import Link from "next/link";
import { Badge, Card, EmptyState, LinkButton, PageHeader } from "@/components/ui";
import { getPlans } from "@/lib/data";
import { formatFullDate } from "@/lib/dates";
import { GOAL_LABELS, label, SPLIT_LABELS } from "@/lib/labels";

export const metadata = { title: "Plans" };

export default async function PlansPage() {
  const plans = await getPlans();
  return (
    <>
      <PageHeader
        title="Plans"
        subtitle="Changes sync to the app next time it opens."
        actions={
          <LinkButton href="/plans/new" variant="primary">
            New plan
          </LinkButton>
        }
      />
      {plans.length ? (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {plans.map((p) => (
            <li key={p.sync_id}>
              <Link href={`/plans/${p.sync_id}`} className="group block h-full rounded-2xl">
                <Card className="h-full transition group-hover:border-primary/50 group-hover:shadow-[0_0_28px_-10px_var(--glow)] group-focus-visible:border-primary">
                  <div className="flex items-start justify-between gap-2">
                    <div className="font-semibold">{p.name}</div>
                    {p.is_active && <Badge tone="primary">Active</Badge>}
                  </div>
                  <div className="mt-1 text-xs text-muted">
                    {label(SPLIT_LABELS, p.split_type)} · {label(GOAL_LABELS, p.goal)} · created {formatFullDate(p.created_at)}
                  </div>
                  <ul className="mt-4 space-y-1 text-sm">
                    {p.days.map((d) => (
                      <li key={d.sync_id} className="flex justify-between">
                        <span>{d.name}</span>
                        <span className="text-muted">{d.exercises.length} exercises</span>
                      </li>
                    ))}
                  </ul>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState
          title="No plan yet"
          action={
            <LinkButton href="/plans/new" variant="primary">
              New plan
            </LinkButton>
          }
        >
          Create one here, or answer a few questions in the app and Spotter builds a plan around your goal, schedule and equipment.
        </EmptyState>
      )}
    </>
  );
}
