import Link from "next/link";
import { Suspense } from "react";
import { BarChartView } from "@/components/charts";
import { Badge, Card, CardTitle, EmptyState, LinkButton, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getActivePlan, getCatalog, getFinishedSessionCount, getPersonalRecords, getPlanDay, getProfile, getScheduled, getSessions, getUnit } from "@/lib/data";
import { formatDateValue, formatFullDate, formatRelative } from "@/lib/dates";
import { APP_BACKUP_PATH, FREE_WORKOUT, PR_LABELS, WORKOUT } from "@/lib/labels";
import { formatDuration, formatRecord, formatTarget, formatVolume, toUnit } from "@/lib/model";
import { DAY_MS, finished, localDate, nextPlanDay, weekStreak, weeklyTotals, windowStart } from "@/lib/stats";

export const metadata = { title: "Today" };

const WEEKS = 12;

/**
 * The home page (the app's Today tab): what's next first, then this week against the goal,
 * volume and what's new. Each card loads on its own.
 */
export default async function TodayPage() {
  const [count, activePlan] = await Promise.all([getFinishedSessionCount(), getActivePlan()]);

  if (count === 0 && !activePlan) {
    return (
      <>
        <PageHeader title="Today" subtitle={formatFullDate(Date.now())} />
        <EmptyState title="Nothing synced yet" action={<LinkButton href="/plans/new" variant="primary">Create a plan</LinkButton>}>
          Turn on cloud backup in the Spotter app ({APP_BACKUP_PATH}) with this email, then finish a workout. Your training shows up here after the next sync.
          You can also start by making a plan here.
        </EmptyState>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Today" subtitle={formatFullDate(Date.now())} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <Suspense fallback={<ListFallback rows={4} />}>
            <NextWorkout />
          </Suspense>
        </Card>
        <div className="lg:col-span-2">
          <Suspense fallback={<StatsFallback />}>
            <WeekStats total={count} />
          </Suspense>
        </div>
      </div>
      <Card className="mt-4">
        <Suspense fallback={<ChartFallback />}>
          <WeeklyVolume />
        </Suspense>
      </Card>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <Suspense fallback={<ListFallback rows={5} />}>
            <RecentWorkouts />
          </Suspense>
        </Card>
        <Card>
          <Suspense fallback={<ListFallback rows={5} />}>
            <RecentRecords />
          </Suspense>
        </Card>
      </div>
    </>
  );
}

const recentSessions = () => getSessions({ since: windowStart(WEEKS) });

async function WeekStats({ total }: { total: number }) {
  const [sessions, profile, unit] = await Promise.all([recentSessions(), getProfile(), getUnit()]);
  const weeks = weeklyTotals(sessions, WEEKS);
  const thisWeek = weeks[weeks.length - 1];
  const target = profile?.days_per_week;
  // Counted inside the loaded window; a longer streak shows as "12+".
  const streak = weekStreak(sessions);
  const fourWeekVolume = weeks.slice(-4).reduce((s, w) => s + w.volumeKg, 0);
  return (
    <div className="grid h-full grid-cols-2 gap-4">
      <Stat label="This week" value={target ? `${thisWeek.sessions} / ${target}` : thisWeek.sessions} hint={target ? "workouts of your weekly goal" : "workouts"} />
      <Stat label="Week streak" value={streak >= WEEKS ? `${WEEKS}+` : streak} hint={streak === 1 ? "week in a row" : "weeks in a row"} />
      <Stat label="Volume, 4 weeks" value={formatVolume(fourWeekVolume, unit)} hint="working sets, weight × reps" />
      <Stat label="Workouts" value={total} hint="all time" />
    </div>
  );
}

async function WeeklyVolume() {
  const [sessions, unit] = await Promise.all([recentSessions(), getUnit()]);
  const weeks = weeklyTotals(sessions, WEEKS);
  return (
    <>
      <CardTitle>Weekly volume ({unit.toLowerCase()})</CardTitle>
      <BarChartView unit={unit.toLowerCase()} data={weeks.map((w) => ({ label: formatDateValue(w.weekStart), value: Math.round(toUnit(w.volumeKg, unit)) }))} />
    </>
  );
}

/** A workout planned on the calendar comes first; otherwise the active plan's rotation (the app's Today tab). */
async function NextWorkout() {
  const todayDay = localDate(Date.now()) / DAY_MS;
  const [activePlan, [planned], sessions, catalog] = await Promise.all([getActivePlan(), getScheduled({ fromDay: todayDay, open: true, limit: 1 }), recentSessions(), getCatalog()]);
  const plannedDay = planned?.plan_day_id ? (activePlan?.days.find((d) => d.sync_id === planned.plan_day_id) ?? (await getPlanDay(planned.plan_day_id))) : null;
  const nextDay = planned ? plannedDay : activePlan ? nextPlanDay(activePlan.days, sessions) : null;
  const when = planned && (planned.epoch_day === todayDay ? "Scheduled today" : `Planned for ${formatDateValue(planned.epoch_day * DAY_MS)}`);

  return (
    <>
      <CardTitle
        action={
          activePlan && (
            <Link className="rounded-md text-sm text-primary" href={`/plans/${activePlan.sync_id}`}>
              Edit plan
            </Link>
          )
        }
      >
        Next workout
      </CardTitle>
      {planned && !nextDay ? (
        <>
          <div className="text-xs text-muted">{when}</div>
          <div className="mt-1 text-xl font-semibold">{FREE_WORKOUT}</div>
          <p className="mt-1 text-sm text-muted">Planned on your calendar. Add exercises as you go.</p>
          {planned.notes && <p className="mt-2 text-sm text-muted">{planned.notes}</p>}
        </>
      ) : nextDay ? (
        <>
          <div className="text-xs text-muted">{when ?? activePlan?.name}</div>
          <div className="mt-1 text-xl font-semibold">{nextDay.name}</div>
          <ul className="mt-3 space-y-1.5 text-sm">
            {nextDay.exercises.map((e) => (
              <li key={e.sync_id} className="flex justify-between gap-2">
                <span className="truncate">{exerciseName(catalog, e.exercise_id)}</span>
                <span className="shrink-0 text-muted tabular-nums">{formatTarget(e.sets, e.rep_min, e.rep_max, e.exercise_id)}</span>
              </li>
            ))}
          </ul>
          {planned?.notes && <p className="mt-3 text-sm text-muted">{planned.notes}</p>}
        </>
      ) : (
        <div className="text-sm text-muted">
          No plan yet.{" "}
          <Link className="rounded-md text-primary" href="/plans/new">
            Create a plan
          </Link>
        </div>
      )}
    </>
  );
}

async function RecentWorkouts() {
  const [sessions, unit] = await Promise.all([getSessions({ limit: 5 }), getUnit()]);
  const last = finished(sessions)[0];
  return (
    <>
      <CardTitle
        action={
          <Link className="rounded-md text-sm text-primary" href="/history">
            All
          </Link>
        }
      >
        Recent workouts{last && <span className="font-normal"> · last {formatRelative(last.started_at).toLowerCase()}</span>}
      </CardTitle>
      {sessions.length ? (
        <ul className="divide-y divide-outline">
          {sessions.map((s) => (
            <li key={s.sync_id}>
              <Link href={`/history/${s.sync_id}`} className="-mx-2 flex items-center justify-between gap-3 rounded-lg px-2 py-2.5 hover:bg-surface">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 font-medium">
                    <span className="truncate">{s.plan_day_name ?? WORKOUT}</span>
                    {s.ended_at == null && <Badge tone="primary">In progress</Badge>}
                  </div>
                  <div className="text-xs text-muted">
                    {formatRelative(s.started_at)}
                    {s.ended_at != null && ` · ${formatDuration(s.ended_at - s.started_at)}`} · {s.completed_sets} sets
                  </div>
                </div>
                <div className="text-sm text-muted tabular-nums">{formatVolume(s.volume_kg, unit)}</div>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted">No workouts yet. Finished workouts from the app show up here.</p>
      )}
    </>
  );
}

async function RecentRecords() {
  const [records, catalog, unit] = await Promise.all([getPersonalRecords({ limit: 5 }), getCatalog(), getUnit()]);
  return (
    <>
      <CardTitle
        action={
          <Link className="rounded-md text-sm text-primary" href="/progress">
            Progress
          </Link>
        }
      >
        Recent PRs
      </CardTitle>
      {records.length ? (
        <ul className="divide-y divide-outline">
          {records.map((r) => (
            <li key={r.sync_id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <Link href={`/progress/${r.exercise_id}`} className="block truncate rounded-md font-medium hover:text-primary">
                  {exerciseName(catalog, r.exercise_id)}
                </Link>
                <div className="text-xs text-muted">{formatRelative(r.achieved_at)}</div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Badge tone="tertiary">{PR_LABELS[r.type]}</Badge>
                <span className="text-sm tabular-nums">{formatRecord(r, unit)}</span>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted">PRs show up from the second time you log an exercise.</p>
      )}
    </>
  );
}

function StatsFallback() {
  return (
    <div className="grid animate-pulse grid-cols-2 gap-4" aria-hidden="true">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="h-28 rounded-2xl bg-surface-high" />
      ))}
    </div>
  );
}

function ChartFallback() {
  return <div className="h-60 animate-pulse rounded-xl bg-surface-high" aria-hidden="true" />;
}

function ListFallback({ rows }: { rows: number }) {
  return (
    <div className="animate-pulse space-y-3" aria-hidden="true">
      <div className="h-4 w-28 rounded bg-surface-high" />
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-9 rounded-lg bg-surface-high" />
      ))}
    </div>
  );
}
