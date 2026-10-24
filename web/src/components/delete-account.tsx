"use client";

import { useState, useTransition } from "react";
import { confirmDeleteAccount } from "@/app/actions/account";
import { DELETE_CONFIRMATION } from "@/lib/auth";
import { Dialog } from "./dialog";
import { Button, Field, InlineMessage, inputClass } from "./ui";

/**
 * Deletes the account after the user types DELETE (or their email) to confirm; the server
 * action checks the same text again and redirects to /login when it's done.
 */
export function DeleteAccount({ email }: { email?: string }) {
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();
  const typed = confirm.trim();
  const confirmed = typed === DELETE_CONFIRMATION || (!!email && typed.toLowerCase() === email.toLowerCase());

  function close() {
    if (pending) return;
    setOpen(false);
    setConfirm("");
    setError(null);
  }

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    start(async () => {
      const result = await confirmDeleteAccount(typed);
      if (!result.ok) setError(result.error);
    });
  }

  return (
    <>
      <Button variant="danger" onClick={() => setOpen(true)}>
        Delete account and backup…
      </Button>
      <Dialog open={open} onClose={close} title="Delete your account?">
        <form onSubmit={submit} className="mt-2 space-y-4">
          <p className="text-sm text-muted">This deletes your account and everything backed up in the cloud, for good. The data on your phone stays, but stops syncing.</p>
          <Field label={`Type ${DELETE_CONFIRMATION} to confirm`} error={error}>
            <input name="confirm" required autoComplete="off" value={confirm} onChange={(e) => setConfirm(e.target.value)} className={inputClass} />
          </Field>
          {pending && <InlineMessage tone="info">Deleting your account…</InlineMessage>}
          <div className="flex flex-wrap-reverse justify-end gap-2">
            <Button variant="ghost" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" disabled={pending || !confirmed}>
              {pending ? "Deleting…" : "Delete everything"}
            </Button>
          </div>
        </form>
      </Dialog>
    </>
  );
}
