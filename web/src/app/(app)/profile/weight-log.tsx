"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { deleteBodyWeight, logBodyWeight } from "@/app/actions/profile";
import { Button, inputClass } from "@/components/ui";
import { fromUnit, toUnit, type BodyWeightRow, type WeightUnit } from "@/lib/model";

const DAY_MS = 86_400_000;

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Body weight entries are per calendar day (epoch days), newest first. */
export function WeightLog({ entries, unit }: { entries: BodyWeightRow[]; unit: WeightUnit }) {
  const router = useRouter();
  const [date, setDate] = useState(todayIso);
  const [weight, setWeight] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();
  const u = unit.toLowerCase();

  function add(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const epochDay = Math.round(Date.parse(`${date}T00:00:00Z`) / DAY_MS);
    start(async () => {
      const result = await logBodyWeight(epochDay, fromUnit(Number(weight), unit));
      if (!result.ok) setError(result.error);
      else {
        setWeight("");
        router.refresh();
      }
    });
  }

  const newest = [...entries].sort((a, b) => b.epoch_day - a.epoch_day);

  return (
    <div className="space-y-3">
      <form onSubmit={add} className="flex flex-wrap items-end gap-2">
        <label className="space-y-1 text-sm">
          <span className="block text-muted">Date</span>
          <input type="date" required max={todayIso()} className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="w-28 space-y-1 text-sm">
          <span className="block text-muted">Weight ({u})</span>
          <input type="number" required step={0.1} min={25} className={inputClass} value={weight} onChange={(e) => setWeight(e.target.value)} />
        </label>
        <Button variant="primary" disabled={pending || !weight}>{pending ? "Saving…" : "Log"}</Button>
      </form>
      {error && <p className="text-sm text-error">{error}</p>}
      {newest.length > 0 && (
        <ul className="max-h-64 divide-y divide-outline overflow-y-auto text-sm tabular-nums">
          {newest.map((e) => (
            <li key={e.sync_id} className="flex items-center justify-between py-1.5">
              <span>{new Date(e.epoch_day * DAY_MS).toLocaleDateString("en-GB", { timeZone: "UTC", day: "numeric", month: "short", year: "numeric" })}</span>
              <span className="flex items-center gap-3">
                {toUnit(e.weight_kg, unit).toFixed(1)} {u}
                <button
                  className="text-muted hover:text-error"
                  title="Delete entry"
                  disabled={pending}
                  onClick={() => start(async () => { await deleteBodyWeight(e.sync_id); router.refresh(); })}
                >
                  ✕
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
