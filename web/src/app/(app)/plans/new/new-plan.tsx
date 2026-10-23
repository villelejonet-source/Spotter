"use client";

import { useState } from "react";
import { newPlanDraft, PlanEditor, type ExerciseOption } from "@/components/plan-editor";
import type { WeightUnit } from "@/lib/model";

/** Makes the draft (and its random ids) once, on the client. */
export function NewPlan({ exercises, unit }: { exercises: ExerciseOption[]; unit: WeightUnit }) {
  const [draft] = useState(newPlanDraft);
  return <PlanEditor initial={draft} exercises={exercises} unit={unit} isNew />;
}
