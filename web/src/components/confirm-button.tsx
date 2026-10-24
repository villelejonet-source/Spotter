"use client";

import { useState, useTransition, type ReactNode } from "react";
import { ConfirmDialog } from "./dialog";
import { Button, InlineMessage, type ButtonVariant } from "./ui";

/**
 * A button that asks before running a server action (sign out, make a plan active,
 * delete). The action is passed in bound, so this works from server components.
 */
export function ConfirmButton({
  action,
  title,
  body,
  confirmLabel,
  variant = "secondary",
  confirmVariant = variant === "danger" ? "danger" : "primary",
  className,
  children,
}: {
  action: () => Promise<void | { ok: boolean; error?: string }>;
  title: string;
  body?: ReactNode;
  confirmLabel: string;
  variant?: ButtonVariant;
  confirmVariant?: ButtonVariant;
  className?: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  function confirm() {
    setError(null);
    start(async () => {
      const result = await action();
      if (result && !result.ok) {
        setError(result.error ?? "Something went wrong. Try again.");
        return;
      }
      setOpen(false);
    });
  }

  return (
    <>
      <Button variant={variant} className={className} onClick={() => setOpen(true)} disabled={pending}>
        {pending ? "Working…" : children}
      </Button>
      <ConfirmDialog
        open={open}
        onClose={() => !pending && setOpen(false)}
        onConfirm={confirm}
        title={title}
        body={
          <>
            {body}
            {error && <InlineMessage tone="error" className="mt-3">{error}</InlineMessage>}
          </>
        }
        confirmLabel={confirmLabel}
        variant={confirmVariant}
        pending={pending}
      />
    </>
  );
}
