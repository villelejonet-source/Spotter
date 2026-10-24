"use client";

import { useState, useTransition } from "react";
import { planWeeks, removeScheduled, scheduleWorkout } from "@/app/actions/schedule";
import { Button, Chip, ChipGroup, Field, IconButton, InlineMessage, inputClass } from "@/components/ui";
import { FREE_WORKOUT } from "@/lib/labels";

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
            <li key={p.syncId} className="flex items-center justify-between gap-2 rounded-lg border border-dashed border-outline-strong py-1 pr-1 pl-3 text-sm">
              <span className="min-w-0">
                <span className="block font-medium">{p.title}</span>
                {p.notes && <span className="block text-xs text-muted">{p.notes}</span>}
              </span>
              <IconButton label={`Remove ${p.title}`} className="hover:text-error" disabled={pending} onClick={() => remove(p.syncId)}>
                ✕
              </IconButton>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={add} className="space-y-2">
        <Field label="Workout">
          <select className={inputClass} value={dayId} onChange={(e) => setDayId(e.target.value)}>
            {plans.map((plan) => (
              <optgroup key={plan.name} label={plan.active ? `${plan.name} (active)` : plan.name}>
                {plan.days.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </optgroup>
            ))}
            <option value="">{FREE_WORKOUT}</option>
          </select>
        </Field>
        <Field label="Note (optional)">
          <input className={inputClass} maxLength={200} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <Button type="submit" variant="primary" className="w-full" disabled={pending}>
          {pending ? "Saving…" : "Plan this workout"}
        </Button>
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
      </form>
    </div>
  );
}

/** Fills chosen weekdays for 1–4 weeks with the active plan's days, in order. [weekdays] are the labels, Monday first. */
export function PlanAhead({ defaultWeekdays, weekdays: labels }: { defaultWeekdays: number[]; weekdays: string[] }) {
  const [weekdays, setWeekdays] = useState(defaultWeekdays);
  const [weeks, setWeeks] = useState(2);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);
  const [pending, start] = useTransition();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    start(async () => {
      const result = await planWeeks({ weekdays, weeks });
      if (!result.ok) setMessage({ tone: "error", text: result.error });
      else setMessage({ tone: "success", text: result.added ? `Planned ${result.added} workouts.` : "Those days are already planned." });
    });
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <ChipGroup label="Training days">
        {labels.map((label, i) => {
          const on = weekdays.includes(i);
          return (
            <Chip key={label} on={on} onClick={() => setWeekdays(on ? weekdays.filter((d) => d !== i) : [...weekdays, i].sort())}>
              {label}
            </Chip>
          );
        })}
      </ChipGroup>
      <Field label="How far ahead">
        <select className={inputClass} value={weeks} onChange={(e) => setWeeks(Number(e.target.value))}>
          {[1, 2, 3, 4].map((w) => (
            <option key={w} value={w}>
              {w === 1 ? "Next 7 days" : `Next ${w} weeks`}
            </option>
          ))}
        </select>
      </Field>
      <Button type="submit" className="w-full" disabled={pending || !weekdays.length}>
        {pending ? "Planning…" : "Plan weeks ahead"}
      </Button>
      {message && <InlineMessage tone={message.tone}>{message.text}</InlineMessage>}
    </form>
  );
}
