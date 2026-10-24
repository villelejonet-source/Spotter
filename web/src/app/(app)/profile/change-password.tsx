"use client";

import { useState } from "react";
import { FormMessage } from "@/components/auth-ui";
import { NewPasswordForm } from "@/components/new-password-form";
import { Button } from "@/components/ui";

/** Change the password, or set a first one for an account made in the app with email codes. */
export function ChangePassword() {
  const [open, setOpen] = useState(false);
  const [saved, setSaved] = useState(false);
  if (!open) {
    return (
      <div className="space-y-3">
        {saved && <FormMessage tone="info">Password saved.</FormMessage>}
        <Button onClick={() => { setSaved(false); setOpen(true); }}>Change password</Button>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <NewPasswordForm submitLabel="Save password" onDone={() => { setSaved(true); setOpen(false); }} />
      <Button variant="ghost" className="w-full" onClick={() => setOpen(false)}>Cancel</Button>
    </div>
  );
}
