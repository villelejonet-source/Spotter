"use client";

import clsx from "clsx";
import Link from "next/link";
import { useState } from "react";
import { Badge, Card, Chip, ChipGroup, Field, inputClass } from "@/components/ui";
import { BODYWEIGHT, EQUIPMENT_LABELS, label, MECHANICS_LABELS, MUSCLE_LABELS } from "@/lib/labels";
import { EQUIPMENT, MUSCLE_GROUPS } from "@/lib/model";

export interface LibraryExercise {
  id: string;
  name: string;
  primary: string[];
  secondary: string[];
  equipment: string[];
  mechanics: string;
  isCustom: boolean;
  /** Workouts the exercise was logged in. */
  sessions: number;
}

const SHOW = [
  { id: "all", label: "All" },
  { id: "done", label: "Done before" },
  { id: "custom", label: "Custom" },
] as const;

export function ExerciseBrowser({ exercises }: { exercises: LibraryExercise[] }) {
  const [query, setQuery] = useState("");
  const [group, setGroup] = useState<string | null>(null);
  const [equipment, setEquipment] = useState<string | null>(null);
  const [show, setShow] = useState<(typeof SHOW)[number]["id"]>("all");

  const q = query.trim().toLowerCase();
  const muscles = group ? (MUSCLE_GROUPS[group] as readonly string[]) : null;
  const results = exercises.filter(
    (e) =>
      (!q || e.name.toLowerCase().includes(q)) &&
      (!muscles || e.primary.some((m) => muscles.includes(m))) &&
      (!equipment || (equipment === "NONE" ? e.equipment.length === 0 : e.equipment.includes(equipment))) &&
      (show === "all" || (show === "done" ? e.sessions > 0 : e.isCustom)),
  );

  return (
    <div className="space-y-4">
      <Card className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end">
          <Field label="Search">
            <input type="search" className={inputClass} placeholder="Exercise name" value={query} onChange={(e) => setQuery(e.target.value)} />
          </Field>
          <Field label="Equipment">
            <select className={clsx(inputClass, "sm:w-auto")} value={equipment ?? ""} onChange={(e) => setEquipment(e.target.value || null)}>
              <option value="">Any equipment</option>
              <option value="NONE">{BODYWEIGHT} only</option>
              {EQUIPMENT.map((x) => (
                <option key={x} value={x}>
                  {label(EQUIPMENT_LABELS, x)}
                </option>
              ))}
            </select>
          </Field>
          <ChipGroup label="Show">
            {SHOW.map((s) => (
              <Chip key={s.id} on={show === s.id} onClick={() => setShow(s.id)}>
                {s.label}
              </Chip>
            ))}
          </ChipGroup>
        </div>
        <ChipGroup label="Muscle group">
          {[null, ...Object.keys(MUSCLE_GROUPS)].map((g) => (
            <Chip key={g ?? "all"} on={group === g} onClick={() => setGroup(g)}>
              {g ?? "All"}
            </Chip>
          ))}
        </ChipGroup>
      </Card>

      <p className="text-sm text-muted" role="status">
        {results.length} exercises
      </p>
      {results.length ? (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {results.map((e) => (
            <li key={e.id}>
              <Link href={`/progress/${e.id}`} className="group block h-full rounded-2xl">
                <Card className="h-full transition group-hover:border-primary group-focus-visible:border-primary">
                  <div className="flex items-start justify-between gap-2">
                    <div className="font-medium">{e.name}</div>
                    {e.isCustom && <Badge>Custom</Badge>}
                  </div>
                  <div className="mt-1 text-xs text-muted">
                    {e.primary.map((m) => label(MUSCLE_LABELS, m)).join(", ")}
                    {e.secondary.length > 0 && <span className="opacity-70"> · {e.secondary.map((m) => label(MUSCLE_LABELS, m)).join(", ")}</span>}
                  </div>
                  <div className="mt-3 flex flex-wrap gap-1">
                    <Badge tone="tertiary">{label(MECHANICS_LABELS, e.mechanics)}</Badge>
                    {e.equipment.length ? e.equipment.map((x) => <Badge key={x}>{label(EQUIPMENT_LABELS, x)}</Badge>) : <Badge>{BODYWEIGHT}</Badge>}
                  </div>
                  {e.sessions > 0 && (
                    <div className="mt-3 text-xs text-primary">
                      Done in {e.sessions} workout{e.sessions > 1 ? "s" : ""}
                    </div>
                  )}
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <Card className="py-8 text-center text-sm text-muted">No exercises match. Try another search or create your own.</Card>
      )}
    </div>
  );
}
