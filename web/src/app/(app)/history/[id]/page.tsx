import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, Card, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getSessionDetail, getUnit } from "@/lib/data";
import { formatFullDate, formatTime } from "@/lib/dates";
import { enumLabel, formatDuration, formatSet, formatVolume } from "@/lib/model";

export default async function SessionPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [detail, catalog, unit] = await Promise.all([getSessionDetail(id), getCatalog(), getUnit()]);
  if (!detail) notFound();
  const { session, exercises } = detail;

  return (
    <>
      <Link href="/history" className="text-sm text-muted hover:text-foreground">← History</Link>
      <div className="mt-2">
        <PageHeader
          title={session.plan_day_name ?? "Free workout"}
          subtitle={`${formatFullDate(session.started_at)} · ${formatTime(session.started_at)}`}
        />
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Duration" value={session.ended_at ? formatDuration(session.ended_at - session.started_at) : "Running"} />
        <Stat label="Volume" value={formatVolume(session.volume_kg, unit)} />
        <Stat label="Sets" value={session.completed_sets} />
        <Stat label="Exercises" value={session.exercise_count} />
      </div>

      {session.notes && <Card className="mt-4 text-sm whitespace-pre-line">{session.notes}</Card>}

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {exercises.map((e) => (
          <Card key={e.sync_id}>
            <div className="mb-3 flex items-start justify-between gap-2">
              <div>
                <Link href={`/progress/${e.exercise_id}`} className="font-medium hover:text-primary">
                  {exerciseName(catalog, e.exercise_id)}
                </Link>
                {e.substituted_from_exercise_id && (
                  <div className="text-xs text-muted">Swapped from {exerciseName(catalog, e.substituted_from_exercise_id)}</div>
                )}
              </div>
              <div className="flex gap-1">
                {e.superset_group != null && <Badge>Superset {e.superset_group}</Badge>}
                {e.progression_reason && <Badge tone="primary">{enumLabel(e.progression_reason)}</Badge>}
              </div>
            </div>
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-muted">
                <tr>
                  <th className="pb-1 font-normal">Set</th>
                  <th className="pb-1 font-normal">Result</th>
                  <th className="pb-1 text-right font-normal">RIR / RPE</th>
                </tr>
              </thead>
              <tbody className="tabular-nums">
                {e.sets.map((s) => (
                  <tr key={s.sync_id} className={s.completed_at == null ? "text-muted line-through" : ""}>
                    <td className="py-1">
                      {s.set_type === "WORKING" ? e.sets.filter((x) => x.set_type === "WORKING").indexOf(s) + 1 : <Badge>{enumLabel(s.set_type)}</Badge>}
                    </td>
                    <td className="py-1">{formatSet(s, e.exercise_id, unit)}</td>
                    <td className="py-1 text-right text-muted">{s.rir != null ? `RIR ${s.rir}` : s.rpe != null ? `RPE ${s.rpe}` : "–"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {e.notes && <p className="mt-2 text-xs text-muted whitespace-pre-line">{e.notes}</p>}
          </Card>
        ))}
      </div>
    </>
  );
}
