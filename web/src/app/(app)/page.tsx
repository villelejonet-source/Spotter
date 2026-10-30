import clsx from "clsx";
import Link from "next/link";
import { Suspense } from "react";
import { AreaChartView } from "@/components/charts";
import { Sparkline } from "@/components/sparkline";
import { TrainingHeatmap, heatmapStart } from "@/components/training-heatmap";
import { Badge, Card, CardTitle, Delta, EmptyState, LinkButton, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getActivePlan, getCatalog, getFinishedSessionCount, getPersonalRecords, getPlanDay, getProfile, getScheduled, getSessionTimes, getSessions, getUnit } from "@/lib/data";
import { formatDateValue, formatFullDate, formatRelative } from "@/lib/dates";
import { APP_BACKUP_PATH, FREE_WORKOUT, PR_LABELS, WORKOUT } from "@/lib/labels";
import { formatDuration, formatRecord, formatTarget, formatVolume, toUnit } from "@/lib/model";
import { DAY_MS, finished, formatChange, localDate, nextPlanDay, percentChange, weekStreak, weeklyTotals, windowStart, workoutsPerDay } from "@/lib/stats";

export const metadata = { title: "Today" };

const WEEKS = 12;
const HEATMAP_WEEKS = 18;

/** The volume chart's windows, picked with ?range= (links, so the page stays server-rendered). */
const RANGES = [
  { key: "4w", label: "4W", weeks: 4 },
  { key: "12w", label: "12W", weeks: 12 },
  { key: "6m", label: "6M", weeks: 26 },
  { key: "1y", label: "1Y", weeks: 52 },
] as const;
const rangeOf = (key: string | undefined) => RANGES.find((r) => r.key === key) ?? RANGES[1];

/**
 * The home page (the app's Today tab): what's next first, then this week against the goal,
 * volume and what's new. Each card loads on its own.
 */
export default async function TodayPage({ searchParams }: { searchParams: Promise<{ range?: string }> }) {
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
      <PageHeader
        eyebrow={formatFullDate(Date.now())}
        title="Today"
        actions={
          <LinkButton href="/calendar" variant="secondary">
            Plan your week
          </LinkButton>
        }
      />
      <Suspense fallback={<StatsFallback />}>
        <WeekStats total={count} />
      </Suspense>
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          {/* The period is read inside the card's own boundary: awaited at the top of the page it
              held back every card and broke hydration in dev. */}
          <Suspense fallback={<ChartFallback />}>
            <WeeklyVolume searchParams={searchParams} />
          </Suspense>
        </Card>
        <Card>
          <Suspense fallback={<ListFallback rows={4} />}>
            <NextWorkout />
          </Suspense>
        </Card>
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card>
          <Suspense fallback={<ListFallback rows={3} />}>
            <TrainingDays />
          </Suspense>
        </Card>
        <Card>
          <Suspense fallback={<ListFallback rows={5} />}>
            <RecentRecords />
          </Suspense>
        </Card>
        <Card>
          <Suspense fallback={<ListFallback rows={5} />}>
            <RecentWorkouts />
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
  const sum = (ws: typeof weeks) => ws.reduce((s, w) => s + w.volumeKg, 0);
  const fourWeekVolume = sum(weeks.slice(-4));
  const change = percentChange(fourWeekVolume, sum(weeks.slice(-8, -4)));
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <Stat
        label="This week"
        value={target ? `${thisWeek.sessions} / ${target}` : thisWeek.sessions}
        hint={target ? "workouts of your weekly goal" : "workouts"}
        spark={<Sparkline values={weeks.map((w) => w.sessions)} />}
      />
      <Stat
        label="Volume, 4 weeks"
        value={formatVolume(fourWeekVolume, unit)}
        hint="vs the 4 weeks before"
        delta={change != null && <Delta tone={change >= 0 ? "good" : "neutral"}>{formatChange(change)}</Delta>}
        spark={<Sparkline values={weeks.map((w) => w.volumeKg)} />}
      />
      <Stat label="Week streak" value={streak >= WEEKS ? `${WEEKS}+` : streak} hint={streak === 1 ? "week in a row" : "weeks in a row"} />
      <Stat label="Workouts" value={total} hint="all time" />
    </div>
  );
}

async function WeeklyVolume({ searchParams }: { searchParams: Promise<{ range?: string }> }) {
  const range = rangeOf((await searchParams).range);
  const [sessions, unit] = await Promise.all([getSessions({ since: windowStart(range.weeks) }), getUnit()]);
  const weeks = weeklyTotals(sessions, range.weeks);
  const thisWeek = weeks[weeks.length - 1];
  return (
    <>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="label text-muted">Weekly volume</h2>
          <div className="mt-2 font-mono text-2xl font-medium tracking-[-0.03em] tabular-nums">
            {formatVolume(thisWeek.volumeKg, unit)}
            <span className="ml-2 font-sans text-sm font-normal tracking-normal text-muted">this week</span>
          </div>
        </div>
        <nav aria-label="Chart period" className="flex rounded-lg border border-outline p-0.5 font-mono text-[0.6875rem]">
          {RANGES.map((r) => (
            <Link
              key={r.key}
              href={r.key === RANGES[1].key ? "/" : `/?range=${r.key}`}
              scroll={false}
              aria-current={r.key === range.key ? "true" : undefined}
              className={clsx("rounded-md px-2.5 py-1 transition", r.key === range.key ? "bg-surface text-foreground" : "text-muted hover:text-foreground")}
            >
              {r.label}
            </Link>
          ))}
        </nav>
      </div>
      <AreaChartView unit={unit.toLowerCase()} data={weeks.map((w) => ({ label: formatDateValue(w.weekStart), value: Math.round(toUnit(w.volumeKg, unit)) }))} />
    </>
  );
}

async function TrainingDays() {
  const now = Date.now();
  const times = await getSessionTimes(heatmapStart(HEATMAP_WEEKS, now));
  const perDay = workoutsPerDay(times);
  return (
    <>
      <CardTitle action={<span className="label text-faint">{workoutCount(finished(times).length)}</span>}>Training days · {HEATMAP_WEEKS} weeks</CardTitle>
      <TrainingHeatmap weeks={HEATMAP_WEEKS} perDay={perDay} now={now} />
    </>
  );
}

const workoutCount = (n: number) => `${n} workout${n === 1 ? "" : "s"}`;

/** How much of the weekly goal is done, as a ring with the percentage inside. */
function WeekRing({ done, target }: { done: number; target: number }) {
  const r = 42;
  const length = 2 * Math.PI * r;
  const share = Math.min(done / target, 1);
  return (
    <div className="relative h-20 w-20 shrink-0" role="img" aria-label={`${done} of ${target} workouts this week`}>
      <svg viewBox="0 0 100 100" className="h-full w-full -rotate-90" aria-hidden="true">
        <circle cx="50" cy="50" r={r} fill="none" stroke="var(--surface-high)" strokeWidth="8" />
        <circle
          cx="50"
          cy="50"
          r={r}
          fill="none"
          stroke="var(--primary)"
          strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray={`${share * length} ${length}`}
          style={{ filter: "drop-shadow(0 0 4px var(--glow))" }}
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">
        <div>
          <div className="font-mono text-base leading-none">{Math.round(share * 100)}%</div>
          <div className="mt-1 text-[0.625rem] text-muted">of week</div>
        </div>
      </div>
    </div>
  );
}

/** A workout planned on the calendar comes first; otherwise the active plan's rotation (the app's Today tab). */
async function NextWorkout() {
  const todayDay = localDate(Date.now()) / DAY_MS;
  const [activePlan, [planned], sessions, catalog, profile] = await Promise.all([
    getActivePlan(),
    getScheduled({ fromDay: todayDay, open: true, limit: 1 }),
    recentSessions(),
    getCatalog(),
    getProfile(),
  ]);
  const weeks = weeklyTotals(sessions, 1);
  const target = profile?.days_per_week;
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
          <div className="flex items-center gap-4">
            {target ? <WeekRing done={weeks[0].sessions} target={target} /> : null}
            <div className="min-w-0">
              <div className="text-xs text-muted">{when ?? activePlan?.name}</div>
              <div className="mt-1 text-xl font-semibold tracking-tight">{nextDay.name}</div>
              <div className="mt-0.5 text-xs text-muted">{nextDay.exercises.length} exercises</div>
            </div>
          </div>
          <ol className="mt-4 divide-y divide-outline border-t border-outline text-sm">
            {nextDay.exercises.map((e, i) => (
              <li key={e.sync_id} className="grid grid-cols-[1.75rem_1fr_auto] items-center gap-2 py-2">
                <span className="font-mono text-[0.6875rem] text-faint">{String(i + 1).padStart(2, "0")}</span>
                <span className="truncate">{exerciseName(catalog, e.exercise_id)}</span>
                <span className="shrink-0 font-mono text-xs text-muted tabular-nums">{formatTarget(e.sets, e.rep_min, e.rep_max, e.exercise_id)}</span>
              </li>
            ))}
          </ol>
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
        Recent workouts{last && <span> · last {formatRelative(last.started_at).toLowerCase()}</span>}
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
                <div className="font-mono text-sm text-muted tabular-nums">{formatVolume(s.volume_kg, unit)}</div>
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
        Recent records
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
                <span className="font-mono text-sm tabular-nums">{formatRecord(r, unit)}</span>
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
    <div className="grid animate-pulse grid-cols-2 gap-4 lg:grid-cols-4" aria-hidden="true">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="h-32 rounded-2xl bg-surface-high" />
      ))}
    </div>
  );
}

function ChartFallback() {
  return <div className="h-80 animate-pulse rounded-xl bg-surface-high" aria-hidden="true" />;
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
