"use client";

import { useState, useTransition } from "react";
import { deleteAccount } from "@/app/actions/account";
import { Button } from "./ui";

export function DeleteAccount() {
  const [confirming, setConfirming] = useState(false);
  const [pending, start] = useTransition();
  if (!confirming) {
    return (
      <Button variant="danger" onClick={() => setConfirming(true)}>
        Delete account…
      </Button>
    );
  }
  return (
    <div className="space-y-3">
      <p className="text-sm">
        This deletes your account and the cloud backup for good. Data on your phone stays, but stops syncing.
      </p>
      <div className="flex gap-2">
        <Button variant="danger" disabled={pending} onClick={() => start(() => deleteAccount())}>
          {pending ? "Deleting…" : "Delete everything"}
        </Button>
        <Button variant="ghost" onClick={() => setConfirming(false)}>Cancel</Button>
      </div>
    </div>
  );
}
