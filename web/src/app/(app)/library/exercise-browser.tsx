"use client";

import clsx from "clsx";
import Link from "next/link";
import { useState } from "react";
import { Badge, Card, inputClass } from "@/components/ui";
import { EQUIPMENT, enumLabel, MUSCLE_GROUPS } from "@/lib/model";

export interface LibraryExercise {
  id: string;
  name: string;
  primary: string[];
  secondary: string[];
  equipment: string[];
  mechanics: string;
  difficulty: string;
  isCustom: boolean;
  /** Workouts the exercise was logged in. */
  sessions: number;
}

const chip = (on: boolean) =>
  clsx("whitespace-nowrap rounded-full px-2.5 py-1 text-xs transition", on ? "bg-primary-container text-on-primary-container" : "bg-surface-high text-muted hover:text-foreground");

export function ExerciseBrowser({ exercises }: { exercises: LibraryExercise[] }) {
  const [query, setQuery] = useState("");
  const [group, setGroup] = useState<string | null>(null);
  const [equipment, setEquipment] = useState<string | null>(null);
  const [show, setShow] = useState<"all" | "done" | "custom">("all");

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
        <div className="flex flex-wrap gap-2">
          <input className={clsx(inputClass, "max-w-sm flex-1")} placeholder="Search exercises" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className={clsx(inputClass, "w-auto")} value={equipment ?? ""} onChange={(e) => setEquipment(e.target.value || null)} aria-label="Equipment">
            <option value="">Any equipment</option>
            <option value="NONE">Bodyweight only</option>
            {EQUIPMENT.map((x) => (
              <option key={x} value={x}>{enumLabel(x)}</option>
            ))}
          </select>
          <div className="flex items-center gap-1">
            {(["all", "done", "custom"] as const).map((s) => (
              <button key={s} className={chip(show === s)} onClick={() => setShow(s)}>
                {s === "all" ? "All" : s === "done" ? "Done before" : "Custom"}
              </button>
            ))}
          </div>
        </div>
        <div className="flex gap-1 overflow-x-auto pb-1">
          {[null, ...Object.keys(MUSCLE_GROUPS)].map((g) => (
            <button key={g ?? "all"} className={chip(group === g)} onClick={() => setGroup(g)}>
              {g ?? "All muscles"}
            </button>
          ))}
        </div>
      </Card>

      <div className="text-sm text-muted">{results.length} exercises</div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {results.map((e) => (
          <Link key={e.id} href={`/progress/${e.id}`} className="group">
            <Card className="h-full p-4 transition group-hover:border-primary">
              <div className="flex items-start justify-between gap-2">
                <div className="font-medium">{e.name}</div>
                {e.isCustom && <Badge>Custom</Badge>}
              </div>
              <div className="mt-1 text-xs text-muted">
                {e.primary.map(enumLabel).join(", ")}
                {e.secondary.length > 0 && <span className="opacity-70"> · {e.secondary.map(enumLabel).join(", ")}</span>}
              </div>
              <div className="mt-3 flex flex-wrap gap-1">
                <Badge tone="tertiary">{enumLabel(e.mechanics)}</Badge>
                {e.equipment.length ? e.equipment.map((x) => <Badge key={x}>{enumLabel(x)}</Badge>) : <Badge>Bodyweight</Badge>}
              </div>
              {e.sessions > 0 && <div className="mt-3 text-xs text-primary">Done in {e.sessions} workout{e.sessions > 1 ? "s" : ""}</div>}
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
