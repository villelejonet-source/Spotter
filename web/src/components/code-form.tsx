"use client";

import { useEffect, useState } from "react";
import { Button, inputClass } from "./ui";

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

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setError(await onVerify(code.replace(/\D/g, "")));
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
      <input
        inputMode="numeric"
        autoComplete="one-time-code"
        autoFocus
        required
        pattern="[0-9 ]{6,10}"
        placeholder="Code from the email"
        aria-label="Code from the email"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        className={`${inputClass} text-center text-lg tracking-[0.3em]`}
      />
      <Button variant="primary" className="w-full" disabled={busy || code.replace(/\D/g, "").length < 6}>
        {busy ? "Checking…" : submitLabel}
      </Button>
      {onResend && (
        <button type="button" onClick={resend} disabled={cooldown > 0} className="w-full text-sm text-muted hover:text-foreground disabled:opacity-60">
          {cooldown > 0 ? `Resend email in ${cooldown}s` : "Resend email"}
        </button>
      )}
      {notice && !error && <p role="status" className="text-sm text-muted">{notice}</p>}
      {error && <p role="alert" className="text-sm text-error">{error}</p>}
    </form>
  );
}
