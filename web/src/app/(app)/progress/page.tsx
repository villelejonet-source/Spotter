import Link from "next/link";
import { LineChartView } from "@/components/charts";
import { Card, CardTitle, EmptyState, PageHeader } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getBodyWeight, getCatalog, getSetHistory, getUnit } from "@/lib/data";
import { formatDateValue, formatRelative } from "@/lib/dates";
import { formatWeight, TIMED_EXERCISE_IDS, toUnit } from "@/lib/model";
import { epochDayToMs, exerciseProgress } from "@/lib/stats";

export default async function ProgressPage() {
  const [sets, bodyWeight, catalog, unit] = await Promise.all([
    getSetHistory(),
    getBodyWeight(),
    getCatalog(),
    getUnit(),
  ]);
  const sessionDates = new Map(sets.map((s) => [s.session_id, s.session_started_at]));
  const byExercise = Map.groupBy(sets, (s) => s.exercise_id);

  const exercises = [...byExercise]
    .map(([id, list]) => {
      const points = exerciseProgress(list, id, sessionDates);
      const last = points.at(-1);
      const best = Math.max(0, ...points.map((p) => p.e1rmKg ?? 0));
      const first = points.find((p) => p.e1rmKg != null)?.e1rmKg;
      const longestHold = Math.max(0, ...points.map((p) => p.bestReps));
      return { id, name: exerciseName(catalog, id), sessions: points.length, last, best, longestHold, change: first && last?.e1rmKg ? last.e1rmKg - first : null };
    })
    .filter((e) => e.sessions > 0)
    .sort((a, b) => (b.last?.date ?? 0) - (a.last?.date ?? 0));

  if (!exercises.length && !bodyWeight.length) {
    return (
      <>
        <PageHeader title="Progress" />
        <EmptyState title="No progress to show yet">Finish a few workouts in the app and your lifts will be charted here.</EmptyState>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Progress" subtitle="Estimated 1RM uses the Epley formula on working sets." />

      {bodyWeight.length > 1 && (
        <Card className="mb-4">
          <CardTitle>Body weight ({unit.toLowerCase()})</CardTitle>
          <LineChartView
            unit={unit.toLowerCase()}
            data={bodyWeight.map((b) => ({ label: formatDateValue(epochDayToMs(b.epoch_day)), value: Number(toUnit(b.weight_kg, unit).toFixed(1)) }))}
          />
        </Card>
      )}

      <Card className="p-2">
        <table className="w-full text-sm">
          <thead className="text-left text-xs text-muted">
            <tr>
              <th className="px-3 py-2 font-normal">Exercise</th>
              <th className="hidden px-3 py-2 font-normal sm:table-cell">Last done</th>
              <th className="px-3 py-2 text-right font-normal">Best e1RM</th>
              <th className="hidden px-3 py-2 text-right font-normal sm:table-cell">Change</th>
              <th className="px-3 py-2 text-right font-normal">Sessions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline tabular-nums">
            {exercises.map((e) => (
              <tr key={e.id} className="hover:bg-surface-high">
                <td className="px-3 py-2.5">
                  <Link href={`/progress/${e.id}`} className="font-medium hover:text-primary">{e.name}</Link>
                </td>
                <td className="hidden px-3 py-2.5 text-muted sm:table-cell">{e.last ? formatRelative(e.last.date) : "–"}</td>
                <td className="px-3 py-2.5 text-right">
                  {TIMED_EXERCISE_IDS.has(e.id) ? `${e.longestHold} s` : e.best ? formatWeight(e.best, unit) : "BW"}
                </td>
                <td className="hidden px-3 py-2.5 text-right sm:table-cell">
                  {e.change != null && Math.abs(e.change) >= 0.05 ? (
                    <span className={e.change > 0 ? "text-primary" : "text-muted"}>
                      {e.change > 0 ? "+" : "−"}
                      {formatWeight(Math.abs(e.change), unit)}
                    </span>
                  ) : (
                    <span className="text-muted">–</span>
                  )}
                </td>
                <td className="px-3 py-2.5 text-right text-muted">{e.sessions}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  );
}
