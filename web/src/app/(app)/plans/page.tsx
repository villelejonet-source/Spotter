import Link from "next/link";
import { Badge, Card, EmptyState, LinkButton, PageHeader } from "@/components/ui";
import { getPlans } from "@/lib/data";
import { formatFullDate } from "@/lib/dates";
import { GOAL_LABELS, SPLIT_LABELS } from "@/lib/model";

export default async function PlansPage() {
  const plans = await getPlans();
  return (
    <>
      <PageHeader
        title="Plans"
        subtitle="Changes sync to the app next time it opens."
        actions={<LinkButton href="/plans/new" variant="primary">New plan</LinkButton>}
      />
      {plans.length ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {plans.map((p) => (
            <Link key={p.sync_id} href={`/plans/${p.sync_id}`} className="group">
              <Card className="h-full transition group-hover:border-primary">
                <div className="flex items-start justify-between gap-2">
                  <div className="font-semibold">{p.name}</div>
                  {p.is_active && <Badge tone="primary">Active</Badge>}
                </div>
                <div className="mt-1 text-xs text-muted">
                  {SPLIT_LABELS[p.split_type] ?? p.split_type} · {GOAL_LABELS[p.goal] ?? p.goal} · created {formatFullDate(p.created_at)}
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
          ))}
        </div>
      ) : (
        <EmptyState title="No plans yet">Create one here, or let the app generate one for you during onboarding.</EmptyState>
      )}
    </>
  );
}
