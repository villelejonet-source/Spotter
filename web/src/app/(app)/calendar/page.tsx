import clsx from "clsx";
import Link from "next/link";
import { Card, CardTitle, IconLink, LinkButton, OutlinePill, PageHeader } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { groupBy } from "@/lib/collections";
import { getActivePlan, getCatalog, getPlanOutlines, getProfile, getScheduled, getSessions } from "@/lib/data";
import { formatDateValue, formatDateValueMonth, formatTime, weekdayLabels } from "@/lib/dates";
import { FREE_WORKOUT, WORKOUT } from "@/lib/labels";
import { formatVolume } from "@/lib/model";
import { DAY_MS, dateValueKey, dayKey, localDate, nextPlanDay, weekStart, windowStart } from "@/lib/stats";
import { DayPlanner, PlanAhead, type PlanOption, type PlannedEntry } from "./day-planner";

export const metadata = { title: "Calendar" };

// Plan days get a stable colour by position, so "Upper" always looks the same.
const DAY_COLOURS = ["bg-primary text-on-primary", "bg-tertiary text-on-tertiary", "bg-on-primary-container text-primary-container", "bg-muted text-background", "bg-primary-container text-on-primary-container"];
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

/** One month of workouts, done and planned, loading only the days on screen. */
export default async function CalendarPage({ searchParams }: { searchParams: Promise<{ month?: string; day?: string }> }) {
  const { month, day } = await searchParams;
  const today = localDate(Date.now());
  const selected = parseDay(day);
  const first = parseMonth(month ?? (selected != null ? monthParam(selected) : undefined), today);
  const firstDate = new Date(first);
  const daysInMonth = new Date(Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth() + 1, 0)).getUTCDate();
  const gridStart = weekStart(first + 12 * 3_600_000); // noon avoids zone edge cases
  const cells = Math.ceil(((first - gridStart) / DAY_MS + daysInMonth) / 7) * 7;
  const gridEnd = gridStart + cells * DAY_MS;

  // The grid's days as a started_at window, padded a day each side for the zone offset
  // and narrowed exactly below; the plan rotation looks back 12 weeks.
  // Plan and day names label the cells; only the active plan's exercises are loaded.
  // A planned workout already started in the app shows as the workout itself, so only open ones are fetched.
  const [sessions, recent, scheduled, [nextPlanned], plans, activePlan, profile, catalog] = await Promise.all([
    getSessions({ since: gridStart - DAY_MS, until: gridEnd + DAY_MS }),
    getSessions({ since: windowStart(12) }),
    getScheduled({ fromDay: gridStart / DAY_MS, toDay: gridEnd / DAY_MS - 1, open: true }),
    getScheduled({ fromDay: today / DAY_MS, open: true, limit: 1 }),
    getPlanOutlines(),
    getActivePlan(),
    getProfile(),
    getCatalog(),
  ]);
  const unit = profile?.units ?? "KG";
  const inGrid = sessions.filter((s) => {
    const d = localDate(s.started_at);
    return d >= gridStart && d < gridEnd;
  });
  const byDay = groupBy(inGrid, (s) => dayKey(s.started_at));
  const plannedByDay = groupBy(scheduled, (s) => dateValueKey(s.epoch_day * DAY_MS));

  const days = new Map(plans.flatMap((p) => p.days.map((d) => [d.sync_id, d] as const)));
  const colourIndex = (planDayId: string | null) => (planDayId != null && days.has(planDayId) ? days.get(planDayId)!.position % DAY_COLOURS.length : null);
  const colour = (planDayId: string | null) => {
    const i = colourIndex(planDayId);
    return i == null ? "bg-surface-high text-foreground" : DAY_COLOURS[i];
  };
  const plannedColour = (planDayId: string | null) => {
    const i = colourIndex(planDayId);
    return i == null ? "border-outline-strong text-muted" : PLANNED_COLOURS[i];
  };
  const titleOf = (planDayId: string | null) => (planDayId && days.get(planDayId)?.name) || FREE_WORKOUT;

  const monthSessions = inGrid.filter((s) => {
    const d = localDate(s.started_at);
    return d >= first && d < first + daysInMonth * DAY_MS && s.ended_at != null;
  });
  // The month as a list, for phones, where the grid only has room for dots.
  const monthEntries = Array.from({ length: daysInMonth }, (_, d) => first + d * DAY_MS).flatMap((date) => {
    const key = dateValueKey(date);
    return [
      ...(byDay.get(key) ?? []).map((entry) => ({ date, done: true as const, entry })),
      ...(plannedByDay.get(key) ?? []).map((entry) => ({ date, done: false as const, entry })),
    ];
  });
  const nextDay = activePlan ? nextPlanDay(activePlan.days, recent) : null;
  const planOptions: PlanOption[] = plans.map((p) => ({ name: p.name, active: p.is_active, days: p.days.map((d) => ({ id: d.sync_id, name: d.name })) }));
  const selectedKey = selected != null ? dateValueKey(selected) : null;
  const weekdays = weekdayLabels();

  return (
    <>
      <PageHeader
        title={formatDateValueMonth(first)}
        subtitle={`${monthSessions.length} workouts · ${formatVolume(monthSessions.reduce((s, x) => s + Number(x.volume_kg), 0), unit)} · pick a day to plan a workout`}
        actions={
          <>
            <IconLink label="Previous month" href={`/calendar?month=${monthParam(first, -1)}`}>
              ←
            </IconLink>
            <LinkButton href="/calendar">Today</LinkButton>
            <IconLink label="Next month" href={`/calendar?month=${monthParam(first, 1)}`}>
              →
            </IconLink>
          </>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[1fr_300px]">
        <Card className="p-2 sm:p-4">
          <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted" aria-hidden="true">
            {weekdays.map((d) => (
              <div key={d} className="py-1">
                {d}
              </div>
            ))}
          </div>
          <ul className="grid grid-cols-7 gap-1" aria-label={`Days of ${formatDateValueMonth(first)}`}>
            {Array.from({ length: cells }, (_, i) => {
              const date = gridStart + i * DAY_MS;
              const inMonth = date >= first && date < first + daysInMonth * DAY_MS;
              const key = dateValueKey(date);
              const done = byDay.get(key) ?? [];
              const planned = plannedByDay.get(key) ?? [];
              const past = date < today;
              const summary = [
                date === today ? "today" : null,
                done.length ? `${done.length} workout${done.length > 1 ? "s" : ""}: ${done.map((s) => s.plan_day_name ?? WORKOUT).join(", ")}` : null,
                planned.length ? `${past ? "missed" : "planned"}: ${planned.map((s) => titleOf(s.plan_day_id)).join(", ")}` : null,
              ]
                .filter(Boolean)
                .join(" · ");
              return (
                <li
                  key={key}
                  className={clsx(
                    "relative min-h-12 rounded-lg border p-1 text-left sm:min-h-24 sm:p-1.5",
                    inMonth ? "border-outline" : "border-transparent opacity-40",
                    date === today && "ring-2 ring-primary",
                    key === selectedKey && "bg-surface-high",
                  )}
                >
                  {/* The whole cell selects the day; workouts inside stay their own links above it. */}
                  <Link
                    href={`/calendar?month=${monthParam(first)}&day=${key}`}
                    className="absolute inset-0 rounded-lg"
                    aria-label={`${formatDateValue(date)}${summary ? ` · ${summary}` : ""}`}
                    aria-current={key === selectedKey ? "date" : undefined}
                  />
                  <div className={clsx("relative text-xs", date === today ? "font-semibold text-primary" : "text-muted")} aria-hidden="true">
                    {new Date(date).getUTCDate()}
                  </div>
                  {/* Phones: a dot per workout (full names in "This month" below); wider screens: the names. */}
                  <div className="relative mt-1 flex flex-wrap gap-1 sm:hidden" aria-hidden="true">
                    {done.map((s) => (
                      <span key={s.sync_id} className={clsx("h-2 w-2 rounded-full", colour(s.plan_day_id).split(" ")[0])} />
                    ))}
                    {planned.map((s) => (
                      <span key={s.sync_id} className={clsx("h-2 w-2 rounded-full border border-dashed", past ? "border-outline-strong" : plannedColour(s.plan_day_id).split(" ")[0])} />
                    ))}
                  </div>
                  <div className="relative mt-1 hidden space-y-1 sm:block">
                    {done.map((s) => (
                      <Link
                        key={s.sync_id}
                        href={`/history/${s.sync_id}`}
                        title={`${s.plan_day_name ?? WORKOUT} · ${formatTime(s.started_at)}`}
                        className={clsx("block truncate rounded px-1 py-0.5 text-[11px] leading-tight font-medium hover:opacity-80 sm:text-xs", colour(s.plan_day_id))}
                      >
                        {s.plan_day_name ?? WORKOUT}
                      </Link>
                    ))}
                    {planned.map((s) => (
                      <div
                        key={s.sync_id}
                        title={past ? `Missed: ${titleOf(s.plan_day_id)}` : `Planned: ${titleOf(s.plan_day_id)}${s.notes ? ` · ${s.notes}` : ""}`}
                        className={clsx(
                          "pointer-events-none truncate rounded border border-dashed px-1 py-0.5 text-[11px] leading-tight sm:text-xs",
                          past ? "border-outline-strong text-muted line-through" : plannedColour(s.plan_day_id),
                        )}
                      >
                        {titleOf(s.plan_day_id)}
                      </div>
                    ))}
                  </div>
                </li>
              );
            })}
          </ul>
          <div className="mt-3 flex flex-wrap items-center gap-4 px-1 text-xs text-muted">
            <span className="flex items-center gap-1.5">
              <span className="h-3 w-3 rounded-sm bg-primary" /> Done
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-3 w-3 rounded-sm border border-dashed border-primary" /> Planned
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-3 w-3 rounded-sm border border-dashed border-outline-strong" /> <span className="line-through">Missed</span>
            </span>
          </div>
        </Card>

        <div className="space-y-4">
          {selected != null && (
            <Card>
              <CardTitle
                action={
                  <Link href={`/calendar?month=${monthParam(first)}`} className="tap rounded-md text-sm text-muted hover:text-foreground">
                    Close
                  </Link>
                }
              >
                {selected === today ? "Today" : formatDateValue(selected)}
              </CardTitle>
              {(byDay.get(selectedKey!) ?? []).map((s) => (
                <Link key={s.sync_id} href={`/history/${s.sync_id}`} className={clsx("mb-2 block rounded-lg px-3 py-2 text-sm font-medium", colour(s.plan_day_id))}>
                  {s.plan_day_name ?? WORKOUT} · {formatTime(s.started_at)}
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

          <Card className="sm:hidden">
            <CardTitle>This month</CardTitle>
            {monthEntries.length ? (
              <ul className="-my-1 divide-y divide-outline text-sm">
                {monthEntries.map(({ date, done, entry }) => (
                  <li key={entry.sync_id}>
                    <Link
                      href={done ? `/history/${entry.sync_id}` : `/calendar?month=${monthParam(first)}&day=${dateValueKey(date)}`}
                      className="-mx-2 flex min-h-11 items-center gap-3 rounded-lg px-2 py-2 hover:bg-surface"
                    >
                      <span
                        className={clsx(
                          "h-2.5 w-2.5 shrink-0 rounded-full",
                          done ? colour(entry.plan_day_id).split(" ")[0] : clsx("border border-dashed", date < today ? "border-outline-strong" : plannedColour(entry.plan_day_id).split(" ")[0]),
                        )}
                        aria-hidden="true"
                      />
                      <span className={clsx("min-w-0 flex-1 truncate font-medium", !done && date < today && "text-muted line-through")}>
                        {done ? (entry.plan_day_name ?? WORKOUT) : titleOf(entry.plan_day_id)}
                      </span>
                      <span className="shrink-0 text-xs text-muted">
                        {done ? "" : date < today ? "Missed · " : "Planned · "}
                        {formatDateValue(date)}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted">Nothing done or planned this month. Pick a day to plan a workout.</p>
            )}
          </Card>

          <Card>
            <CardTitle>Next workout</CardTitle>
            {nextPlanned ? (
              <>
                <div className="text-xs text-muted">{nextPlanned.epoch_day * DAY_MS === today ? "Scheduled today" : `Planned for ${formatDateValue(nextPlanned.epoch_day * DAY_MS)}`}</div>
                <div className="mt-1">
                  <OutlinePill className={plannedColour(nextPlanned.plan_day_id)}>{titleOf(nextPlanned.plan_day_id)}</OutlinePill>
                </div>
                {nextPlanned.notes && <p className="mt-2 text-sm text-muted">{nextPlanned.notes}</p>}
              </>
            ) : activePlan && nextDay ? (
              <>
                <span className={clsx("inline-block rounded px-2 py-0.5 text-sm font-medium", colour(nextDay.sync_id))}>{nextDay.name}</span>
                <ul className="mt-3 space-y-1 text-sm text-muted">
                  {nextDay.exercises.map((e) => (
                    <li key={e.sync_id} className="truncate">
                      {exerciseName(catalog, e.exercise_id)}
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="text-sm text-muted">
                No plan yet.{" "}
                <Link className="tap rounded-md text-primary" href="/plans/new">
                  Create a plan
                </Link>
              </p>
            )}
          </Card>

          {activePlan && (
            <Card>
              <CardTitle>Plan weeks ahead</CardTitle>
              <p className="-mt-1 mb-3 text-xs text-muted">
                Puts {activePlan.name}&apos;s days on these weekdays in order, starting with {nextDay?.name ?? "the next day"}. Days that already have a workout are
                skipped.
              </p>
              <PlanAhead defaultWeekdays={DEFAULT_WEEKDAYS[profile?.days_per_week ?? 3] ?? [0, 2, 4]} weekdays={weekdays} />
              <ul className="mt-4 space-y-1.5 border-t border-outline pt-3 text-sm">
                {activePlan.days.map((d) => (
                  <li key={d.sync_id} className="flex items-center gap-2">
                    <span className={clsx("h-3 w-3 rounded-sm", colour(d.sync_id).split(" ")[0])} aria-hidden="true" />
                    {d.name}
                  </li>
                ))}
              </ul>
            </Card>
          )}
          <p className="px-1 text-xs text-muted">Planned workouts show on the app&apos;s Today tab after its next sync.</p>
        </div>
      </div>
    </>
  );
}
