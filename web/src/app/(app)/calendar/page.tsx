import clsx from "clsx";
import Link from "next/link";
import { Card, CardTitle, PageHeader } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getPlans, getProfile, getScheduled, getSessions } from "@/lib/data";
import { formatDateValue, formatDateValueMonth, formatTime } from "@/lib/dates";
import { formatVolume } from "@/lib/model";
import { dayKey, localDate, nextPlanDay, weekStart } from "@/lib/stats";
import { DayPlanner, PlanAhead, type PlanOption, type PlannedEntry } from "./day-planner";

const DAY_MS = 86_400_000;
const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
// Plan days get a stable colour by position, so "Upper" always looks the same.
const DAY_COLOURS = [
  "bg-primary text-on-primary",
  "bg-tertiary text-background",
  "bg-on-primary-container text-primary-container",
  "bg-muted text-background",
  "bg-primary-container text-on-primary-container",
];
// Outline versions for planned (not yet done) workouts.
const PLANNED_COLOURS = ["border-primary text-primary", "border-tertiary text-tertiary", "border-on-primary-container text-on-primary-container", "border-muted text-muted", "border-primary text-primary"];
/** Suggested training weekdays (0 = Monday) for the profile's days per week. */
const DEFAULT_WEEKDAYS: Record<number, number[]> = { 2: [0, 3], 3: [0, 2, 4], 4: [0, 1, 3, 4], 5: [0, 1, 2, 3, 4], 6: [0, 1, 2, 3, 4, 5] };

function parseMonth(value: string | undefined, today: number): number {
  const m = value?.match(/^(\d{4})-(\d{2})$/);
  if (m) return Date.UTC(Number(m[1]), Number(m[2]) - 1, 1);
  const d = new Date(today);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1);
}

function monthParam(dateMs: number, delta = 0): string {
  const d = new Date(dateMs);
  const t = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + delta, 1));
  return `${t.getUTCFullYear()}-${String(t.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** "2026-10-24" → date value (UTC midnight), or null. */
function parseDay(value: string | undefined): number | null {
  const m = value?.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return m ? Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : null;
}

export default async function CalendarPage({ searchParams }: { searchParams: Promise<{ month?: string; day?: string }> }) {
  const [{ month, day }, sessions, plans, scheduled, profile, catalog] = await Promise.all([
    searchParams,
    getSessions(),
    getPlans(),
    getScheduled(),
    getProfile(),
    getCatalog(),
  ]);
  const unit = profile?.units ?? "KG";
  const today = localDate(Date.now());
  const selected = parseDay(day);
  const first = parseMonth(month ?? (selected != null ? monthParam(selected) : undefined), today);
  const firstDate = new Date(first);
  const daysInMonth = new Date(Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth() + 1, 0)).getUTCDate();
  const gridStart = weekStart(first + 12 * 3_600_000); // noon avoids zone edge cases
  const cells = Math.ceil(((first - gridStart) / DAY_MS + daysInMonth) / 7) * 7;

  const byDay = Map.groupBy(sessions, (s) => dayKey(s.started_at));
  const finishedIds = new Set(sessions.filter((s) => s.ended_at != null).map((s) => s.sync_id));
  // A planned workout that was done shows as the workout itself, so only open ones are drawn.
  const open = scheduled.filter((s) => !(s.session_id && finishedIds.has(s.session_id)));
  const plannedByDay = Map.groupBy(open, (s) => new Date(s.epoch_day * DAY_MS).toISOString().slice(0, 10));

  const days = new Map(plans.flatMap((p) => p.days.map((d) => [d.sync_id, d] as const)));
  const colourIndex = (planDayId: string | null) => (planDayId != null && days.has(planDayId) ? days.get(planDayId)!.position % DAY_COLOURS.length : null);
  const colour = (planDayId: string | null) => {
    const i = colourIndex(planDayId);
    return i == null ? "bg-surface-high text-foreground" : DAY_COLOURS[i];
  };
  const plannedColour = (planDayId: string | null) => {
    const i = colourIndex(planDayId);
    return i == null ? "border-outline text-muted" : PLANNED_COLOURS[i];
  };
  const titleOf = (planDayId: string | null) => (planDayId && days.get(planDayId)?.name) || "Free workout";

  const monthSessions = sessions.filter((s) => {
    const d = localDate(s.started_at);
    return d >= first && d < first + daysInMonth * DAY_MS && s.ended_at != null;
  });
  const activePlan = plans.find((p) => p.is_active);
  const nextDay = activePlan ? nextPlanDay(activePlan.days, sessions) : null;
  const nextPlanned = open.find((s) => s.epoch_day * DAY_MS >= today);
  const planOptions: PlanOption[] = plans.map((p) => ({ name: p.name, active: p.is_active, days: p.days.map((d) => ({ id: d.sync_id, name: d.name })) }));
  const selectedKey = selected != null ? new Date(selected).toISOString().slice(0, 10) : null;
  const navLink = "rounded-full border border-outline px-3 py-1.5 text-sm hover:bg-surface-high";

  return (
    <>
      <PageHeader
        title={formatDateValueMonth(first)}
        subtitle={`${monthSessions.length} workouts · ${formatVolume(monthSessions.reduce((s, x) => s + Number(x.volume_kg), 0), unit)} · click a day to plan a workout`}
        actions={
          <>
            <Link className={navLink} href={`/calendar?month=${monthParam(first, -1)}`} aria-label="Previous month">←</Link>
            <Link className={navLink} href="/calendar">Today</Link>
            <Link className={navLink} href={`/calendar?month=${monthParam(first, 1)}`} aria-label="Next month">→</Link>
          </>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[1fr_300px]">
        <Card className="p-2 sm:p-4">
          <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted">
            {WEEKDAYS.map((d) => (
              <div key={d} className="py-1">{d}</div>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-1">
            {Array.from({ length: cells }, (_, i) => {
              const date = gridStart + i * DAY_MS;
              const inMonth = date >= first && date < first + daysInMonth * DAY_MS;
              const key = new Date(date).toISOString().slice(0, 10);
              const done = byDay.get(key) ?? [];
              const planned = plannedByDay.get(key) ?? [];
              const past = date < today;
              return (
                <div
                  key={key}
                  className={clsx(
                    "relative min-h-20 rounded-lg border p-1 text-left sm:min-h-24 sm:p-1.5",
                    inMonth ? "border-outline" : "border-transparent opacity-40",
                    date === today && "ring-2 ring-primary",
                    key === selectedKey && "bg-surface-high",
                  )}
                >
                  {/* The whole cell selects the day; workouts inside stay their own links above it. */}
                  <Link href={`/calendar?month=${monthParam(first)}&day=${key}`} className="absolute inset-0 rounded-lg" aria-label={`Plan ${formatDateValue(date)}`} />
                  <div className={clsx("relative text-xs", date === today ? "font-semibold text-primary" : "text-muted")}>{new Date(date).getUTCDate()}</div>
                  <div className="relative mt-1 space-y-1">
                    {done.map((s) => (
                      <Link
                        key={s.sync_id}
                        href={`/history/${s.sync_id}`}
                        title={`${s.plan_day_name ?? "Free workout"} · ${formatTime(s.started_at)}`}
                        className={clsx("block truncate rounded px-1 py-0.5 text-[11px] leading-tight font-medium hover:opacity-80 sm:text-xs", colour(s.plan_day_id))}
                      >
                        {s.plan_day_name ?? "Workout"}
                      </Link>
                    ))}
                    {planned.map((s) => (
                      <div
                        key={s.sync_id}
                        title={past ? `Missed: ${titleOf(s.plan_day_id)}` : `Planned: ${titleOf(s.plan_day_id)}${s.notes ? ` · ${s.notes}` : ""}`}
                        className={clsx(
                          "pointer-events-none truncate rounded border border-dashed px-1 py-0.5 text-[11px] leading-tight sm:text-xs",
                          past ? "border-outline text-muted line-through" : plannedColour(s.plan_day_id),
                        )}
                      >
                        {titleOf(s.plan_day_id)}
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-4 px-1 text-xs text-muted">
            <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm bg-primary" /> Done</span>
            <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm border border-dashed border-primary" /> Planned</span>
            <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm border border-dashed border-outline" /> <span className="line-through">Missed</span></span>
          </div>
        </Card>

        <div className="space-y-4">
          {selected != null && (
            <Card>
              <CardTitle action={<Link href={`/calendar?month=${monthParam(first)}`} className="text-sm text-muted hover:text-foreground">Close</Link>}>
                {selected === today ? "Today" : formatDateValue(selected)}
              </CardTitle>
              {(byDay.get(selectedKey!) ?? []).map((s) => (
                <Link key={s.sync_id} href={`/history/${s.sync_id}`} className={clsx("mb-2 block rounded-lg px-3 py-2 text-sm font-medium", colour(s.plan_day_id))}>
                  {s.plan_day_name ?? "Workout"} · {formatTime(s.started_at)}
                </Link>
              ))}
              {selected >= today ? (
                <DayPlanner
                  key={selectedKey}
                  epochDay={selected / DAY_MS}
                  planned={(plannedByDay.get(selectedKey!) ?? []).map((s): PlannedEntry => ({ syncId: s.sync_id, title: titleOf(s.plan_day_id), notes: s.notes }))}
                  plans={planOptions}
                  defaultDayId={nextDay?.sync_id ?? null}
                />
              ) : (
                <p className="text-sm text-muted">
                  {(plannedByDay.get(selectedKey!) ?? []).length ? "Planned but not done." : (byDay.get(selectedKey!) ?? []).length ? "" : "Nothing logged this day."}
                </p>
              )}
            </Card>
          )}

          <Card>
            <CardTitle>Up next</CardTitle>
            {nextPlanned ? (
              <>
                <div className="text-xs text-muted">Planned for {nextPlanned.epoch_day * DAY_MS === today ? "today" : formatDateValue(nextPlanned.epoch_day * DAY_MS)}</div>
                <div className={clsx("mt-1 inline-block rounded border border-dashed px-2 py-0.5 text-sm font-medium", plannedColour(nextPlanned.plan_day_id))}>
                  {titleOf(nextPlanned.plan_day_id)}
                </div>
                {nextPlanned.notes && <p className="mt-2 text-sm text-muted">{nextPlanned.notes}</p>}
              </>
            ) : activePlan && nextDay ? (
              <>
                <div className={clsx("inline-block rounded px-2 py-0.5 text-sm font-medium", colour(nextDay.sync_id))}>{nextDay.name}</div>
                <ul className="mt-3 space-y-1 text-sm text-muted">
                  {nextDay.exercises.map((e) => (
                    <li key={e.sync_id} className="truncate">{exerciseName(catalog, e.exercise_id)}</li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="text-sm text-muted">No active plan.</p>
            )}
          </Card>

          {activePlan && (
            <Card>
              <CardTitle>Plan weeks ahead</CardTitle>
              <p className="-mt-1 mb-3 text-xs text-muted">
                Puts {activePlan.name}&apos;s days on these weekdays in order, starting with {nextDay?.name ?? "the next day"}. Days that already have a workout are skipped.
              </p>
              <PlanAhead defaultWeekdays={DEFAULT_WEEKDAYS[profile?.days_per_week ?? 3] ?? [0, 2, 4]} />
              <ul className="mt-4 space-y-1.5 border-t border-outline pt-3 text-sm">
                {activePlan.days.map((d) => (
                  <li key={d.sync_id} className="flex items-center gap-2">
                    <span className={clsx("h-3 w-3 rounded-sm", colour(d.sync_id).split(" ")[0])} />
                    {d.name}
                  </li>
                ))}
              </ul>
            </Card>
          )}
          <p className="px-1 text-xs text-muted">Planned workouts show on the app&apos;s Today tab after its next update.</p>
        </div>
      </div>
    </>
  );
}
