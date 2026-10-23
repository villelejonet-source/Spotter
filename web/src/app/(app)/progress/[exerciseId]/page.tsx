import Link from "next/link";
import { notFound } from "next/navigation";
import { LineChartView } from "@/components/charts";
import { Badge, Card, CardTitle, PageHeader, Stat } from "@/components/ui";
import { getCatalog, getPersonalRecords, getSessions, getSetHistory, getUnit } from "@/lib/data";
import { formatDayMonth, formatFullDate } from "@/lib/dates";
import { enumLabel, formatVolume, formatWeight, PR_LABELS, TIMED_EXERCISE_IDS, toUnit } from "@/lib/model";
import { exerciseProgress } from "@/lib/stats";

export default async function ExerciseProgressPage({ params }: { params: Promise<{ exerciseId: string }> }) {
  const { exerciseId } = await params;
  const id = decodeURIComponent(exerciseId);
  const [sets, sessions, records, catalog, unit] = await Promise.all([
    getSetHistory(),
    getSessions(),
    getPersonalRecords(),
    getCatalog(),
    getUnit(),
  ]);
  const exercise = catalog.get(id);
  const mine = sets.filter((s) => s.exercise_id === id);
  if (!exercise && !mine.length) notFound();

  const sessionDates = new Map(sessions.filter((s) => s.ended_at != null).map((s) => [s.sync_id, s.started_at]));
  const points = exerciseProgress(mine, id, sessionDates);
  const timed = TIMED_EXERCISE_IDS.has(id);
  const loaded = points.some((p) => p.e1rmKg != null);
  const prs = records.filter((r) => r.exercise_id === id);
  const u = unit.toLowerCase();

  return (
    <>
      <Link href="/progress" className="text-sm text-muted hover:text-foreground">← Progress</Link>
      <div className="mt-2">
        <PageHeader
          title={exercise?.name ?? id}
          subtitle={
            exercise && (
              <span className="flex flex-wrap gap-1">
                {exercise.primary.map((m) => (
                  <Badge key={m} tone="primary">{enumLabel(m)}</Badge>
                ))}
                {exercise.equipment.map((e) => (
                  <Badge key={e}>{enumLabel(e)}</Badge>
                ))}
              </span>
            )
          }
        />
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Sessions" value={points.length} />
        {timed ? (
          <Stat label="Longest hold" value={`${Math.max(0, ...points.map((p) => p.bestReps))} s`} />
        ) : (
          <Stat label="Best e1RM" value={loaded ? formatWeight(Math.max(...points.map((p) => p.e1rmKg ?? 0)), unit) : "–"} />
        )}
        <Stat
          label="Last top set"
          value={points.at(-1)?.topSet ? (timed ? `${points.at(-1)!.topSet!.reps} s` : `${formatWeight(points.at(-1)!.topSet!.weightKg, unit)} × ${points.at(-1)!.topSet!.reps}`) : "–"}
        />
        <Stat label="Records" value={prs.length} />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {loaded && (
          <Card>
            <CardTitle>Estimated 1RM ({u})</CardTitle>
            <LineChartView
              unit={u}
              data={points.map((p) => ({ label: formatDayMonth(p.date), value: p.e1rmKg != null ? Number(toUnit(p.e1rmKg, unit).toFixed(1)) : null }))}
            />
          </Card>
        )}
        {loaded ? (
          <Card>
            <CardTitle>Volume per session ({u})</CardTitle>
            <LineChartView unit={u} data={points.map((p) => ({ label: formatDayMonth(p.date), value: Math.round(toUnit(p.volumeKg, unit)) }))} />
          </Card>
        ) : (
          <Card>
            <CardTitle>{timed ? "Best hold (s)" : "Best set (reps)"}</CardTitle>
            <LineChartView unit={timed ? "s" : "reps"} data={points.map((p) => ({ label: formatDayMonth(p.date), value: p.bestReps }))} />
          </Card>
        )}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardTitle>Sessions</CardTitle>
          <ul className="divide-y divide-outline text-sm tabular-nums">
            {[...points].reverse().map((p) => (
              <li key={p.sessionId}>
                <Link href={`/history/${p.sessionId}`} className="-mx-2 flex justify-between rounded-lg px-2 py-2 hover:bg-surface-high">
                  <span>{formatFullDate(p.date)}</span>
                  <span className="text-muted">
                    {p.topSet && (timed ? `${p.topSet.reps} s` : `${formatWeight(p.topSet.weightKg, unit)} × ${p.topSet.reps}`)}
                    {!timed && loaded && ` · ${formatVolume(p.volumeKg, unit)}`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
        <Card>
          <CardTitle>Personal records</CardTitle>
          {prs.length ? (
            <ul className="divide-y divide-outline text-sm tabular-nums">
              {prs.map((r) => (
                <li key={r.sync_id} className="flex justify-between py-2">
                  <span>
                    {PR_LABELS[r.type]} <span className="text-muted">· {formatFullDate(r.achieved_at)}</span>
                  </span>
                  <span>{r.type === "REPS_AT_WEIGHT" ? `${r.reps} @ ${formatWeight(r.weight_kg, unit)}` : formatWeight(r.value, unit)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">No records yet.</p>
          )}
        </Card>
      </div>

      {exercise?.instructions && (
        <Card className="mt-4">
          <CardTitle>How to</CardTitle>
          <ul className="list-disc space-y-1 pl-5 text-sm">
            {exercise.instructions.split("\n").map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}
