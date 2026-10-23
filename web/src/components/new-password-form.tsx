"use client";

import { useState } from "react";
import { FormMessage } from "./auth-ui";
import { Button, inputClass } from "./ui";
import { authErrorMessage, MIN_PASSWORD_LENGTH, passwordProblem } from "@/lib/auth";
import { createClient } from "@/lib/supabase/client";

/**
 * Sets the signed-in user's password. If the project requires a recent sign-in to change
 * it ("secure password change"), Supabase emails a code first and this asks for it.
 */
export function NewPasswordForm({ submitLabel, onDone }: { submitLabel: string; onDone: () => void }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [nonce, setNonce] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const supabase = createClient();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const problem = passwordProblem(password, confirm);
    if (problem) return setError(problem);
    setBusy(true);
    setError(null);
    const { error } = await supabase.auth.updateUser(nonce ? { password, nonce: nonce.replace(/\D/g, "") } : { password });
    if (error?.code === "reauthentication_needed") {
      const { error: sendError } = await supabase.auth.reauthenticate();
      setBusy(false);
      if (sendError) return setError(authErrorMessage(sendError));
      setNonce("");
      return setError(null);
    }
    setBusy(false);
    if (error) return setError(authErrorMessage(error));
    setPassword("");
    setConfirm("");
    setNonce(null);
    onDone();
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <input
        type="password"
        required
        minLength={MIN_PASSWORD_LENGTH}
        autoComplete="new-password"
        placeholder={`New password (at least ${MIN_PASSWORD_LENGTH} characters)`}
        aria-label="New password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        className={inputClass}
      />
      <input
        type="password"
        required
        autoComplete="new-password"
        placeholder="Repeat new password"
        aria-label="Repeat new password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        className={inputClass}
      />
      {nonce != null && (
        <>
          <FormMessage tone="info">For your security we emailed you a code. Enter it to confirm the change.</FormMessage>
          <input
            inputMode="numeric"
            autoComplete="one-time-code"
            required
            placeholder="Code from the email"
            aria-label="Code from the email"
            value={nonce}
            onChange={(e) => setNonce(e.target.value)}
            className={`${inputClass} text-center tracking-[0.3em]`}
          />
        </>
      )}
      <Button variant="primary" className="w-full" disabled={busy}>
        {busy ? "Saving…" : submitLabel}
      </Button>
      {error && <FormMessage tone="error">{error}</FormMessage>}
    </form>
  );
}
