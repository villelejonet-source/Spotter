"use client";

import { useState, useTransition } from "react";
import { planWeeks, removeScheduled, scheduleWorkout } from "@/app/actions/schedule";
import { Button, inputClass } from "@/components/ui";

export interface PlanOption {
  name: string;
  active: boolean;
  days: { id: string; name: string }[];
}

export interface PlannedEntry {
  syncId: string;
  title: string;
  notes: string | null;
}

/** Plans workouts on one day (today or later) and removes planned ones. */
export function DayPlanner({ epochDay, planned, plans, defaultDayId }: { epochDay: number; planned: PlannedEntry[]; plans: PlanOption[]; defaultDayId: string | null }) {
  const [dayId, setDayId] = useState(defaultDayId ?? "");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  function add(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    start(async () => {
      const result = await scheduleWorkout({ epoch_day: epochDay, plan_day_id: dayId || null, notes: notes.trim() || null });
      if (!result.ok) setError(result.error);
      else setNotes("");
    });
  }

  function remove(syncId: string) {
    setError(null);
    start(async () => {
      const result = await removeScheduled(syncId);
      if (!result.ok) setError(result.error);
    });
  }

  return (
    <div className="space-y-3">
      {planned.length > 0 && (
        <ul className="space-y-2">
          {planned.map((p) => (
            <li key={p.syncId} className="flex items-start justify-between gap-2 rounded-lg border border-dashed border-outline px-3 py-2 text-sm">
              <span className="min-w-0">
                <span className="block font-medium">{p.title}</span>
                {p.notes && <span className="block text-xs text-muted">{p.notes}</span>}
              </span>
              <button className="text-muted hover:text-error" title="Remove" aria-label={`Remove ${p.title}`} disabled={pending} onClick={() => remove(p.syncId)}>
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={add} className="space-y-2">
        <select className={inputClass} value={dayId} onChange={(e) => setDayId(e.target.value)} aria-label="Workout">
          {plans.map((plan) => (
            <optgroup key={plan.name} label={plan.active ? `${plan.name} (active)` : plan.name}>
              {plan.days.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </optgroup>
          ))}
          <option value="">Free workout</option>
        </select>
        <input className={inputClass} placeholder="Note (optional)" maxLength={200} value={notes} onChange={(e) => setNotes(e.target.value)} />
        <Button variant="primary" className="w-full" disabled={pending}>{pending ? "Saving…" : "Plan this workout"}</Button>
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
      </form>
    </div>
  );
}

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** Fills chosen weekdays for 1–4 weeks with the active plan's days, in order. */
export function PlanAhead({ defaultWeekdays }: { defaultWeekdays: number[] }) {
  const [weekdays, setWeekdays] = useState(defaultWeekdays);
  const [weeks, setWeeks] = useState(2);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);
  const [pending, start] = useTransition();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    start(async () => {
      const result = await planWeeks({ weekdays, weeks });
      if (!result.ok) setMessage({ tone: "error", text: result.error });
      else setMessage({ tone: "ok", text: result.added ? `Planned ${result.added} workouts.` : "Those days are already planned." });
    });
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <div className="flex flex-wrap gap-1">
        {WEEKDAYS.map((label, i) => {
          const on = weekdays.includes(i);
          return (
            <button
              key={label}
              type="button"
              aria-pressed={on}
              onClick={() => setWeekdays(on ? weekdays.filter((d) => d !== i) : [...weekdays, i].sort())}
              className={on ? "rounded-full bg-primary-container px-2.5 py-1 text-xs text-on-primary-container" : "rounded-full bg-surface-high px-2.5 py-1 text-xs text-muted"}
            >
              {label}
            </button>
          );
        })}
      </div>
      <select className={inputClass} value={weeks} onChange={(e) => setWeeks(Number(e.target.value))} aria-label="How many weeks">
        {[1, 2, 3, 4].map((w) => (
          <option key={w} value={w}>{w === 1 ? "Next 7 days" : `Next ${w} weeks`}</option>
        ))}
      </select>
      <Button className="w-full" disabled={pending || !weekdays.length}>{pending ? "Planning…" : "Plan weeks ahead"}</Button>
      {message && <p role={message.tone === "error" ? "alert" : "status"} className={message.tone === "error" ? "text-sm text-error" : "text-sm text-muted"}>{message.text}</p>}
    </form>
  );
}
