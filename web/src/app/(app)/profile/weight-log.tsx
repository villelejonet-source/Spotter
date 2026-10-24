"use client";

import { useState, useTransition } from "react";
import { deleteBodyWeight, logBodyWeight } from "@/app/actions/profile";
import { ConfirmDialog } from "@/components/dialog";
import { Button, Field, IconButton, InlineMessage, inputClass } from "@/components/ui";
import { formatEpochDay } from "@/lib/dates";
import { formatWeightValue, fromUnit, toUnit, unitLabel, type BodyWeightRow, type WeightUnit } from "@/lib/model";
import { DAY_MS, dayKey } from "@/lib/stats";

/** Today in the display time zone, so server and browser agree (and so does the app's calendar). */
function todayIso(): string {
  return dayKey(Date.now());
}

/** Body weight entries are per calendar day (epoch days), newest first. 30–300 kg, as in the app. */
export function WeightLog({ entries, unit }: { entries: BodyWeightRow[]; unit: WeightUnit }) {
  const [date, setDate] = useState(todayIso);
  const [weight, setWeight] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<BodyWeightRow | null>(null);
  const [pending, start] = useTransition();
  const u = unitLabel(unit);

  function add(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const epochDay = Math.round(Date.parse(`${date}T00:00:00Z`) / DAY_MS);
    start(async () => {
      const result = await logBodyWeight(epochDay, fromUnit(Number(weight), unit));
      if (!result.ok) setError(result.error);
      else setWeight("");
    });
  }

  function remove(entry: BodyWeightRow) {
    setError(null);
    start(async () => {
      const result = await deleteBodyWeight(entry.sync_id);
      if (!result.ok) setError(result.error);
      setDeleting(null);
    });
  }

  const newest = [...entries].sort((a, b) => b.epoch_day - a.epoch_day);

  return (
    <div className="space-y-3">
      <form onSubmit={add} className="flex flex-wrap items-end gap-2">
        <Field label="Date">
          <input type="date" required max={todayIso()} className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Field label={`Weight (${u})`} className="w-28">
          <input
            type="number"
            required
            step={0.1}
            min={Math.floor(toUnit(30, unit))}
            max={Math.ceil(toUnit(300, unit))}
            className={inputClass}
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
          />
        </Field>
        <Button type="submit" variant="primary" disabled={pending || !weight}>
          {pending ? "Saving…" : "Log weight"}
        </Button>
      </form>
      {error && <InlineMessage tone="error">{error}</InlineMessage>}
      {newest.length > 0 ? (
        <ul className="max-h-64 divide-y divide-outline overflow-y-auto text-sm tabular-nums">
          {newest.map((e) => (
            <li key={e.sync_id} className="flex items-center justify-between py-1">
              <span>{formatEpochDay(e.epoch_day)}</span>
              <span className="flex items-center gap-1">
                {formatWeightValue(e.weight_kg, unit)} {u}
                <IconButton label={`Delete the entry for ${formatEpochDay(e.epoch_day)}`} className="hover:text-error" disabled={pending} onClick={() => setDeleting(e)}>
                  ✕
                </IconButton>
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted">Log your weight a few times to see the trend.</p>
      )}
      <ConfirmDialog
        open={deleting != null}
        onClose={() => !pending && setDeleting(null)}
        onConfirm={() => deleting && remove(deleting)}
        title="Delete this entry?"
        body={deleting && `${formatWeightValue(deleting.weight_kg, unit)} ${u} on ${formatEpochDay(deleting.epoch_day)} is removed here and from the app.`}
        confirmLabel="Delete"
        variant="danger"
        pending={pending}
      />
    </div>
  );
}
