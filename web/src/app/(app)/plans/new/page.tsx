import Link from "next/link";
import { PageHeader } from "@/components/ui";
import { exerciseOptions } from "../options";
import { NewPlan } from "./new-plan";

export default async function NewPlanPage() {
  const { exercises, unit } = await exerciseOptions();
  return (
    <>
      <Link href="/plans" className="text-sm text-muted hover:text-foreground">← Plans</Link>
      <div className="mt-2">
        <PageHeader title="New plan" subtitle="Your first plan becomes the active one in the app." />
      </div>
      <NewPlan exercises={exercises} unit={unit} />
    </>
  );
}
