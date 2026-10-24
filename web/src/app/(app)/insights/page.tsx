import clsx from "clsx";
import Link from "next/link";
import { BarChartView } from "@/components/charts";
import { Card, CardTitle, EmptyState, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getPersonalRecords, getProfile, getSessions, getSetHistory } from "@/lib/data";
import { formatDateValue, formatDateValueMonth, formatFullDate } from "@/lib/dates";
import { formatDuration, formatWeight, GOAL_LABELS, PR_LABELS, volumeTarget } from "@/lib/model";
import { finished, localDate, muscleVolume, weekStart, weeklyTotals, workoutsPerDay } from "@/lib/stats";

const DAY_MS = 86_400_000;
const WEEK_MS = 7 * DAY_MS;
const HEATMAP_WEEKS = 53;

export default async function InsightsPage() {
  const now = Date.now();
  const [profile, sessions, sets, records, catalog] = await Promise.all([
    getProfile(),
    getSessions(),
    getSetHistory(undefined, now - 4 * WEEK_MS),
    getPersonalRecords(),
    getCatalog(),
  ]);
  const done = finished(sessions);
  if (!done.length) {
    return (
      <>
        <PageHeader title="Insights" />
        <EmptyState title="Nothing to analyse yet">Finish a few workouts in the app and your training balance shows up here.</EmptyState>
      </>
    );
  }

  const unit = profile?.units ?? "KG";
  const muscles = (id: string) => catalog.get(id);
  const inWindow = (from: number) => sets.filter((s) => s.session_started_at >= from);

  // Same window as the app's Progress tab: the last 7 days.
  const week = muscleVolume(inWindow(now - WEEK_MS), muscles);
  const fourWeeks = new Map(muscleVolume(inWindow(now - 4 * WEEK_MS), muscles).map((g) => [g.group, g.sets / 4]));
  const [low, high] = volumeTarget(profile?.goal);
  const scaleMax = Math.max(high * 1.25, ...week.map((g) => g.sets), ...[...fourWeeks.values()]);

  // Training heatmap: 53 columns of weeks (Monday on top), ending with this week.
  const perDay = workoutsPerDay(sessions);
  const lastWeek = weekStart(now);
  const firstWeek = lastWeek - (HEATMAP_WEEKS - 1) * WEEK_MS;
  const today = localDate(now);
  const yearCount = done.filter((s) => s.started_at >= firstWeek).length;

  const weeks = weeklyTotals(sessions, 12);
  const avgPerWeek = weeks.reduce((s, w) => s + w.sessions, 0) / weeks.length;
  const recent = done.slice(0, 20);
  const avgDuration = recent.reduce((s, x) => s + (x.ended_at! - x.started_at), 0) / recent.length;

  // Records per month over the last 12 months.
  const months = Array.from({ length: 12 }, (_, i) => {
    const d = new Date(today);
    return Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - 11 + i, 1);
  });
  const recordsPerMonth = months.map((m, i) => ({
    label: formatDateValueMonth(m).split(" ")[0].slice(0, 3),
    value: records.filter((r) => {
      const d = localDate(r.achieved_at);
      return d >= m && (i === months.length - 1 || d < months[i + 1]);
    }).length,
  }));

  return (
    <>
      <PageHeader title="Insights" subtitle={profile ? `Targets follow your goal: ${GOAL_LABELS[profile.goal] ?? profile.goal}` : undefined} />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Workouts / week" value={avgPerWeek.toFixed(1)} hint={profile ? `last 12 weeks · goal ${profile.days_per_week}` : "last 12 weeks"} />
        <Stat label="Average workout" value={formatDuration(avgDuration)} hint="last 20 workouts" />
        <Stat label="Workouts this year" value={yearCount} hint="last 53 weeks" />
        <Stat label="Records" value={records.length} hint="all time" />
      </div>

      <Card className="mt-4">
        <CardTitle>Muscle balance · working sets in the last 7 days</CardTitle>
        <p className="-mt-1 mb-4 text-xs text-muted">
          Shaded band: target of {low}–{high} sets per week. The tick shows your weekly average over the last 4 weeks.
        </p>
        <ul className="space-y-2.5">
          {week.map((g) => {
            const avg = fourWeeks.get(g.group) ?? 0;
            const status = g.sets < low ? "under" : g.sets > high ? "over" : "in";
            return (
              <li key={g.group} className="grid grid-cols-[90px_1fr_64px] items-center gap-3 text-sm">
                <span>{g.group}</span>
                <div className="relative h-5 rounded-full bg-surface-high">
                  <div
                    className="absolute inset-y-0 rounded-full bg-tertiary-container"
                    style={{ left: `${(low / scaleMax) * 100}%`, width: `${((high - low) / scaleMax) * 100}%` }}
                  />
                  <div
                    className={clsx("absolute inset-y-1 left-1 rounded-full", status === "in" ? "bg-primary" : "bg-muted")}
                    style={{ width: `calc(${(g.sets / scaleMax) * 100}% - 4px)`, minWidth: g.sets ? 6 : 0 }}
                  />
                  <div className="absolute -inset-y-0.5 w-0.5 bg-foreground" style={{ left: `${(avg / scaleMax) * 100}%` }} title={`4-week average: ${avg.toFixed(1)}`} />
                </div>
                <span className={clsx("text-right tabular-nums", status === "in" ? "text-foreground" : "text-muted")}>
                  {Number(g.sets.toFixed(1))}
                  <span className="ml-1 text-xs">{status === "under" ? "↓" : status === "over" ? "↑" : "✓"}</span>
                </span>
              </li>
            );
          })}
        </ul>
      </Card>

      <Card className="mt-4">
        <CardTitle>Training days</CardTitle>
        <div className="overflow-x-auto">
          <div className="inline-grid grid-flow-col grid-rows-7 gap-[3px]">
            {Array.from({ length: HEATMAP_WEEKS * 7 }, (_, i) => {
              const date = firstWeek + Math.floor(i / 7) * WEEK_MS + (i % 7) * DAY_MS;
              const key = new Date(date).toISOString().slice(0, 10);
              const count = perDay.get(key) ?? 0;
              return (
                <div
                  key={key}
                  title={`${formatDateValue(date)}: ${count ? `${count} workout${count > 1 ? "s" : ""}` : "rest"}`}
                  className={clsx(
                    "h-3 w-3 rounded-[3px]",
                    date > today ? "opacity-0" : count >= 2 ? "bg-on-primary-container" : count === 1 ? "bg-primary" : "bg-surface-high",
                  )}
                />
              );
            })}
          </div>
        </div>
        <div className="mt-2 flex items-center gap-2 text-xs text-muted">
          <span className="h-3 w-3 rounded-[3px] bg-surface-high" /> Rest
          <span className="ml-2 h-3 w-3 rounded-[3px] bg-primary" /> Workout
        </div>
      </Card>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardTitle>Records per month</CardTitle>
          <BarChartView data={recordsPerMonth} height={200} />
        </Card>
        <Card>
          <CardTitle>Latest records</CardTitle>
          {records.length ? (
            <ul className="divide-y divide-outline text-sm">
              {records.slice(0, 8).map((r) => (
                <li key={r.sync_id} className="flex justify-between gap-3 py-2">
                  <span className="min-w-0">
                    <Link href={`/progress/${r.exercise_id}`} className="block truncate font-medium hover:text-primary">
                      {exerciseName(catalog, r.exercise_id)}
                    </Link>
                    <span className="text-xs text-muted">
                      {PR_LABELS[r.type]} · {formatFullDate(r.achieved_at)}
                    </span>
                  </span>
                  <span className="shrink-0 tabular-nums">
                    {r.type === "REPS_AT_WEIGHT" ? `${r.reps} @ ${formatWeight(r.weight_kg, unit)}` : formatWeight(r.value, unit)}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">No records yet.</p>
          )}
        </Card>
      </div>
    </>
  );
}
