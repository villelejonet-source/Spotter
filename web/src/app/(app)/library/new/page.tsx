import Link from "next/link";
import { PageHeader } from "@/components/ui";
import { CustomExerciseForm } from "./custom-exercise-form";

export default function NewExercisePage() {
  return (
    <>
      <Link href="/library" className="text-sm text-muted hover:text-foreground">← Exercises</Link>
      <div className="mt-2">
        <PageHeader title="New exercise" subtitle="Available in your plans here and in the app after it syncs." />
      </div>
      <CustomExerciseForm />
    </>
  );
}
