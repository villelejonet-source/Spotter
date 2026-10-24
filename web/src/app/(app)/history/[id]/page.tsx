import Link from "next/link";
import { notFound } from "next/navigation";
import { BackLink, Badge, Card, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getSessionDetail, getUnit } from "@/lib/data";
import { formatFullDate, formatTime } from "@/lib/dates";
import { EFFORT_LABEL, LIGHTER_WEEK, progressionReasonLabel, SET_TYPE_LABELS, SET_TYPE_SHORT, WORKOUT } from "@/lib/labels";
import { formatDuration, formatSet, formatSetEffort, formatVolume, isTimed } from "@/lib/model";

export const metadata = { title: "Workout" };

export default async function SessionPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [detail, catalog, unit] = await Promise.all([getSessionDetail(id), getCatalog(), getUnit()]);
  if (!detail) notFound();
  const { session, exercises } = detail;

  return (
    <>
      <BackLink href="/history">History</BackLink>
      <PageHeader
        title={session.plan_day_name ?? WORKOUT}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            {formatFullDate(session.started_at)} · {formatTime(session.started_at)}
            {session.is_deload && <Badge tone="tertiary">{LIGHTER_WEEK}</Badge>}
          </span>
        }
      />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Duration" value={session.ended_at ? formatDuration(session.ended_at - session.started_at) : "In progress"} />
        <Stat label="Volume" value={formatVolume(session.volume_kg, unit)} />
        <Stat label="Sets" value={session.completed_sets} />
        <Stat label="Exercises" value={session.exercise_count} />
      </div>

      {session.notes && <Card className="mt-4 text-sm whitespace-pre-line">{session.notes}</Card>}

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {exercises.map((e) => {
          // Working sets are numbered 1, 2, 3…; warm-ups, drop sets and sets to failure show their marker instead.
          let working = 0;
          return (
            <Card key={e.sync_id}>
              <div className="mb-3 flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <Link href={`/progress/${e.exercise_id}`} className="rounded-md font-medium hover:text-primary">
                    {exerciseName(catalog, e.exercise_id)}
                  </Link>
                  {e.substituted_from_exercise_id && <div className="text-xs text-muted">Swapped from {exerciseName(catalog, e.substituted_from_exercise_id)}</div>}
                </div>
                {e.superset_group != null && <Badge>Superset</Badge>}
              </div>
              {e.progression_reason && <p className="mb-3 text-xs text-primary">{progressionReasonLabel(e.progression_reason, isTimed(e.exercise_id))}</p>}
              <table className="w-full text-sm">
                <thead className="text-left text-xs text-muted">
                  <tr>
                    <th scope="col" className="w-12 pb-1 font-normal">
                      Set
                    </th>
                    <th scope="col" className="pb-1 font-normal">
                      {isTimed(e.exercise_id) ? "Hold" : "Result"}
                    </th>
                    <th scope="col" className="pb-1 text-right font-normal">
                      {EFFORT_LABEL}
                    </th>
                  </tr>
                </thead>
                <tbody className="tabular-nums">
                  {e.sets.map((s) => {
                    const marker = s.set_type === "WORKING" ? String(++working) : SET_TYPE_SHORT[s.set_type];
                    return (
                      <tr key={s.sync_id} className={s.completed_at == null ? "text-muted line-through" : ""}>
                        <td className="py-1">
                          {s.set_type === "WORKING" ? marker : <abbr title={SET_TYPE_LABELS[s.set_type]} className="no-underline"><Badge>{marker}</Badge></abbr>}
                        </td>
                        <td className="py-1">{formatSet(s, e.exercise_id, unit)}</td>
                        <td className="py-1 text-right text-muted">{formatSetEffort(s) ?? "–"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {e.notes && <p className="mt-2 text-xs text-muted whitespace-pre-line">{e.notes}</p>}
            </Card>
          );
        })}
      </div>
    </>
  );
}
