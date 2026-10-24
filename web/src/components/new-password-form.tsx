"use client";

import { useState } from "react";
import { useAuthForm } from "./auth-ui";
import { Button, Field, InlineMessage, inputClass } from "./ui";
import { authErrorMessage, MIN_PASSWORD_LENGTH, passwordProblem } from "@/lib/auth";

/**
 * Sets the signed-in user's password. If the project requires a recent sign-in to change
 * it ("secure password change"), Supabase emails a code first and this asks for it.
 */
export function NewPasswordForm({ submitLabel, onDone }: { submitLabel: string; onDone: () => void }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [nonce, setNonce] = useState<string | null>(null);
  const { busy, error, setError, run, supabase } = useAuthForm();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const problem = passwordProblem(password, confirm);
    if (problem) return setError(problem);
    const ok = await run(async () => {
      const { error } = await supabase.auth.updateUser(nonce ? { password, nonce: nonce.replace(/\D/g, "") } : { password });
      if (error?.code === "reauthentication_needed") {
        const { error: sendError } = await supabase.auth.reauthenticate();
        if (sendError) return authErrorMessage(sendError);
        setNonce("");
        return null;
      }
      return error ? authErrorMessage(error) : null;
    });
    if (!ok || nonce === "") return; // a code was just requested: wait for it
    setPassword("");
    setConfirm("");
    setNonce(null);
    onDone();
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <Field label="New password" hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}>
        <input type="password" required minLength={MIN_PASSWORD_LENGTH} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass} />
      </Field>
      <Field label="Repeat new password">
        <input type="password" required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} className={inputClass} />
      </Field>
      {nonce != null && (
        <>
          <InlineMessage tone="info">For your security we emailed you a code. Enter it to confirm the change.</InlineMessage>
          <Field label="Code from the email">
            <input inputMode="numeric" autoComplete="one-time-code" required value={nonce} onChange={(e) => setNonce(e.target.value)} className={`${inputClass} text-center tracking-[0.3em]`} />
          </Field>
        </>
      )}
      <Button type="submit" variant="primary" className="w-full" disabled={busy}>
        {busy ? "Saving…" : submitLabel}
      </Button>
      {error && <InlineMessage tone="error">{error}</InlineMessage>}
    </form>
  );
}
