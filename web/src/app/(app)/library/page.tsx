import { LinkButton, PageHeader } from "@/components/ui";
import { getCatalog, getExerciseUsage } from "@/lib/data";
import { ExerciseBrowser, type LibraryExercise } from "./exercise-browser";

export const metadata = { title: "Exercises" };

export default async function LibraryPage() {
  const [catalog, usage] = await Promise.all([getCatalog(), getExerciseUsage()]);
  // Only what the browser renders: no instructions, which are half the catalog's bytes.
  const exercises: LibraryExercise[] = [...catalog.values()]
    .map((e) => ({
      id: e.id,
      name: e.name,
      primary: e.primary,
      secondary: e.secondary,
      equipment: e.equipment,
      mechanics: e.mechanics,
      isCustom: e.isCustom,
      sessions: usage.get(e.id) ?? 0,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <>
      <PageHeader
        title="Exercises"
        subtitle={`${exercises.length} exercises · custom ones sync to the app.`}
        actions={
          <LinkButton href="/library/new" variant="primary">
            Create exercise
          </LinkButton>
        }
      />
      <ExerciseBrowser exercises={exercises} />
    </>
  );
}
