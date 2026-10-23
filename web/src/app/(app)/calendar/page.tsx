import clsx from "clsx";
import Link from "next/link";
import { Card, CardTitle, PageHeader } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getPlans, getSessions, getUnit } from "@/lib/data";
import { formatDateValueMonth, formatTime } from "@/lib/dates";
import { formatVolume } from "@/lib/model";
import { dayKey, localDate, nextPlanDay, weekStart } from "@/lib/stats";

const DAY_MS = 86_400_000;
const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
// Plan days get a stable colour by position, so "Upper" always looks the same.
const DAY_COLOURS = [
  "bg-primary text-on-primary",
  "bg-tertiary text-background",
  "bg-on-primary-container text-primary-container",
  "bg-muted text-background",
  "bg-primary-container text-on-primary-container",
];

function parseMonth(value: string | undefined, today: number): number {
  const m = value?.match(/^(\d{4})-(\d{2})$/);
  if (m) return Date.UTC(Number(m[1]), Number(m[2]) - 1, 1);
  const d = new Date(today);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1);
}

function monthParam(dateMs: number, delta: number): string {
  const d = new Date(dateMs);
  const t = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + delta, 1));
  return `${t.getUTCFullYear()}-${String(t.getUTCMonth() + 1).padStart(2, "0")}`;
}

export default async function CalendarPage({ searchParams }: { searchParams: Promise<{ month?: string }> }) {
  const [{ month }, sessions, plans, catalog, unit] = await Promise.all([searchParams, getSessions(), getPlans(), getCatalog(), getUnit()]);
  const today = localDate(Date.now());
  const first = parseMonth(month, today);
  const firstDate = new Date(first);
  const daysInMonth = new Date(Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth() + 1, 0)).getUTCDate();
  const gridStart = weekStart(first + 12 * 3_600_000); // noon avoids zone edge cases
  const cells = Math.ceil(((first - gridStart) / DAY_MS + daysInMonth) / 7) * 7;

  const byDay = Map.groupBy(sessions, (s) => dayKey(s.started_at));

  const dayPosition = new Map(plans.flatMap((p) => p.days.map((d) => [d.sync_id, d.position] as const)));
  const colour = (planDayId: string | null) =>
    planDayId != null && dayPosition.has(planDayId) ? DAY_COLOURS[dayPosition.get(planDayId)! % DAY_COLOURS.length] : "bg-surface-high text-foreground";

  const monthSessions = sessions.filter((s) => {
    const d = localDate(s.started_at);
    return d >= first && d < first + daysInMonth * DAY_MS && s.ended_at != null;
  });
  const activePlan = plans.find((p) => p.is_active);
  const nextDay = activePlan ? nextPlanDay(activePlan.days, sessions) : null;

  return (
    <>
      <PageHeader
        title={formatDateValueMonth(first)}
        subtitle={`${monthSessions.length} workouts · ${formatVolume(monthSessions.reduce((s, x) => s + Number(x.volume_kg), 0), unit)}`}
        actions={
          <>
            <Link className="rounded-full border border-outline px-3 py-1.5 text-sm hover:bg-surface-high" href={`/calendar?month=${monthParam(first, -1)}`}>←</Link>
            <Link className="rounded-full border border-outline px-3 py-1.5 text-sm hover:bg-surface-high" href="/calendar">Today</Link>
            <Link className="rounded-full border border-outline px-3 py-1.5 text-sm hover:bg-surface-high" href={`/calendar?month=${monthParam(first, 1)}`}>→</Link>
          </>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[1fr_280px]">
        <Card className="p-2 sm:p-4">
          <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted">
            {WEEKDAYS.map((d) => (
              <div key={d} className="py-1">{d}</div>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-1">
            {Array.from({ length: cells }, (_, i) => {
              const date = gridStart + i * DAY_MS;
              const inMonth = date >= first && date < first + daysInMonth * DAY_MS;
              const key = new Date(date).toISOString().slice(0, 10);
              const list = byDay.get(key) ?? [];
              return (
                <div
                  key={key}
                  className={clsx(
                    "min-h-20 rounded-lg border p-1 text-left sm:min-h-24 sm:p-1.5",
                    inMonth ? "border-outline" : "border-transparent opacity-40",
                    date === today && "ring-2 ring-primary",
                  )}
                >
                  <div className={clsx("text-xs", date === today ? "font-semibold text-primary" : "text-muted")}>{new Date(date).getUTCDate()}</div>
                  <div className="mt-1 space-y-1">
                    {list.map((s) => (
                      <Link
                        key={s.sync_id}
                        href={`/history/${s.sync_id}`}
                        title={`${s.plan_day_name ?? "Free workout"} · ${formatTime(s.started_at)}`}
                        className={clsx("block truncate rounded px-1 py-0.5 text-[11px] leading-tight font-medium hover:opacity-80 sm:text-xs", colour(s.plan_day_id))}
                      >
                        {s.plan_day_name ?? "Workout"}
                      </Link>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardTitle>Up next</CardTitle>
            {activePlan && nextDay ? (
              <>
                <div className={clsx("inline-block rounded px-2 py-0.5 text-sm font-medium", colour(nextDay.sync_id))}>{nextDay.name}</div>
                <ul className="mt-3 space-y-1 text-sm text-muted">
                  {nextDay.exercises.map((e) => (
                    <li key={e.sync_id} className="truncate">{exerciseName(catalog, e.exercise_id)}</li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="text-sm text-muted">No active plan.</p>
            )}
          </Card>
          {activePlan && (
            <Card>
              <CardTitle>{activePlan.name}</CardTitle>
              <ul className="space-y-1.5 text-sm">
                {activePlan.days.map((d) => (
                  <li key={d.sync_id} className="flex items-center gap-2">
                    <span className={clsx("h-3 w-3 rounded-sm", colour(d.sync_id).split(" ")[0])} />
                    {d.name}
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-muted">Days rotate in order. Scheduling workouts on specific dates is coming later.</p>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
