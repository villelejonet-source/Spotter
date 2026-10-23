import Link from "next/link";
import { BarChartView } from "@/components/charts";
import { Badge, Card, CardTitle, EmptyState, LinkButton, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getPersonalRecords, getPlans, getProfile, getSessions } from "@/lib/data";
import { formatDateValue, formatRelative } from "@/lib/dates";
import { formatDuration, formatVolume, formatWeight, PR_LABELS, toUnit } from "@/lib/model";
import { finished, nextPlanDay, weekStreak, weeklyTotals } from "@/lib/stats";

export default async function DashboardPage() {
  const [profile, sessions, plans, records, catalog] = await Promise.all([
    getProfile(),
    getSessions(),
    getPlans(),
    getPersonalRecords(),
    getCatalog(),
  ]);
  const unit = profile?.units ?? "KG";
  const done = finished(sessions);

  if (!done.length && !plans.length) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <EmptyState title="Nothing synced yet">
          Turn on cloud backup in the Spotter app (Profile → Account) with this email, then finish a workout. Your training
          shows up here after the next sync.
        </EmptyState>
      </>
    );
  }

  const weeks = weeklyTotals(sessions, 12);
  const thisWeek = weeks.at(-1)!;
  const target = profile?.days_per_week;
  const streak = weekStreak(sessions);
  const activePlan = plans.find((p) => p.is_active);
  const nextDay = activePlan ? nextPlanDay(activePlan.days, sessions) : null;
  const last = done[0];
  const fourWeekVolume = weeks.slice(-4).reduce((s, w) => s + w.volumeKg, 0);

  return (
    <>
      <PageHeader title="Dashboard" subtitle={last ? `Last workout ${formatRelative(last.started_at).toLowerCase()}` : undefined} />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat
          label="This week"
          value={target ? `${thisWeek.sessions} / ${target}` : thisWeek.sessions}
          hint={target ? "workouts vs. your weekly goal" : "workouts"}
        />
        <Stat label="Week streak" value={streak} hint={streak === 1 ? "week in a row" : "weeks in a row"} />
        <Stat label="Volume, 4 weeks" value={formatVolume(fourWeekVolume, unit)} hint="working sets, weight × reps" />
        <Stat label="Workouts" value={done.length} hint="all time" />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardTitle>Weekly volume ({unit.toLowerCase()})</CardTitle>
          <BarChartView
            unit={unit.toLowerCase()}
            data={weeks.map((w) => ({ label: formatDateValue(w.weekStart), value: Math.round(toUnit(w.volumeKg, unit)) }))}
          />
        </Card>

        <Card>
          <CardTitle action={activePlan && <Link className="text-sm text-primary" href={`/plans/${activePlan.sync_id}`}>Edit</Link>}>
            Up next
          </CardTitle>
          {activePlan && nextDay ? (
            <>
              <div className="text-xs text-muted">{activePlan.name}</div>
              <div className="mt-1 text-xl font-semibold">{nextDay.name}</div>
              <ul className="mt-3 space-y-1.5 text-sm">
                {nextDay.exercises.map((e) => (
                  <li key={e.sync_id} className="flex justify-between gap-2">
                    <span className="truncate">{exerciseName(catalog, e.exercise_id)}</span>
                    <span className="shrink-0 text-muted tabular-nums">
                      {e.sets} × {e.rep_min === e.rep_max ? e.rep_min : `${e.rep_min}–${e.rep_max}`}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <div className="text-sm text-muted">
              No active plan. <Link className="text-primary" href="/plans">Create one</Link>
            </div>
          )}
        </Card>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardTitle action={<Link className="text-sm text-primary" href="/history">All</Link>}>Recent workouts</CardTitle>
          <ul className="divide-y divide-outline">
            {done.slice(0, 5).map((s) => (
              <li key={s.sync_id}>
                <Link href={`/history/${s.sync_id}`} className="-mx-2 flex items-center justify-between gap-3 rounded-lg px-2 py-2.5 hover:bg-surface-high">
                  <div>
                    <div className="font-medium">{s.plan_day_name ?? "Free workout"}</div>
                    <div className="text-xs text-muted">
                      {formatRelative(s.started_at)} · {formatDuration(s.ended_at! - s.started_at)} · {s.completed_sets} sets
                    </div>
                  </div>
                  <div className="text-sm text-muted tabular-nums">{formatVolume(s.volume_kg, unit)}</div>
                </Link>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <CardTitle action={<Link className="text-sm text-primary" href="/progress">Progress</Link>}>Recent records</CardTitle>
          {records.length ? (
            <ul className="divide-y divide-outline">
              {records.slice(0, 5).map((r) => (
                <li key={r.sync_id} className="flex items-center justify-between gap-3 py-2.5">
                  <div className="min-w-0">
                    <Link href={`/progress/${r.exercise_id}`} className="block truncate font-medium hover:text-primary">
                      {exerciseName(catalog, r.exercise_id)}
                    </Link>
                    <div className="text-xs text-muted">{formatRelative(r.achieved_at)}</div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <Badge tone="tertiary">{PR_LABELS[r.type]}</Badge>
                    <span className="text-sm tabular-nums">
                      {r.type === "REPS_AT_WEIGHT" ? `${r.reps} @ ${formatWeight(r.weight_kg, unit)}` : formatWeight(r.value, unit)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <div className="text-sm text-muted">No records yet.</div>
          )}
        </Card>
      </div>

      {!activePlan && (
        <div className="mt-6">
          <LinkButton href="/plans/new" variant="primary">Create a plan</LinkButton>
        </div>
      )}
    </>
  );
}
