"use client";

import { useMemo, useState, useTransition } from "react";
import { updateProfile } from "@/app/actions/profile";
import { Button, Chip, ChipGroup, Field, InlineMessage, inputClass } from "@/components/ui";
import { BODY_AREA_LABELS, EQUIPMENT_LABELS, EXPERIENCE_LABELS, GOAL_LABELS, label, LIMITATION_LABELS, SEX_LABELS } from "@/lib/labels";
import { BODY_AREAS, DAYS_PER_WEEK, EQUIPMENT, EXPERIENCE_LEVELS, fromUnit, GOALS, LIMITATIONS, SESSION_LENGTHS, SEXES, toUnit } from "@/lib/model";
import { DAY_MS, localDate } from "@/lib/stats";
import type { ProfileDraft } from "@/lib/sync-writer";

function Toggles<T extends string>({ options, labels, value, onChange }: { options: readonly T[]; labels: Record<string, string>; value: T[]; onChange: (v: T[]) => void }) {
  return (
    <>
      {options.map((o) => {
        const on = value.includes(o);
        return (
          <Chip key={o} on={on} onClick={() => onChange(on ? value.filter((v) => v !== o) : [...value, o])}>
            {label(labels, o)}
          </Chip>
        );
      })}
    </>
  );
}

/** A starting point for an account that hasn't run the app's onboarding yet. */
function defaultProfile(): ProfileDraft {
  return {
    sex: "FEMALE",
    height_cm: 170,
    birth_date_epoch_day: Math.round((localDate(Date.now()) - 30 * 365.25 * DAY_MS) / DAY_MS),
    body_weight_kg: 70,
    units: "KG",
    experience: "NEW",
    goal: "GENERAL_FITNESS",
    days_per_week: 3,
    session_length_minutes: 60,
    equipment: [],
    focus_areas: [],
    limitations: [],
  };
}

/**
 * Edits the training profile. [version] is the profile's updated_at as loaded; the server
 * refuses to save over a newer one. With [create], there is no profile yet and saving makes one.
 */
export function ProfileForm(props: { initial: ProfileDraft; version: number; create?: false } | { create: true; initial?: undefined; version?: undefined }) {
  const initial = useMemo(() => props.initial ?? defaultProfile(), [props.initial]);
  const [p, setP] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [pending, start] = useTransition();
  const dirty = props.create || JSON.stringify(p) !== JSON.stringify(initial);
  const set = (patch: Partial<ProfileDraft>) => {
    setSaved(false);
    setP({ ...p, ...patch });
  };

  function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    start(async () => {
      // The save revalidates the page, which brings the new version in as props.
      const result = await updateProfile(p, props.version ?? 0);
      if (!result.ok) setError(result.error);
      else setSaved(true);
    });
  }

  const birth = new Date(p.birth_date_epoch_day * DAY_MS).toISOString().slice(0, 10);
  const u = p.units.toLowerCase();

  return (
    <form onSubmit={save} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Goal">
          <select className={inputClass} value={p.goal} onChange={(e) => set({ goal: e.target.value as ProfileDraft["goal"] })}>
            {GOALS.map((g) => (
              <option key={g} value={g}>
                {GOAL_LABELS[g]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Experience">
          <select className={inputClass} value={p.experience} onChange={(e) => set({ experience: e.target.value as ProfileDraft["experience"] })}>
            {EXPERIENCE_LEVELS.map((x) => (
              <option key={x} value={x}>
                {label(EXPERIENCE_LABELS, x)}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Days per week">
          <select className={inputClass} value={p.days_per_week} onChange={(e) => set({ days_per_week: Number(e.target.value) })}>
            {DAYS_PER_WEEK.map((d) => (
              <option key={d} value={d}>
                {d} days a week
              </option>
            ))}
          </select>
        </Field>
        <Field label="Session length">
          <select className={inputClass} value={p.session_length_minutes} onChange={(e) => set({ session_length_minutes: Number(e.target.value) })}>
            {SESSION_LENGTHS.map((m) => (
              <option key={m} value={m}>
                {m === 75 ? "75+ min" : `${m} min`}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Sex">
          <select className={inputClass} value={p.sex} onChange={(e) => set({ sex: e.target.value as ProfileDraft["sex"] })}>
            {SEXES.map((x) => (
              <option key={x} value={x}>
                {label(SEX_LABELS, x)}
              </option>
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
        <Field label={`Weight (${u})`} hint="Logging a weight below updates this too.">
          <input
            type="number"
            step={0.1}
            min={Math.floor(toUnit(30, p.units))}
            max={Math.ceil(toUnit(300, p.units))}
            className={inputClass}
            value={Number(toUnit(p.body_weight_kg, p.units).toFixed(1))}
            onChange={(e) => set({ body_weight_kg: fromUnit(Number(e.target.value), p.units) })}
          />
        </Field>
        <Field label="Units" hint="How weights show here and in the app.">
          <select className={inputClass} value={p.units} onChange={(e) => set({ units: e.target.value as ProfileDraft["units"] })}>
            <option value="KG">kg</option>
            <option value="LB">lb</option>
          </select>
        </Field>
      </div>
      <ChipGroup label="Focus areas" hint="Adds extra work for these areas.">
        <Toggles options={BODY_AREAS} labels={BODY_AREA_LABELS} value={p.focus_areas} onChange={(focus_areas) => set({ focus_areas })} />
      </ChipGroup>
      <ChipGroup label="Pain or injuries to work around" hint="Exercises that load these areas are left out of generated plans.">
        <Toggles options={LIMITATIONS} labels={LIMITATION_LABELS} value={p.limitations} onChange={(limitations) => set({ limitations })} />
      </ChipGroup>
      <ChipGroup label="Equipment you have">
        <Toggles options={EQUIPMENT} labels={EQUIPMENT_LABELS} value={p.equipment} onChange={(equipment) => set({ equipment })} />
      </ChipGroup>
      <p className="text-xs text-muted">
        {props.create
          ? "The app builds your first plan from this when you sign in there."
          : "Changes don't rebuild your plan. Rebuild it in the app if your goal or schedule changed a lot."}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" variant="primary" disabled={pending || !dirty}>
          {pending ? "Saving…" : props.create ? "Create profile" : "Save profile"}
        </Button>
        {saved && !dirty && <InlineMessage tone="success">Saved · syncs to your phone next time the app opens.</InlineMessage>}
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
      </div>
    </form>
  );
}
