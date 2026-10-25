import { EmptyState, LinkButton, PageHeader } from "@/components/ui";

/** A workout, exercise or plan that isn't there (deleted, or never yours). */
export default function AppNotFound() {
  return (
    <>
      <PageHeader title="Not found" />
      <EmptyState title="There's nothing here" action={<LinkButton href="/" variant="primary">Go to Today</LinkButton>}>
        This workout, exercise or plan doesn&apos;t exist, or it was deleted on your phone.
      </EmptyState>
    </>
  );
}
