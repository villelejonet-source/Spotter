import { BackLink, PageHeader } from "@/components/ui";
import { exerciseOptions } from "../options";
import { NewPlan } from "./new-plan";

export const metadata = { title: "New plan" };

export default async function NewPlanPage() {
  const { exercises, unit } = await exerciseOptions();
  return (
    <>
      <BackLink href="/plans">Plans</BackLink>
      <PageHeader title="New plan" subtitle="Your first plan becomes the active one in the app." />
      <NewPlan exercises={exercises} unit={unit} />
    </>
  );
}
