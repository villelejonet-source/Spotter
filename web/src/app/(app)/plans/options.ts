import type { ExerciseOption } from "@/components/plan-editor";
import { getCatalog, getUnit } from "@/lib/data";

/** The exercise list for the plan editor's picker, alphabetical. */
export async function exerciseOptions() {
  const [catalog, unit] = await Promise.all([getCatalog(), getUnit()]);
  const exercises: ExerciseOption[] = [...catalog.values()]
    .map((e) => ({ id: e.id, name: e.name, primary: e.primary, equipment: e.equipment, isCustom: e.isCustom }))
    .sort((a, b) => a.name.localeCompare(b.name));
  return { exercises, unit };
}
