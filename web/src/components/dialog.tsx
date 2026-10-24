"use client";

import clsx from "clsx";
import { useEffect, useId, useRef, type ReactNode } from "react";
import { Button, type ButtonVariant } from "./ui";

/**
 * A modal on the native <dialog> element: the browser traps focus inside it, closes it on
 * Escape and returns focus to the opener when it closes. Clicking the backdrop closes it.
 */
export function Dialog({ open, onClose, title, children, className }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; className?: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    else if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(e) => e.target === e.currentTarget && onClose()}
      className={clsx(
        "m-auto w-full max-w-md rounded-2xl border border-outline bg-surface p-0 text-foreground shadow-xl backdrop:bg-scrim",
        "max-sm:mb-0 max-sm:max-w-full max-sm:rounded-b-none",
        className,
      )}
    >
      <div className="p-6">
        <h2 id={titleId} className="text-lg font-semibold">
          {title}
        </h2>
        {children}
      </div>
    </dialog>
  );
}

/**
 * "Are you sure?" with one action, the way the app confirms sign-out, deletions and plan
 * changes. The confirm button comes first so Enter triggers it.
 */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  body,
  confirmLabel,
  variant = "primary",
  pending = false,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  body?: ReactNode;
  confirmLabel: string;
  variant?: ButtonVariant;
  pending?: boolean;
}) {
  return (
    <Dialog open={open} onClose={onClose} title={title}>
      {body && <div className="mt-2 text-sm text-muted">{body}</div>}
      <div className="mt-6 flex flex-wrap-reverse justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={pending}>
          Cancel
        </Button>
        <Button variant={variant} onClick={onConfirm} disabled={pending} autoFocus>
          {pending ? "Working…" : confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
