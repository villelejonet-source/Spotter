"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState, useTransition } from "react";
import { savePlan } from "@/app/actions/plans";
import { newSyncId } from "@/lib/ids";
import { BODYWEIGHT, EFFORT_LABEL, EQUIPMENT_LABELS, GOAL_LABELS, label, MUSCLE_LABELS, PROGRESSION_RULE_LABELS, SPLIT_LABELS } from "@/lib/labels";
import { fromUnit, GOALS, isTimed, MUSCLES, PROGRESSION_RULES, SPLIT_TYPES, toUnit, unitLabel, type WeightUnit } from "@/lib/model";
import type { DayDraft, ExerciseDraft, PlanDraft, PlanVersions } from "@/lib/sync-writer";
import { ConfirmDialog, Dialog } from "./dialog";
import { Badge, Button, Card, Chip, Field, IconButton, InlineMessage, inputClass, inputClasses } from "./ui";

export interface ExerciseOption {
  id: string;
  name: string;
  primary: string[];
  equipment: string[];
  isCustom: boolean;
}

const compact = inputClasses({ compact: true });

function move<T>(list: T[], from: number, to: number): T[] {
  if (to < 0 || to >= list.length) return list;
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

function newExercise(exerciseId: string): ExerciseDraft {
  const timed = isTimed(exerciseId);
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

type Removal = { kind: "day"; dayIndex: number } | { kind: "exercise"; dayIndex: number; index: number };

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
  const [saved, setSaved] = useState(() => JSON.stringify(initial));
  const [pickerDay, setPickerDay] = useState<number | null>(null);
  const [removal, setRemoval] = useState<Removal | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();
  const serialized = useMemo(() => JSON.stringify(plan), [plan]);
  const dirty = isNew || serialized !== saved;
  const byId = useMemo(() => new Map(exercises.map((e) => [e.id, e])), [exercises]);
  const nameOf = (id: string) => byId.get(id)?.name ?? id;

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

  function removeDay(dayIndex: number) {
    setPlan((p) => ({ ...p, days: p.days.filter((_, j) => j !== dayIndex) }));
  }

  function removeExercise(dayIndex: number, index: number) {
    const day = plan.days[dayIndex];
    setDay(dayIndex, { ...day, exercises: day.exercises.filter((_, k) => k !== index) });
  }

  function confirmRemoval() {
    if (!removal) return;
    if (removal.kind === "day") removeDay(removal.dayIndex);
    else removeExercise(removal.dayIndex, removal.index);
    setRemoval(null);
  }

  function save() {
    setError(null);
    start(async () => {
      const result = await savePlan(plan, base);
      if (!result.ok) {
        setError(result.error);
        return;
      }
      setSaved(serialized);
      // The save revalidates every page, so an existing plan's page re-renders by itself.
      if (isNew) router.replace(`/plans/${plan.sync_id}`);
    });
  }

  const num = (value: string) => (value === "" ? null : Number(value));
  const removalTarget = removal && (removal.kind === "day" ? plan.days[removal.dayIndex] : plan.days[removal.dayIndex]?.exercises[removal.index]);

  return (
    <div className="space-y-4">
      <Card className="grid gap-3 sm:grid-cols-3">
        <Field label="Name" className="sm:col-span-3">
          <input className={inputClass} value={plan.name} maxLength={80} onChange={(e) => setPlan({ ...plan, name: e.target.value })} />
        </Field>
        <Field label="Split">
          <select className={inputClass} value={plan.split_type} onChange={(e) => setPlan({ ...plan, split_type: e.target.value as PlanDraft["split_type"] })}>
            {SPLIT_TYPES.map((s) => (
              <option key={s} value={s}>
                {SPLIT_LABELS[s]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Goal">
          <select className={inputClass} value={plan.goal} onChange={(e) => setPlan({ ...plan, goal: e.target.value as PlanDraft["goal"] })}>
            {GOALS.map((g) => (
              <option key={g} value={g}>
                {GOAL_LABELS[g]}
              </option>
            ))}
          </select>
        </Field>
      </Card>

      {plan.days.map((day, dayIndex) => (
        <Card key={day.sync_id} className="p-0">
          <div className="flex flex-wrap items-center gap-2 border-b border-outline p-4">
            <input
              className="min-w-0 flex-1 rounded-md border border-transparent bg-transparent px-2 py-1 text-lg font-semibold tracking-tight outline-none transition hover:border-outline focus:border-primary focus:ring-3 focus:ring-primary/20"
              value={day.name}
              maxLength={60}
              onChange={(e) => setDay(dayIndex, { ...day, name: e.target.value })}
              aria-label={`Name of day ${dayIndex + 1}`}
            />
            <div className="flex gap-1">
              <IconButton label={`Move ${day.name} up`} disabled={dayIndex === 0} onClick={() => setPlan({ ...plan, days: move(plan.days, dayIndex, dayIndex - 1) })}>
                ↑
              </IconButton>
              <IconButton label={`Move ${day.name} down`} disabled={dayIndex === plan.days.length - 1} onClick={() => setPlan({ ...plan, days: move(plan.days, dayIndex, dayIndex + 1) })}>
                ↓
              </IconButton>
              <Button
                variant="ghost"
                className="text-error"
                disabled={plan.days.length === 1}
                onClick={() => (day.exercises.length ? setRemoval({ kind: "day", dayIndex }) : removeDay(dayIndex))}
              >
                Remove day
              </Button>
            </div>
          </div>

          {day.exercises.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-sm">
                <thead className="text-left text-xs text-muted">
                  <tr>
                    <th scope="col" className="px-4 py-2 font-normal">
                      Exercise
                    </th>
                    <th scope="col" className="w-16 px-1 py-2 font-normal">
                      Sets
                    </th>
                    <th scope="col" className="w-32 px-1 py-2 font-normal">
                      Reps / s
                    </th>
                    <th scope="col" className="w-16 px-1 py-2 font-normal">
                      <abbr title="Target reps in reserve" className="no-underline">
                        {EFFORT_LABEL}
                      </abbr>
                    </th>
                    <th scope="col" className="w-20 px-1 py-2 font-normal">
                      Rest (s)
                    </th>
                    <th scope="col" className="w-20 px-1 py-2 font-normal">
                      Start ({unitLabel(unit)})
                    </th>
                    <th scope="col" className="w-20 px-1 py-2 font-normal">
                      <abbr title="Exercises with the same number are done as a superset" className="no-underline">
                        Superset
                      </abbr>
                    </th>
                    <th scope="col" className="w-40 px-1 py-2 font-normal">
                      Progression
                    </th>
                    <th scope="col" className="w-28 px-2 py-2">
                      <span className="sr-only">Reorder or remove</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline">
                  {day.exercises.map((e, i) => {
                    const name = nameOf(e.exercise_id);
                    const timed = isTimed(e.exercise_id);
                    return (
                      <tr key={e.sync_id}>
                        <td className="px-4 py-2">
                          <div className="font-medium">{name}</div>
                          <div className="text-xs text-muted">{byId.get(e.exercise_id)?.primary.map((m) => label(MUSCLE_LABELS, m)).join(", ")}</div>
                        </td>
                        <td className="px-1 py-2">
                          <input type="number" min={1} max={20} className={compact} aria-label={`Sets for ${name}`} value={e.sets} onChange={(x) => setExercise(dayIndex, i, { sets: Number(x.target.value) })} />
                        </td>
                        <td className="px-1 py-2">
                          <div className="flex items-center gap-1">
                            <input type="number" min={1} max={300} className={compact} aria-label={`${timed ? "Seconds" : "Reps"} from, for ${name}`} value={e.rep_min} onChange={(x) => setExercise(dayIndex, i, { rep_min: Number(x.target.value) })} />
                            <span className="text-muted" aria-hidden="true">
                              –
                            </span>
                            <input type="number" min={1} max={300} className={compact} aria-label={`${timed ? "Seconds" : "Reps"} to, for ${name}`} value={e.rep_max} onChange={(x) => setExercise(dayIndex, i, { rep_max: Number(x.target.value) })} />
                          </div>
                        </td>
                        <td className="px-1 py-2">
                          <input type="number" min={0} max={10} className={compact} aria-label={`Target ${EFFORT_LABEL} for ${name}`} value={e.target_rir ?? ""} onChange={(x) => setExercise(dayIndex, i, { target_rir: num(x.target.value) })} />
                        </td>
                        <td className="px-1 py-2">
                          <input type="number" min={0} max={900} step={15} className={compact} aria-label={`Rest in seconds for ${name}`} value={e.rest_seconds} onChange={(x) => setExercise(dayIndex, i, { rest_seconds: Number(x.target.value) })} />
                        </td>
                        <td className="px-1 py-2">
                          <input
                            type="number"
                            min={0}
                            step={0.5}
                            className={compact}
                            aria-label={`Starting weight in ${unitLabel(unit)} for ${name}`}
                            placeholder="–"
                            value={e.starting_weight_kg == null ? "" : Number(toUnit(e.starting_weight_kg, unit).toFixed(1))}
                            onChange={(x) => {
                              const v = num(x.target.value);
                              setExercise(dayIndex, i, { starting_weight_kg: v == null ? null : fromUnit(v, unit) });
                            }}
                          />
                        </td>
                        <td className="px-1 py-2">
                          <input type="number" min={1} max={50} className={compact} aria-label={`Superset group for ${name}`} placeholder="–" value={e.superset_group ?? ""} onChange={(x) => setExercise(dayIndex, i, { superset_group: num(x.target.value) })} />
                        </td>
                        <td className="px-1 py-2">
                          <select className={clsx(compact, "font-sans text-sm")} aria-label={`Progression for ${name}`} value={e.progression_rule} onChange={(x) => setExercise(dayIndex, i, { progression_rule: x.target.value as ExerciseDraft["progression_rule"] })}>
                            {PROGRESSION_RULES.map((r) => (
                              <option key={r} value={r}>
                                {PROGRESSION_RULE_LABELS[r]}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td className="px-2 py-2">
                          <div className="flex justify-end">
                            <IconButton label={`Move ${name} up`} className="h-8 w-8" disabled={i === 0} onClick={() => setDay(dayIndex, { ...day, exercises: move(day.exercises, i, i - 1) })}>
                              ↑
                            </IconButton>
                            <IconButton label={`Move ${name} down`} className="h-8 w-8" disabled={i === day.exercises.length - 1} onClick={() => setDay(dayIndex, { ...day, exercises: move(day.exercises, i, i + 1) })}>
                              ↓
                            </IconButton>
                            <IconButton label={`Remove ${name}`} className="h-8 w-8 text-error" onClick={() => setRemoval({ kind: "exercise", dayIndex, index: i })}>
                              ✕
                            </IconButton>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
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
        <Button disabled={plan.days.length >= 7} onClick={() => setPlan({ ...plan, days: [...plan.days, { sync_id: newSyncId(), name: `Day ${String.fromCharCode(65 + plan.days.length)}`, exercises: [] }] })}>
          + Add day
        </Button>
      </div>

      <div className="sticky bottom-14 -mx-4 flex items-center justify-end gap-3 border-t border-outline bg-background/95 px-4 py-3 backdrop-blur md:bottom-0 md:-mx-10 md:px-10">
        {error ? (
          <InlineMessage tone="error" className="mr-auto">
            {error}
          </InlineMessage>
        ) : (
          <span className="mr-auto text-sm text-muted" role="status">
            {dirty ? "Unsaved changes" : "Saved · syncs to your phone next time the app opens"}
          </span>
        )}
        <Button variant="primary" disabled={pending || !dirty} onClick={save}>
          {pending ? "Saving…" : isNew ? "Create plan" : "Save changes"}
        </Button>
      </div>

      <ExercisePicker
        open={pickerDay != null}
        exercises={exercises}
        onClose={() => setPickerDay(null)}
        onPick={(id) => {
          if (pickerDay == null) return;
          const day = plan.days[pickerDay];
          setDay(pickerDay, { ...day, exercises: [...day.exercises, newExercise(id)] });
          setPickerDay(null);
        }}
      />

      <ConfirmDialog
        open={removal != null}
        onClose={() => setRemoval(null)}
        onConfirm={confirmRemoval}
        title={removal?.kind === "day" ? "Remove this day?" : "Remove this exercise?"}
        body={
          removal?.kind === "day"
            ? `${removalTarget && "name" in removalTarget ? removalTarget.name : "This day"} and its ${plan.days[removal.dayIndex]?.exercises.length ?? 0} exercises are removed when you save. Past workouts stay.`
            : `${removalTarget && "exercise_id" in removalTarget ? nameOf(removalTarget.exercise_id) : "The exercise"} is removed from this day when you save. Past workouts stay.`
        }
        confirmLabel="Remove"
        variant="danger"
      />
    </div>
  );
}

/** The app's exercise picker: search, a muscle filter, one tap adds. */
function ExercisePicker({ open, exercises, onPick, onClose }: { open: boolean; exercises: ExerciseOption[]; onPick: (id: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const [muscle, setMuscle] = useState<string | null>(null);
  const q = query.trim().toLowerCase();
  const results = exercises.filter((e) => (!q || e.name.toLowerCase().includes(q)) && (!muscle || e.primary.includes(muscle)));

  return (
    <Dialog open={open} onClose={onClose} title="Add exercise" className="max-w-lg">
      <div className="mt-3 space-y-3">
        <Field label="Search">
          <input autoFocus type="search" className={inputClass} placeholder="Exercise name" value={query} onChange={(e) => setQuery(e.target.value)} />
        </Field>
        <div className="flex gap-1 overflow-x-auto pb-1" role="group" aria-label="Main muscle">
          {[null, ...MUSCLES].map((m) => (
            <Chip key={m ?? "all"} on={muscle === m} onClick={() => setMuscle(m)}>
              {m ? label(MUSCLE_LABELS, m) : "All"}
            </Chip>
          ))}
        </div>
      </div>
      <ul className="-mx-2 mt-2 max-h-[50vh] overflow-y-auto">
        {results.map((e) => (
          <li key={e.id}>
            <button type="button" onClick={() => onPick(e.id)} className="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left hover:bg-surface-high">
              <span>
                <span className="block text-sm font-medium">{e.name}</span>
                <span className="block text-xs text-muted">
                  {e.primary.map((m) => label(MUSCLE_LABELS, m)).join(", ")}
                  {e.equipment.length ? ` · ${e.equipment.map((x) => label(EQUIPMENT_LABELS, x)).join(", ")}` : ` · ${BODYWEIGHT}`}
                </span>
              </span>
              {e.isCustom && <Badge>Custom</Badge>}
            </button>
          </li>
        ))}
        {!results.length && <li className="p-4 text-center text-sm text-muted">No exercises match. Try another search or create your own.</li>}
      </ul>
    </Dialog>
  );
}
