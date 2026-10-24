"use client";

import { useEffect, useState } from "react";
import { Button, Field, InlineMessage, inputClass } from "./ui";

/**
 * A one-time code from an email (Supabase sends 6–8 digits, depending on the project),
 * with a resend button that waits a minute between sends.
 */
export function CodeForm({
  email,
  onVerify,
  onResend,
  submitLabel = "Continue",
}: {
  email: string;
  onVerify: (code: string) => Promise<string | null>;
  onResend?: () => Promise<string | null>;
  submitLabel?: string;
}) {
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(60);
  const digits = code.replace(/\D/g, "");

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setError(await onVerify(digits));
    setBusy(false);
  }

  async function resend() {
    if (!onResend) return;
    setError(null);
    const problem = await onResend();
    if (problem) setError(problem);
    else {
      setNotice(`Sent a new email to ${email}.`);
      setCooldown(60);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <Field label="Code from the email">
        <input
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          required
          pattern="[0-9 ]{6,10}"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          className={`${inputClass} text-center text-lg tracking-[0.3em]`}
        />
      </Field>
      <Button type="submit" variant="primary" className="w-full" disabled={busy || digits.length < 6}>
        {busy ? "Checking…" : submitLabel}
      </Button>
      {onResend && (
        <Button variant="ghost" className="w-full" onClick={resend} disabled={cooldown > 0}>
          {cooldown > 0 ? `Resend email in ${cooldown}s` : "Resend email"}
        </Button>
      )}
      {notice && !error && <InlineMessage tone="info">{notice}</InlineMessage>}
      {error && <InlineMessage tone="error">{error}</InlineMessage>}
    </form>
  );
}
