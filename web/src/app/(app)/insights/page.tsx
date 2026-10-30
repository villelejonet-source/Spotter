import clsx from "clsx";
import Link from "next/link";
import { Suspense } from "react";
import { BarChartView } from "@/components/charts";
import { TrainingHeatmap, heatmapStart } from "@/components/training-heatmap";
import { Card, CardTitle, EmptyState, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getFinishedSessionCount, getPersonalRecords, getProfile, getRecordCount, getSessionTimes, getSetHistory, getUnit } from "@/lib/data";
import { formatDateValueMonthShort, formatFullDate } from "@/lib/dates";
import { GOAL_LABELS, label, PR_LABELS } from "@/lib/labels";
import { formatDuration, formatRecord, volumeTarget } from "@/lib/model";
import { DAY_MS, finished, localDate, muscleVolume, WEEK_MS, weeklyTotals, workoutsPerDay } from "@/lib/stats";

export const metadata = { title: "Insights" };

const HEATMAP_WEEKS = 53;

/** Training balance, consistency and records; the page shell shows first and each card streams in. */
export default async function InsightsPage() {
  const [count, profile] = await Promise.all([getFinishedSessionCount(), getProfile()]);
  if (count === 0) {
    return (
      <>
        <PageHeader title="Insights" />
        <EmptyState title="Nothing to chart yet">Log a few workouts to see your muscle balance, training days and PRs.</EmptyState>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Insights" subtitle={profile ? `Targets follow your goal: ${label(GOAL_LABELS, profile.goal)}.` : undefined} />
      <Suspense fallback={<Tiles />}>
        <Overview />
      </Suspense>
      <Card className="mt-4">
        <Suspense fallback={<Block height="h-80" />}>
          <MuscleBalance />
        </Suspense>
      </Card>
      <Card className="mt-4">
        <Suspense fallback={<Block height="h-28" />}>
          <TrainingDays />
        </Suspense>
      </Card>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <Suspense fallback={<Block height="h-56" />}>
            <RecordsPerMonth />
          </Suspense>
        </Card>
        <Card>
          <Suspense fallback={<Block height="h-56" />}>
            <LatestRecords />
          </Suspense>
        </Card>
      </div>
    </>
  );
}

/** The heatmap window: 53 columns of weeks (Monday on top), ending with this week. */
async function Overview() {
  const now = Date.now();
  const [times, records, profile] = await Promise.all([getSessionTimes(heatmapStart(HEATMAP_WEEKS, now)), getRecordCount(), getProfile()]);
  const done = finished(times);
  const weeks = weeklyTotals(times, 12, now);
  const avgPerWeek = weeks.reduce((s, w) => s + w.sessions, 0) / weeks.length;
  const recent = done.slice(-20);
  const avgDuration = recent.length ? recent.reduce((s, x) => s + (x.ended_at! - x.started_at), 0) / recent.length : 0;
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <Stat label="Workouts / week" value={avgPerWeek.toFixed(1)} hint={profile ? `last 12 weeks · goal ${profile.days_per_week}` : "last 12 weeks"} />
      <Stat label="Average workout" value={recent.length ? formatDuration(avgDuration) : "–"} hint="last 20 workouts" />
      <Stat label="Workouts this year" value={done.length} hint="last 53 weeks" />
      <Stat label="PRs" value={records} hint="all time" />
    </div>
  );
}

async function MuscleBalance() {
  const now = Date.now();
  const [sets, profile, catalog] = await Promise.all([getSetHistory(undefined, now - 4 * WEEK_MS), getProfile(), getCatalog()]);
  const muscles = (id: string) => catalog.get(id);
  // Same window as the app's Progress tab: the last 7 days.
  const week = muscleVolume(sets.filter((s) => s.session_started_at >= now - WEEK_MS), muscles);
  const fourWeeks = new Map(muscleVolume(sets, muscles).map((g) => [g.group, g.sets / 4]));
  const [low, high] = volumeTarget(profile?.goal);
  const scaleMax = Math.max(high * 1.25, ...week.map((g) => g.sets), ...fourWeeks.values());
  return (
    <>
      <CardTitle>Muscle balance · last 7 days</CardTitle>
      <p className="-mt-1 mb-4 text-xs text-muted">
        Working sets per muscle group. Target {low}–{high} for your goal (shaded). The tick is your weekly average over the last 4 weeks.
      </p>
      <ul className="space-y-2.5">
        {week.map((g) => {
          const avg = fourWeeks.get(g.group) ?? 0;
          const status = g.sets < low ? "under" : g.sets > high ? "over" : "in";
          const text = `${g.group}: ${Number(g.sets.toFixed(1))} sets this week, ${status === "in" ? "on target" : status}; 4-week average ${avg.toFixed(1)}`;
          return (
            <li key={g.group} className="grid grid-cols-[90px_1fr_64px] items-center gap-3 text-sm">
              <span>{g.group}</span>
              <div className="relative h-5 rounded-full bg-surface-high" role="img" aria-label={text} title={text}>
                <div className="absolute inset-y-0 rounded-full bg-tertiary-container" style={{ left: `${(low / scaleMax) * 100}%`, width: `${((high - low) / scaleMax) * 100}%` }} />
                <div
                  className={clsx("absolute inset-y-1 left-1 rounded-full", status === "in" ? "bg-primary" : "bg-muted")}
                  style={{ width: `calc(${(g.sets / scaleMax) * 100}% - 4px)`, minWidth: g.sets ? 6 : 0 }}
                />
                <div className="absolute -inset-y-0.5 w-0.5 bg-foreground" style={{ left: `${(avg / scaleMax) * 100}%` }} />
              </div>
              <span className={clsx("text-right tabular-nums", status === "in" ? "text-foreground" : "text-muted")}>
                {Number(g.sets.toFixed(1))}
                <span className="ml-1 text-xs" aria-hidden="true">
                  {status === "under" ? "↓" : status === "over" ? "↑" : "✓"}
                </span>
              </span>
            </li>
          );
        })}
      </ul>
    </>
  );
}

async function TrainingDays() {
  const now = Date.now();
  const perDay = workoutsPerDay(await getSessionTimes(heatmapStart(HEATMAP_WEEKS, now)));
  return (
    <>
      <CardTitle>Training days</CardTitle>
      <TrainingHeatmap weeks={HEATMAP_WEEKS} perDay={perDay} now={now} />
    </>
  );
}

async function RecordsPerMonth() {
  const today = localDate(Date.now());
  const d = new Date(today);
  const months = Array.from({ length: 12 }, (_, i) => Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - 11 + i, 1));
  // Month boundaries in the display zone: a record logged late on the last day belongs to its month.
  const records = await getPersonalRecords({ since: months[0] - DAY_MS });
  const perMonth = months.map((m, i) => ({
    label: formatDateValueMonthShort(m),
    value: records.filter((r) => {
      const day = localDate(r.achieved_at);
      return day >= m && (i === months.length - 1 || day < months[i + 1]);
    }).length,
  }));
  return (
    <>
      <CardTitle>PRs per month</CardTitle>
      <BarChartView data={perMonth} height={200} />
    </>
  );
}

async function LatestRecords() {
  const [records, catalog, unit] = await Promise.all([getPersonalRecords({ limit: 8 }), getCatalog(), getUnit()]);
  return (
    <>
      <CardTitle>Recent PRs</CardTitle>
      {records.length ? (
        <ul className="divide-y divide-outline text-sm">
          {records.map((r) => (
            <li key={r.sync_id} className="flex justify-between gap-3 py-2">
              <span className="min-w-0">
                <Link href={`/progress/${r.exercise_id}`} className="block truncate rounded-md font-medium hover:text-primary">
                  {exerciseName(catalog, r.exercise_id)}
                </Link>
                <span className="text-xs text-muted">
                  {PR_LABELS[r.type]} · {formatFullDate(r.achieved_at)}
                </span>
              </span>
              <span className="shrink-0 tabular-nums">{formatRecord(r, unit)}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted">PRs show up from the second time you log an exercise.</p>
      )}
    </>
  );
}

function Tiles() {
  return (
    <div className="grid animate-pulse grid-cols-2 gap-4 lg:grid-cols-4" aria-hidden="true">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="h-28 rounded-2xl bg-surface-high" />
      ))}
    </div>
  );
}

function Block({ height }: { height: string }) {
  return <div className={clsx("animate-pulse rounded-xl bg-surface-high", height)} aria-hidden="true" />;
}
