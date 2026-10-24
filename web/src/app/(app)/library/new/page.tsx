import { BackLink, PageHeader } from "@/components/ui";
import { CustomExerciseForm } from "./custom-exercise-form";

export const metadata = { title: "New exercise" };

export default function NewExercisePage() {
  return (
    <>
      <BackLink href="/library">Exercises</BackLink>
      <PageHeader title="New exercise" subtitle="Available in your plans here and in the app after it syncs." />
      <CustomExerciseForm />
    </>
  );
}
