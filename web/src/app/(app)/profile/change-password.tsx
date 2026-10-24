"use client";

import dynamic from "next/dynamic";
import { useState } from "react";
import { Button, InlineMessage } from "@/components/ui";

// The form needs the Supabase browser client (~75 kB); it loads only when the form opens.
const NewPasswordForm = dynamic(() => import("@/components/new-password-form").then((m) => m.NewPasswordForm), {
  loading: () => <div className="h-40 animate-pulse rounded-lg bg-surface-high" aria-busy="true" aria-label="Loading" />,
});

/** Change the password, or set a first one for an account made in the app with email codes. */
export function ChangePassword() {
  const [open, setOpen] = useState(false);
  const [saved, setSaved] = useState(false);
  if (!open) {
    return (
      <div className="space-y-3">
        {saved && <InlineMessage tone="success">Password saved. Use it next time you sign in, here or in the app.</InlineMessage>}
        <Button
          onClick={() => {
            setSaved(false);
            setOpen(true);
          }}
        >
          Change password
        </Button>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <NewPasswordForm
        submitLabel="Save password"
        onDone={() => {
          setSaved(true);
          setOpen(false);
        }}
      />
      <Button variant="ghost" className="w-full" onClick={() => setOpen(false)}>
        Cancel
      </Button>
    </div>
  );
}
