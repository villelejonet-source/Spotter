"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { createCustomExercise } from "@/app/actions/library";
import { Button, Card, inputClass } from "@/components/ui";
import { EQUIPMENT, enumLabel, MOVEMENT_PATTERNS, MUSCLES } from "@/lib/model";
import type { CustomExerciseDraft } from "@/lib/sync-writer";

function Toggles<T extends string>({ options, value, onChange, max }: { options: readonly T[]; value: T[]; onChange: (v: T[]) => void; max?: number }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((o) => {
        const on = value.includes(o);
        return (
          <button
            key={o}
            type="button"
            disabled={!on && max != null && value.length >= max}
            onClick={() => onChange(on ? value.filter((v) => v !== o) : [...value, o])}
            className={clsx(
              "rounded-full px-2.5 py-1 text-xs transition disabled:opacity-40",
              on ? "bg-primary-container text-on-primary-container" : "bg-surface-high text-muted hover:text-foreground",
            )}
          >
            {enumLabel(o)}
          </button>
        );
      })}
    </div>
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
        <label className="block space-y-1 text-sm">
          <span className="text-muted">Name</span>
          <input required maxLength={60} className={inputClass} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </label>
        <label className="block space-y-1 text-sm">
          <span className="text-muted">Movement</span>
          <select className={inputClass} value={draft.pattern} onChange={(e) => setDraft({ ...draft, pattern: e.target.value as CustomExerciseDraft["pattern"] })}>
            {MOVEMENT_PATTERNS.map((p) => (
              <option key={p} value={p}>{enumLabel(p)}</option>
            ))}
          </select>
          <span className="block text-xs text-muted">Used to suggest swaps and to tell compound from isolation lifts.</span>
        </label>
        <div className="space-y-1.5 text-sm">
          <span className="text-muted">Main muscles (up to 3)</span>
          <Toggles options={MUSCLES} value={draft.primary} max={3} onChange={(primary) => setDraft({ ...draft, primary, secondary: draft.secondary.filter((m) => !primary.includes(m)) })} />
        </div>
        <div className="space-y-1.5 text-sm">
          <span className="text-muted">Also works (optional)</span>
          <Toggles options={MUSCLES.filter((m) => !draft.primary.includes(m))} value={draft.secondary} max={5} onChange={(secondary) => setDraft({ ...draft, secondary })} />
        </div>
        <div className="space-y-1.5 text-sm">
          <span className="text-muted">Equipment (none means bodyweight)</span>
          <Toggles options={EQUIPMENT} value={draft.equipment} max={5} onChange={(equipment) => setDraft({ ...draft, equipment })} />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={draft.unilateral} onChange={(e) => setDraft({ ...draft, unilateral: e.target.checked })} />
          One side at a time
        </label>
        <label className="block space-y-1 text-sm">
          <span className="text-muted">How to (optional, one cue per line)</span>
          <textarea
            rows={4}
            maxLength={1000}
            className={inputClass}
            value={draft.instructions ?? ""}
            onChange={(e) => setDraft({ ...draft, instructions: e.target.value || null })}
          />
        </label>
      </Card>
      <div className="flex items-center gap-3">
        <Button variant="primary" disabled={pending || !draft.name.trim() || !draft.primary.length}>
          {pending ? "Creating…" : "Create exercise"}
        </Button>
        {error && <span className="text-sm text-error">{error}</span>}
      </div>
    </form>
  );
}
