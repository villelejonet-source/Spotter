import Link from "next/link";
import { Badge, Card, EmptyState, LinkButton, PageHeader, SectionTitle } from "@/components/ui";
import { groupBy } from "@/lib/collections";
import { getFinishedSessionCount, getSessions, getUnit } from "@/lib/data";
import { formatFullDate, formatMonthYear, formatTime } from "@/lib/dates";
import { LIGHTER_WEEK, WORKOUT } from "@/lib/labels";
import { formatDuration, formatVolume } from "@/lib/model";

export const metadata = { title: "History" };

const PAGE = 50;

/** Workouts newest first, by month, 50 at a time; `?before=<started_at>` continues past them. */
export default async function HistoryPage({ searchParams }: { searchParams: Promise<{ before?: string }> }) {
  const { before } = await searchParams;
  const until = before && /^\d+$/.test(before) ? Number(before) : undefined;
  const [page, total, unit] = await Promise.all([getSessions({ until, limit: PAGE + 1 }), getFinishedSessionCount(), getUnit()]);
  const sessions = page.slice(0, PAGE);
  const older = page.length > PAGE ? sessions[sessions.length - 1].started_at : null;

  if (!sessions.length) {
    return (
      <>
        <PageHeader title="History" />
        <EmptyState title="No workouts yet" action={until != null && <LinkButton href="/history">Back to the latest</LinkButton>}>
          Finished workouts show up here, with every set, by date and by exercise.
        </EmptyState>
      </>
    );
  }

  const months = groupBy(sessions, (s) => formatMonthYear(s.started_at));

  return (
    <>
      <PageHeader title="History" subtitle={`${total} workouts`} />
      <div className="space-y-6">
        {[...months].map(([month, list]) => (
          <section key={month} aria-labelledby={`month-${month.replace(/\s/g, "-")}`}>
            <SectionTitle>
              <span id={`month-${month.replace(/\s/g, "-")}`}>{month}</span>
            </SectionTitle>
            <Card className="p-2">
              <ul className="divide-y divide-outline">
                {list.map((s) => (
                  <li key={s.sync_id}>
                    <Link href={`/history/${s.sync_id}`} className="flex items-center justify-between gap-3 rounded-xl px-3 py-3 hover:bg-surface">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2 font-medium">
                          {s.plan_day_name ?? WORKOUT}
                          {s.is_deload && <Badge tone="tertiary">{LIGHTER_WEEK}</Badge>}
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
      {(older != null || until != null) && (
        <nav aria-label="More workouts" className="mt-6 flex flex-wrap justify-center gap-2">
          {until != null && <LinkButton href="/history">Back to the latest</LinkButton>}
          {older != null && <LinkButton href={`/history?before=${older}`}>Show older workouts</LinkButton>}
        </nav>
      )}
    </>
  );
}
