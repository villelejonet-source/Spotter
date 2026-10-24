"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { createCustomExercise } from "@/app/actions/library";
import { Button, Card, Chip, ChipGroup, Field, InlineMessage, inputClass } from "@/components/ui";
import { EQUIPMENT_LABELS, label, MUSCLE_LABELS, PATTERN_LABELS } from "@/lib/labels";
import { EQUIPMENT, MOVEMENT_PATTERNS, MUSCLES } from "@/lib/model";
import type { CustomExerciseDraft } from "@/lib/sync-writer";

function Toggles<T extends string>({ options, labels, value, onChange, max }: { options: readonly T[]; labels: Record<string, string>; value: T[]; onChange: (v: T[]) => void; max?: number }) {
  return (
    <>
      {options.map((o) => {
        const on = value.includes(o);
        return (
          <Chip key={o} on={on} disabled={!on && max != null && value.length >= max} onClick={() => onChange(on ? value.filter((v) => v !== o) : [...value, o])}>
            {label(labels, o)}
          </Chip>
        );
      })}
    </>
  );
}

export function CustomExerciseForm() {
  const router = useRouter();
  const [draft, setDraft] = useState<CustomExerciseDraft>({
    name: "",
    primary: [],
    secondary: [],
    pattern: "HORIZONTAL_PUSH",
    equipment: [],
    unilateral: false,
    instructions: null,
  });
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    start(async () => {
      const result = await createCustomExercise(draft);
      if (!result.ok) setError(result.error);
      else router.push(`/progress/${result.id}`);
    });
  }

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-4">
      <Card className="space-y-4">
        <Field label="Name">
          <input required maxLength={60} className={inputClass} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </Field>
        <Field label="Movement" hint="Used to suggest swaps and to tell compound from isolation lifts.">
          <select className={inputClass} value={draft.pattern} onChange={(e) => setDraft({ ...draft, pattern: e.target.value as CustomExerciseDraft["pattern"] })}>
            {MOVEMENT_PATTERNS.map((p) => (
              <option key={p} value={p}>
                {label(PATTERN_LABELS, p)}
              </option>
            ))}
          </select>
        </Field>
        <ChipGroup label="Main muscles (up to 3)">
          <Toggles options={MUSCLES} labels={MUSCLE_LABELS} value={draft.primary} max={3} onChange={(primary) => setDraft({ ...draft, primary, secondary: draft.secondary.filter((m) => !primary.includes(m)) })} />
        </ChipGroup>
        <ChipGroup label="Also works (optional)">
          <Toggles options={MUSCLES.filter((m) => !draft.primary.includes(m))} labels={MUSCLE_LABELS} value={draft.secondary} max={5} onChange={(secondary) => setDraft({ ...draft, secondary })} />
        </ChipGroup>
        <ChipGroup label="Equipment" hint="None means bodyweight.">
          <Toggles options={EQUIPMENT} labels={EQUIPMENT_LABELS} value={draft.equipment} max={5} onChange={(equipment) => setDraft({ ...draft, equipment })} />
        </ChipGroup>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={draft.unilateral} onChange={(e) => setDraft({ ...draft, unilateral: e.target.checked })} />
          One side at a time
        </label>
        <Field label="How to (optional)" hint="One cue per line.">
          <textarea rows={4} maxLength={1000} className={inputClass} value={draft.instructions ?? ""} onChange={(e) => setDraft({ ...draft, instructions: e.target.value || null })} />
        </Field>
      </Card>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" variant="primary" disabled={pending || !draft.name.trim() || !draft.primary.length}>
          {pending ? "Creating…" : "Create exercise"}
        </Button>
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
      </div>
    </form>
  );
}
