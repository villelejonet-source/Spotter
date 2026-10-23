"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { updateProfile } from "@/app/actions/profile";
import { Button, inputClass } from "@/components/ui";
import {
  BODY_AREAS,
  DAYS_PER_WEEK,
  enumLabel,
  EQUIPMENT,
  EXPERIENCE_LEVELS,
  fromUnit,
  GOAL_LABELS,
  GOALS,
  LIMITATIONS,
  SESSION_LENGTHS,
  SEXES,
  toUnit,
} from "@/lib/model";
import type { ProfileDraft } from "@/lib/sync-writer";

const DAY_MS = 86_400_000;

function Toggles<T extends string>({ options, value, onChange }: { options: readonly T[]; value: T[]; onChange: (v: T[]) => void }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((o) => {
        const on = value.includes(o);
        return (
          <button
            key={o}
            type="button"
            onClick={() => onChange(on ? value.filter((v) => v !== o) : [...value, o])}
            className={clsx("rounded-full px-2.5 py-1 text-xs transition", on ? "bg-primary-container text-on-primary-container" : "bg-surface-high text-muted hover:text-foreground")}
          >
            {enumLabel(o)}
          </button>
        );
      })}
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1 text-sm">
      <span className="text-muted">{label}</span>
      {children}
      {hint && <span className="block text-xs text-muted">{hint}</span>}
    </label>
  );
}

export function ProfileForm({ initial }: { initial: ProfileDraft }) {
  const router = useRouter();
  const [p, setP] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [pending, start] = useTransition();
  const dirty = JSON.stringify(p) !== JSON.stringify(initial);
  const set = (patch: Partial<ProfileDraft>) => {
    setSaved(false);
    setP({ ...p, ...patch });
  };

  function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    start(async () => {
      const result = await updateProfile(p);
      if (!result.ok) setError(result.error);
      else {
        setSaved(true);
        router.refresh();
      }
    });
  }

  const birth = new Date(p.birth_date_epoch_day * DAY_MS).toISOString().slice(0, 10);

  return (
    <form onSubmit={save} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Goal">
          <select className={inputClass} value={p.goal} onChange={(e) => set({ goal: e.target.value as ProfileDraft["goal"] })}>
            {GOALS.map((g) => (
              <option key={g} value={g}>{GOAL_LABELS[g]}</option>
            ))}
          </select>
        </Field>
        <Field label="Experience">
          <select className={inputClass} value={p.experience} onChange={(e) => set({ experience: e.target.value as ProfileDraft["experience"] })}>
            {EXPERIENCE_LEVELS.map((x) => (
              <option key={x} value={x}>{enumLabel(x)}</option>
            ))}
          </select>
        </Field>
        <Field label="Days per week">
          <select className={inputClass} value={p.days_per_week} onChange={(e) => set({ days_per_week: Number(e.target.value) })}>
            {DAYS_PER_WEEK.map((d) => (
              <option key={d} value={d}>{d}</option>
            ))}
          </select>
        </Field>
        <Field label="Session length">
          <select className={inputClass} value={p.session_length_minutes} onChange={(e) => set({ session_length_minutes: Number(e.target.value) })}>
            {SESSION_LENGTHS.map((m) => (
              <option key={m} value={m}>{m} min</option>
            ))}
          </select>
        </Field>
        <Field label="Sex">
          <select className={inputClass} value={p.sex} onChange={(e) => set({ sex: e.target.value as ProfileDraft["sex"] })}>
            {SEXES.map((x) => (
              <option key={x} value={x}>{enumLabel(x)}</option>
            ))}
          </select>
        </Field>
        <Field label="Birth date">
          <input
            type="date"
            className={inputClass}
            value={birth}
            onChange={(e) => e.target.value && set({ birth_date_epoch_day: Math.round(Date.parse(`${e.target.value}T00:00:00Z`) / DAY_MS) })}
          />
        </Field>
        <Field label="Height (cm)">
          <input type="number" min={100} max={250} className={inputClass} value={p.height_cm} onChange={(e) => set({ height_cm: Number(e.target.value) })} />
        </Field>
        <Field label={`Body weight (${p.units.toLowerCase()})`} hint="Logging a weight below updates this too.">
          <input
            type="number"
            step={0.1}
            className={inputClass}
            value={Number(toUnit(p.body_weight_kg, p.units).toFixed(1))}
            onChange={(e) => set({ body_weight_kg: fromUnit(Number(e.target.value), p.units) })}
          />
        </Field>
        <Field label="Units on the website" hint="The app keeps its own unit setting.">
          <select className={inputClass} value={p.units} onChange={(e) => set({ units: e.target.value as ProfileDraft["units"] })}>
            <option value="KG">kg</option>
            <option value="LB">lb</option>
          </select>
        </Field>
      </div>
      <div className="space-y-1.5 text-sm">
        <span className="text-muted">Focus areas</span>
        <Toggles options={BODY_AREAS} value={p.focus_areas} onChange={(focus_areas) => set({ focus_areas })} />
      </div>
      <div className="space-y-1.5 text-sm">
        <span className="text-muted">Work around</span>
        <Toggles options={LIMITATIONS} value={p.limitations} onChange={(limitations) => set({ limitations })} />
      </div>
      <div className="space-y-1.5 text-sm">
        <span className="text-muted">Equipment you have</span>
        <Toggles options={EQUIPMENT} value={p.equipment} onChange={(equipment) => set({ equipment })} />
      </div>
      <p className="text-xs text-muted">Changes don&apos;t rebuild your plan. Generate a new plan in the app if your goal or schedule changed a lot.</p>
      <div className="flex items-center gap-3">
        <Button variant="primary" disabled={pending || !dirty}>{pending ? "Saving…" : "Save profile"}</Button>
        {saved && !dirty && <span className="text-sm text-muted">Saved · syncs to your phone next time the app opens</span>}
        {error && <span className="text-sm text-error">{error}</span>}
      </div>
    </form>
  );
}
