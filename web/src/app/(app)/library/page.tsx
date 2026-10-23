import { LinkButton, PageHeader } from "@/components/ui";
import { getCatalog, getSetHistory } from "@/lib/data";
import { ExerciseBrowser, type LibraryExercise } from "./exercise-browser";

export default async function LibraryPage() {
  const [catalog, sets] = await Promise.all([getCatalog(), getSetHistory()]);
  const logged = new Map<string, Set<string>>();
  for (const s of sets) {
    if (s.completed_at == null) continue;
    logged.set(s.exercise_id, (logged.get(s.exercise_id) ?? new Set()).add(s.session_id));
  }
  const exercises: LibraryExercise[] = [...catalog.values()]
    .map((e) => ({
      id: e.id,
      name: e.name,
      primary: e.primary,
      secondary: e.secondary,
      equipment: e.equipment,
      mechanics: e.mechanics,
      difficulty: e.difficulty,
      isCustom: e.isCustom,
      sessions: logged.get(e.id)?.size ?? 0,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <>
      <PageHeader
        title="Exercises"
        subtitle={`${exercises.length} exercises · custom ones sync to the app`}
        actions={<LinkButton href="/library/new" variant="primary">New exercise</LinkButton>}
      />
      <ExerciseBrowser exercises={exercises} />
    </>
  );
}
