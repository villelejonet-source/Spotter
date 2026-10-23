import Link from "next/link";
import { Badge, Card, EmptyState, PageHeader } from "@/components/ui";
import { getSessions, getUnit } from "@/lib/data";
import { formatFullDate, formatMonthYear, formatTime } from "@/lib/dates";
import { formatDuration, formatVolume } from "@/lib/model";

export default async function HistoryPage() {
  const [sessions, unit] = await Promise.all([getSessions(), getUnit()]);

  if (!sessions.length) {
    return (
      <>
        <PageHeader title="History" />
        <EmptyState title="No workouts yet">Workouts you log in the app appear here after the next sync.</EmptyState>
      </>
    );
  }

  const months = Map.groupBy(sessions, (s) => formatMonthYear(s.started_at));

  return (
    <>
      <PageHeader title="History" subtitle={`${sessions.length} workouts`} />
      <div className="space-y-6">
        {[...months].map(([month, list]) => (
          <section key={month}>
            <h2 className="mb-2 text-sm font-medium text-muted">{month}</h2>
            <Card className="p-2">
              <ul className="divide-y divide-outline">
                {list.map((s) => (
                  <li key={s.sync_id}>
                    <Link href={`/history/${s.sync_id}`} className="flex items-center justify-between gap-3 rounded-xl px-3 py-3 hover:bg-surface-high">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 font-medium">
                          {s.plan_day_name ?? "Free workout"}
                          {s.is_deload && <Badge tone="tertiary">Deload</Badge>}
                          {s.ended_at == null && <Badge tone="primary">In progress</Badge>}
                        </div>
                        <div className="text-xs text-muted">
                          {formatFullDate(s.started_at)} · {formatTime(s.started_at)}
                          {s.ended_at != null && ` · ${formatDuration(s.ended_at - s.started_at)}`}
                        </div>
                      </div>
                      <div className="shrink-0 text-right text-sm tabular-nums">
                        <div>{formatVolume(s.volume_kg, unit)}</div>
                        <div className="text-xs text-muted">
                          {s.exercise_count} exercises · {s.completed_sets} sets
                        </div>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          </section>
        ))}
      </div>
    </>
  );
}
