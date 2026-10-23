"use client";

import { Button, Card } from "@/components/ui";

/** Shown when a page's data fails to load or an action throws, instead of a blank error. */
export default function AppError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <Card className="mx-auto mt-10 max-w-md py-10 text-center">
      <div className="font-medium">Something went wrong</div>
      <p className="mt-2 text-sm text-muted">
        {error.digest ? "The page couldn't load your data. Try again in a moment." : error.message}
      </p>
      <Button variant="primary" className="mt-5" onClick={reset}>Try again</Button>
    </Card>
  );
}
