"use client";

import { useEffect } from "react";
import { Button, EmptyState } from "@/components/ui";

/** Shown when a page's data fails to load or an action throws. The error itself only goes to the console. */
export default function AppError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto mt-10 max-w-md">
      <EmptyState title="Something went wrong" action={<Button variant="primary" onClick={reset}>Try again</Button>}>
        The page couldn&apos;t load your data. Try again in a moment.
      </EmptyState>
    </div>
  );
}
