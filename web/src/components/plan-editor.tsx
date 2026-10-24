"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState, useTransition } from "react";
import { savePlan } from "@/app/actions/plans";
import {
  enumLabel,
  fromUnit,
  GOAL_LABELS,
  GOALS,
  MUSCLES,
  PROGRESSION_LABELS,
  PROGRESSION_RULES,
  SPLIT_LABELS,
  SPLIT_TYPES,
  TIMED_EXERCISE_IDS,
  toUnit,
  type WeightUnit,
} from "@/lib/model";
import { newSyncId, type DayDraft, type ExerciseDraft, type PlanDraft, type PlanVersions } from "@/lib/sync-writer";
import { Badge, Button, Card, inputClass } from "./ui";

export interface ExerciseOption {
  id: string;
  name: string;
  primary: string[];
  equipment: string[];
  isCustom: boolean;
}

const smallInput = "w-full rounded-md border border-outline px-2 py-1 text-sm tabular-nums outline-none focus:border-primary";

function move<T>(list: T[], from: number, to: number): T[] {
  if (to < 0 || to >= list.length) return list;
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

function newExercise(exerciseId: string): ExerciseDraft {
  const timed = TIMED_EXERCISE_IDS.has(exerciseId);
  return {
    sync_id: newSyncId(),
    exercise_id: exerciseId,
    sets: 3,
    rep_min: timed ? 30 : 8,
    rep_max: timed ? 60 : 12,
    target_rir: timed ? null : 2,
    rest_seconds: 90,
    progression_rule: timed ? "NONE" : "DOUBLE_PROGRESSION",
    superset_group: null,
    starting_weight_kg: null,
  };
}

export function newPlanDraft(): PlanDraft {
  return {
    sync_id: newSyncId(),
    name: "My plan",
    split_type: "CUSTOM",
    goal: "HYPERTROPHY",
    days: [{ sync_id: newSyncId(), name: "Day A", exercises: [] }],
  };
}

export function PlanEditor({
  initial,
  base = {},
  exercises,
  unit,
  isNew,
}: {
  initial: PlanDraft;
  /** Versions of the rows as loaded, so the server can refuse to overwrite newer edits. */
  base?: PlanVersions;
  exercises: ExerciseOption[];
  unit: WeightUnit;
  isNew: boolean;
}) {
  const router = useRouter();
  const [plan, setPlan] = useState(initial);
  const [saved, setSaved] = useState(JSON.stringify(initial));
  const [pickerDay, setPickerDay] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();
  const dirty = isNew || JSON.stringify(plan) !== saved;
  const byId = useMemo(() => new Map(exercises.map((e) => [e.id, e])), [exercises]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const setDay = (i: number, day: DayDraft) => setPlan((p) => ({ ...p, days: p.days.map((d, j) => (j === i ? day : d)) }));
  const setExercise = (dayIndex: number, i: number, patch: Partial<ExerciseDraft>) =>
    setPlan((p) => ({
      ...p,
      days: p.days.map((d, j) => (j === dayIndex ? { ...d, exercises: d.exercises.map((e, k) => (k === i ? { ...e, ...patch } : e)) } : d)),
    }));

  function save() {
    setError(null);
    start(async () => {
      const result = await savePlan(plan, base);
      if (!result.ok) {
        setError(result.error);
        return;
      }
      setSaved(JSON.stringify(plan));
      // The save revalidates every page, so an existing plan's page re-renders by itself.
      if (isNew) router.replace(`/plans/${plan.sync_id}`);
    });
  }

  const num = (value: string) => (value === "" ? null : Number(value));

  return (
    <div className="space-y-4">
      <Card className="grid gap-3 sm:grid-cols-3">
        <label className="space-y-1 text-sm sm:col-span-3">
          <span className="text-muted">Name</span>
          <input className={inputClass} value={plan.name} maxLength={80} onChange={(e) => setPlan({ ...plan, name: e.target.value })} />
        </label>
        <label className="space-y-1 text-sm">
          <span className="text-muted">Split</span>
          <select className={inputClass} value={plan.split_type} onChange={(e) => setPlan({ ...plan, split_type: e.target.value as PlanDraft["split_type"] })}>
            {SPLIT_TYPES.map((s) => (
              <option key={s} value={s}>{SPLIT_LABELS[s]}</option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-sm">
          <span className="text-muted">Goal</span>
          <select className={inputClass} value={plan.goal} onChange={(e) => setPlan({ ...plan, goal: e.target.value as PlanDraft["goal"] })}>
            {GOALS.map((g) => (
              <option key={g} value={g}>{GOAL_LABELS[g]}</option>
            ))}
          </select>
        </label>
      </Card>

      {plan.days.map((day, dayIndex) => (
        <Card key={day.sync_id} className="p-0">
          <div className="flex flex-wrap items-center gap-2 border-b border-outline p-4">
            <input
              className="min-w-0 flex-1 rounded-md bg-transparent px-1 text-lg font-semibold outline-none focus:ring-1 focus:ring-primary"
              value={day.name}
              maxLength={60}
              onChange={(e) => setDay(dayIndex, { ...day, name: e.target.value })}
              aria-label="Day name"
            />
            <div className="flex gap-1">
              <Button variant="ghost" className="px-2.5" title="Move up" disabled={dayIndex === 0} onClick={() => setPlan({ ...plan, days: move(plan.days, dayIndex, dayIndex - 1) })}>↑</Button>
              <Button variant="ghost" className="px-2.5" title="Move down" disabled={dayIndex === plan.days.length - 1} onClick={() => setPlan({ ...plan, days: move(plan.days, dayIndex, dayIndex + 1) })}>↓</Button>
              <Button
                variant="ghost"
                className="px-2.5 text-error"
                title="Remove day"
                disabled={plan.days.length === 1}
                onClick={() => setPlan({ ...plan, days: plan.days.filter((_, j) => j !== dayIndex) })}
              >
                Remove
              </Button>
            </div>
          </div>

          {day.exercises.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-sm">
                <thead className="text-left text-xs text-muted">
                  <tr>
                    <th className="px-4 py-2 font-normal">Exercise</th>
                    <th className="w-16 px-1 py-2 font-normal">Sets</th>
                    <th className="w-32 px-1 py-2 font-normal">Reps / sec</th>
                    <th className="w-16 px-1 py-2 font-normal">RIR</th>
                    <th className="w-20 px-1 py-2 font-normal">Rest (s)</th>
                    <th className="w-20 px-1 py-2 font-normal">Start {unit.toLowerCase()}</th>
                    <th className="w-16 px-1 py-2 font-normal" title="Exercises with the same number are done as a superset">SS</th>
                    <th className="w-40 px-1 py-2 font-normal">Progression</th>
                    <th className="w-24 px-2 py-2" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline">
                  {day.exercises.map((e, i) => (
                    <tr key={e.sync_id}>
                      <td className="px-4 py-2">
                        <div className="font-medium">{byId.get(e.exercise_id)?.name ?? e.exercise_id}</div>
                        <div className="text-xs text-muted">{byId.get(e.exercise_id)?.primary.map(enumLabel).join(", ")}</div>
                      </td>
                      <td className="px-1 py-2">
                        <input type="number" min={1} max={20} className={smallInput} value={e.sets} onChange={(x) => setExercise(dayIndex, i, { sets: Number(x.target.value) })} />
                      </td>
                      <td className="px-1 py-2">
                        <div className="flex items-center gap-1">
                          <input type="number" min={1} max={300} className={smallInput} value={e.rep_min} onChange={(x) => setExercise(dayIndex, i, { rep_min: Number(x.target.value) })} />
                          <span className="text-muted">–</span>
                          <input type="number" min={1} max={300} className={smallInput} value={e.rep_max} onChange={(x) => setExercise(dayIndex, i, { rep_max: Number(x.target.value) })} />
                        </div>
                      </td>
                      <td className="px-1 py-2">
                        <input type="number" min={0} max={10} className={smallInput} value={e.target_rir ?? ""} onChange={(x) => setExercise(dayIndex, i, { target_rir: num(x.target.value) })} />
                      </td>
                      <td className="px-1 py-2">
                        <input type="number" min={0} max={900} step={15} className={smallInput} value={e.rest_seconds} onChange={(x) => setExercise(dayIndex, i, { rest_seconds: Number(x.target.value) })} />
                      </td>
                      <td className="px-1 py-2">
                        <input
                          type="number"
                          min={0}
                          step={0.5}
                          className={smallInput}
                          placeholder="–"
                          value={e.starting_weight_kg == null ? "" : Number(toUnit(e.starting_weight_kg, unit).toFixed(1))}
                          onChange={(x) => {
                            const v = num(x.target.value);
                            setExercise(dayIndex, i, { starting_weight_kg: v == null ? null : fromUnit(v, unit) });
                          }}
                        />
                      </td>
                      <td className="px-1 py-2">
                        <input type="number" min={1} max={50} className={smallInput} placeholder="–" value={e.superset_group ?? ""} onChange={(x) => setExercise(dayIndex, i, { superset_group: num(x.target.value) })} />
                      </td>
                      <td className="px-1 py-2">
                        <select className={smallInput} value={e.progression_rule} onChange={(x) => setExercise(dayIndex, i, { progression_rule: x.target.value as ExerciseDraft["progression_rule"] })}>
                          {PROGRESSION_RULES.map((r) => (
                            <option key={r} value={r}>{PROGRESSION_LABELS[r]}</option>
                          ))}
                        </select>
                      </td>
                      <td className="px-2 py-2">
                        <div className="flex justify-end">
                          <button className="rounded px-1.5 text-muted hover:bg-surface-high disabled:opacity-30" title="Move up" disabled={i === 0} onClick={() => setDay(dayIndex, { ...day, exercises: move(day.exercises, i, i - 1) })}>↑</button>
                          <button className="rounded px-1.5 text-muted hover:bg-surface-high disabled:opacity-30" title="Move down" disabled={i === day.exercises.length - 1} onClick={() => setDay(dayIndex, { ...day, exercises: move(day.exercises, i, i + 1) })}>↓</button>
                          <button className="rounded px-1.5 text-error hover:bg-surface-high" title="Remove" onClick={() => setDay(dayIndex, { ...day, exercises: day.exercises.filter((_, k) => k !== i) })}>✕</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="p-4">
            <Button onClick={() => setPickerDay(dayIndex)}>+ Add exercise</Button>
          </div>
        </Card>
      ))}

      <div className="flex flex-wrap items-center gap-3">
        <Button
          disabled={plan.days.length >= 7}
          onClick={() =>
            setPlan({ ...plan, days: [...plan.days, { sync_id: newSyncId(), name: `Day ${String.fromCharCode(65 + plan.days.length)}`, exercises: [] }] })
          }
        >
          + Add day
        </Button>
      </div>

      <div className="sticky bottom-0 -mx-4 flex items-center justify-end gap-3 border-t border-outline bg-background/95 px-4 py-3 backdrop-blur md:-mx-10 md:px-10">
        {error && <span className="mr-auto text-sm text-error">{error}</span>}
        {!error && dirty && <span className="mr-auto text-sm text-muted">Unsaved changes</span>}
        {!dirty && <span className="mr-auto text-sm text-muted">Saved · syncs to your phone next time the app opens</span>}
        <Button variant="primary" disabled={pending || !dirty} onClick={save}>
          {pending ? "Saving…" : isNew ? "Create plan" : "Save changes"}
        </Button>
      </div>

      {pickerDay != null && (
        <ExercisePicker
          exercises={exercises}
          onClose={() => setPickerDay(null)}
          onPick={(id) => {
            const day = plan.days[pickerDay];
            setDay(pickerDay, { ...day, exercises: [...day.exercises, newExercise(id)] });
            setPickerDay(null);
          }}
        />
      )}
    </div>
  );
}

function ExercisePicker({ exercises, onPick, onClose }: { exercises: ExerciseOption[]; onPick: (id: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const [muscle, setMuscle] = useState<string | null>(null);
  const q = query.trim().toLowerCase();
  const results = exercises.filter((e) => (!q || e.name.toLowerCase().includes(q)) && (!muscle || e.primary.includes(muscle)));

  useEffect(() => {
    const close = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-label="Add exercise"
        className="flex max-h-[85vh] w-full max-w-lg flex-col rounded-t-2xl border border-outline bg-surface sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="space-y-3 border-b border-outline p-4">
          <input autoFocus className={inputClass} placeholder="Search exercises" value={query} onChange={(e) => setQuery(e.target.value)} />
          <div className="flex gap-1 overflow-x-auto pb-1">
            {[null, ...MUSCLES].map((m) => (
              <button
                key={m ?? "all"}
                onClick={() => setMuscle(m)}
                className={clsx(
                  "whitespace-nowrap rounded-full px-2.5 py-1 text-xs",
                  muscle === m ? "bg-primary-container text-on-primary-container" : "bg-surface-high text-muted",
                )}
              >
                {m ? enumLabel(m) : "All"}
              </button>
            ))}
          </div>
        </div>
        <ul className="overflow-y-auto p-2">
          {results.map((e) => (
            <li key={e.id}>
              <button onClick={() => onPick(e.id)} className="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left hover:bg-surface-high">
                <span>
                  <span className="block text-sm font-medium">{e.name}</span>
                  <span className="block text-xs text-muted">
                    {e.primary.map(enumLabel).join(", ")}
                    {e.equipment.length ? ` · ${e.equipment.map(enumLabel).join(", ")}` : " · Bodyweight"}
                  </span>
                </span>
                {e.isCustom && <Badge>Custom</Badge>}
              </button>
            </li>
          ))}
          {!results.length && <li className="p-4 text-center text-sm text-muted">No exercises match.</li>}
        </ul>
      </div>
    </div>
  );
}
