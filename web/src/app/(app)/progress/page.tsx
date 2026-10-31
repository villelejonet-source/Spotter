import Link from "next/link";
import { LineChartView } from "@/components/charts";
import { Card, CardTitle, EmptyState, LinkButton, PageHeader } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { groupBy } from "@/lib/collections";
import { getBodyWeight, getCatalog, getProgressSets, getUnit } from "@/lib/data";
import { formatDateValue, formatRelative } from "@/lib/dates";
import { BODYWEIGHT } from "@/lib/labels";
import { formatWeight, isTimed, toUnit } from "@/lib/model";
import { DAY_MS, epochDayToMs, exerciseProgress } from "@/lib/stats";

export const metadata = { title: "Progress" };

const WINDOW_DAYS = 183;

/** Your lifts (the app's Progress tab): the last 6 months by default, everything with ?all=1. */
export default async function ProgressPage({ searchParams }: { searchParams: Promise<{ all?: string }> }) {
  const { all } = await searchParams;
  const showAll = all === "1";
  const since = showAll ? undefined : Date.now() - WINDOW_DAYS * DAY_MS;
  const [sets, bodyWeight, catalog, unit] = await Promise.all([getProgressSets(since), getBodyWeight(), getCatalog(), getUnit()]);
  const sessionDates = new Map(sets.map((s) => [s.session_id, s.session_started_at]));

  const exercises = [...groupBy(sets, (s) => s.exercise_id)]
    .map(([id, list]) => {
      const points = exerciseProgress(list, id, sessionDates);
      const last = points[points.length - 1];
      const loaded = points.filter((p) => p.e1rmKg != null);
      const best = loaded.length ? Math.max(...loaded.map((p) => p.e1rmKg!)) : null;
      const first = loaded[0]?.e1rmKg;
      const longestHold = Math.max(0, ...points.map((p) => p.bestReps));
      return { id, name: exerciseName(catalog, id), sessions: points.length, last, best, longestHold, change: first != null && last?.e1rmKg != null ? last.e1rmKg - first : null };
    })
    .filter((e) => e.sessions > 0)
    .sort((a, b) => (b.last?.date ?? 0) - (a.last?.date ?? 0));

  const windowLabel = showAll ? "All time" : "Last 6 months";
  const toggle = <LinkButton href={showAll ? "/progress" : "/progress?all=1"}>{showAll ? "Last 6 months" : "Show all time"}</LinkButton>;

  if (!exercises.length && !bodyWeight.length) {
    return (
      <>
        <PageHeader title="Progress" actions={toggle} />
        <EmptyState title="Nothing to chart yet">
          {showAll ? "Log a few workouts to see your PRs, strength trends and body weight." : "No workouts in the last 6 months. Try all time, or log a few workouts."}
        </EmptyState>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Progress" subtitle={`${windowLabel} · est. 1RM uses the Epley formula on working sets.`} actions={toggle} />

      {bodyWeight.length > 1 && (
        <Card className="mb-4">
          <CardTitle>Body weight ({unit.toLowerCase()})</CardTitle>
          <LineChartView unit={unit.toLowerCase()} data={bodyWeight.map((b) => ({ label: formatDateValue(epochDayToMs(b.epoch_day)), value: Number(toUnit(b.weight_kg, unit).toFixed(1)) }))} />
        </Card>
      )}

      {exercises.length ? (
        <Card className="p-2">
          <table className="w-full text-sm">
            <caption className="sr-only">Your lifts, most recent first</caption>
            <thead className="text-left text-xs text-muted">
              <tr>
                <th scope="col" className="px-2 py-2 sm:px-3 font-normal">
                  Exercise
                </th>
                <th scope="col" className="hidden px-2 py-2 sm:px-3 font-normal sm:table-cell">
                  Last done
                </th>
                <th scope="col" className="px-2 py-2 sm:px-3 text-right font-normal">
                  Best est. 1RM
                </th>
                <th scope="col" className="hidden px-2 py-2 sm:px-3 text-right font-normal sm:table-cell">
                  Change
                </th>
                <th scope="col" className="px-2 py-2 sm:px-3 text-right font-normal">
                  Sessions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline tabular-nums">
              {exercises.map((e) => (
                <tr key={e.id} className="hover:bg-surface">
                  <td className="px-2 py-2.5 sm:px-3">
                    <Link href={`/progress/${e.id}`} className="rounded-md font-medium hover:text-primary">
                      {e.name}
                    </Link>
                  </td>
                  <td className="hidden px-2 py-2.5 sm:px-3 text-muted sm:table-cell">{e.last ? formatRelative(e.last.date) : "–"}</td>
                  <td className="px-2 py-2.5 sm:px-3 text-right">{isTimed(e.id) ? `${e.longestHold} s` : e.best != null ? formatWeight(e.best, unit) : BODYWEIGHT}</td>
                  <td className="hidden px-2 py-2.5 sm:px-3 text-right sm:table-cell">
                    {e.change != null && Math.abs(e.change) >= 0.05 ? (
                      <span className={e.change > 0 ? "text-primary" : "text-muted"}>
                        {e.change > 0 ? "+" : "−"}
                        {formatWeight(Math.abs(e.change), unit)}
                      </span>
                    ) : (
                      <span className="text-muted">–</span>
                    )}
                  </td>
                  <td className="px-2 py-2.5 sm:px-3 text-right text-muted">{e.sessions}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      ) : (
        <EmptyState title={showAll ? "No lifts logged yet" : "No lifts in the last 6 months"}>Finish a workout in the app and your lifts are charted here.</EmptyState>
      )}
    </>
  );
}
