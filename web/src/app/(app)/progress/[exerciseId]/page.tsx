import Link from "next/link";
import { notFound } from "next/navigation";
import { LineChartView } from "@/components/charts";
import { BackLink, Badge, Card, CardTitle, EmptyState, PageHeader, Stat } from "@/components/ui";
import { getCatalog, getPersonalRecords, getSetHistory, getUnit } from "@/lib/data";
import { formatDayMonth, formatFullDate } from "@/lib/dates";
import { BODYWEIGHT, EQUIPMENT_LABELS, label, MUSCLE_LABELS, PR_LABELS } from "@/lib/labels";
import { formatRecord, formatSet, formatVolume, formatWeight, isTimed, toUnit } from "@/lib/model";
import { exerciseProgress } from "@/lib/stats";

export const metadata = { title: "Exercise" };

export default async function ExerciseProgressPage({ params }: { params: Promise<{ exerciseId: string }> }) {
  const { exerciseId } = await params;
  const id = decodeURIComponent(exerciseId);
  const [mine, prs, catalog, unit] = await Promise.all([getSetHistory(id), getPersonalRecords({ exerciseId: id }), getCatalog(), getUnit()]);
  const exercise = catalog.get(id);
  if (!exercise && !mine.length) notFound();

  const sessionDates = new Map(mine.map((s) => [s.session_id, s.session_started_at]));
  const points = exerciseProgress(mine, id, sessionDates);
  const timed = isTimed(id);
  const loaded = points.some((p) => p.e1rmKg != null);
  const latest = points[points.length - 1];
  const u = unit.toLowerCase();
  const topSet = (p: (typeof points)[number]) => (p.topSet ? formatSet({ weight_kg: p.topSet.weightKg, reps: p.topSet.reps }, id, unit) : "–");

  return (
    <>
      <BackLink href="/progress">Progress</BackLink>
      <PageHeader
        title={exercise?.name ?? id}
        subtitle={
          exercise && (
            <span className="flex flex-wrap gap-1">
              {exercise.primary.map((m) => (
                <Badge key={m} tone="primary">
                  {label(MUSCLE_LABELS, m)}
                </Badge>
              ))}
              {exercise.equipment.length ? exercise.equipment.map((e) => <Badge key={e}>{label(EQUIPMENT_LABELS, e)}</Badge>) : <Badge>{BODYWEIGHT}</Badge>}
            </span>
          )
        }
      />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Sessions" value={points.length} />
        {timed ? (
          <Stat label="Longest hold" value={points.length ? `${Math.max(...points.map((p) => p.bestReps))} s` : "–"} />
        ) : (
          <Stat label="Best est. 1RM" value={loaded ? formatWeight(Math.max(...points.map((p) => p.e1rmKg ?? 0)), unit) : "–"} />
        )}
        <Stat label="Last top set" value={latest ? topSet(latest) : "–"} />
        <Stat label="PRs" value={prs.length} />
      </div>

      {!points.length ? (
        <div className="mt-4">
          <EmptyState title="No sets logged for this exercise yet">Add it to a plan, or log it in the app, and its progress shows here.</EmptyState>
        </div>
      ) : (
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          {loaded && (
            <Card>
              <CardTitle>Est. 1RM ({u})</CardTitle>
              <LineChartView unit={u} data={points.map((p) => ({ label: formatDayMonth(p.date), value: p.e1rmKg != null ? Number(toUnit(p.e1rmKg, unit).toFixed(1)) : null }))} />
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
      )}

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {points.length > 0 && (
          <Card>
            <CardTitle>Sessions</CardTitle>
            <ul className="divide-y divide-outline text-sm tabular-nums">
              {[...points].reverse().map((p) => (
                <li key={p.sessionId}>
                  <Link href={`/history/${p.sessionId}`} className="-mx-2 flex justify-between gap-3 rounded-lg px-2 py-2 hover:bg-surface">
                    <span>{formatFullDate(p.date)}</span>
                    <span className="text-right text-muted">
                      {topSet(p)}
                      {!timed && loaded && ` · ${formatVolume(p.volumeKg, unit)}`}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        )}
        <Card>
          <CardTitle>Personal records</CardTitle>
          {prs.length ? (
            <ul className="divide-y divide-outline text-sm tabular-nums">
              {prs.map((r) => (
                <li key={r.sync_id} className="flex justify-between gap-3 py-2">
                  <span>
                    {PR_LABELS[r.type]} <span className="text-muted">· {formatFullDate(r.achieved_at)}</span>
                  </span>
                  <span>{formatRecord(r, unit)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">PRs show up from the second time you log an exercise.</p>
          )}
        </Card>
      </div>

      {exercise && (
        <Card className="mt-4">
          <CardTitle>About</CardTitle>
          <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[auto_1fr]">
            <dt className="text-muted">Main muscles</dt>
            <dd>{exercise.primary.map((m) => label(MUSCLE_LABELS, m)).join(", ")}</dd>
            {exercise.secondary.length > 0 && (
              <>
                <dt className="text-muted">Also works</dt>
                <dd>{exercise.secondary.map((m) => label(MUSCLE_LABELS, m)).join(", ")}</dd>
              </>
            )}
            <dt className="text-muted">Equipment</dt>
            <dd>{exercise.equipment.length ? exercise.equipment.map((e) => label(EQUIPMENT_LABELS, e)).join(", ") : `None (${BODYWEIGHT.toLowerCase()})`}</dd>
          </dl>
          {exercise.instructions && (
            <>
              <h3 className="mt-4 mb-2 text-sm font-medium text-muted">How to</h3>
              <ul className="list-disc space-y-1 pl-5 text-sm">
                {exercise.instructions
                  .split("\n")
                  .filter((line) => line.trim())
                  .map((line, i) => (
                    <li key={i}>{line}</li>
                  ))}
              </ul>
            </>
          )}
        </Card>
      )}
    </>
  );
}
