"use client";

import { useState } from "react";
import { AuthCard } from "@/components/auth-ui";
import { NewPasswordForm } from "@/components/new-password-form";
import { LinkButton } from "@/components/ui";

/** Reached from the reset email (link or code), which signs the user in for this. */
export default function ResetPasswordPage() {
  const [done, setDone] = useState(false);
  if (done) {
    return (
      <AuthCard title="Password saved" subtitle="Use it next time you sign in, here or in the app's cloud backup.">
        <LinkButton href="/" variant="primary" className="w-full">Go to your dashboard</LinkButton>
      </AuthCard>
    );
  }
  return (
    <AuthCard title="Choose a new password" subtitle="You'll stay signed in on this device.">
      <NewPasswordForm submitLabel="Save new password" onDone={() => setDone(true)} />
    </AuthCard>
  );
}
