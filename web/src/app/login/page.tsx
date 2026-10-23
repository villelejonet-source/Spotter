"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Card, inputClass } from "@/components/ui";
import { createClient } from "@/lib/supabase/client";

/**
 * Passwordless sign-in with the same account as the app: the email has a link (back
 * to /auth/callback) and a 6-digit code that can be typed in here.
 */
export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function sendCode(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { error } = await createClient().auth.signInWithOtp({
      email: email.trim(),
      options: { emailRedirectTo: `${window.location.origin}/auth/callback`, shouldCreateUser: true },
    });
    setBusy(false);
    if (error) setError(error.message);
    else setSent(true);
  }

  async function verify(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { error } = await createClient().auth.verifyOtp({ email: email.trim(), token: code.trim(), type: "email" });
    setBusy(false);
    if (error) setError(error.message);
    else router.replace("/");
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-sm p-8">
        <div className="mb-6">
          <div className="text-sm font-semibold text-primary">Spotter</div>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Sign in</h1>
          <p className="mt-1 text-sm text-muted">Use the same email as cloud backup in the app.</p>
        </div>
        {!sent ? (
          <form onSubmit={sendCode} className="space-y-3">
            <input
              type="email"
              required
              autoFocus
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClass}
            />
            <Button variant="primary" className="w-full" disabled={busy}>
              {busy ? "Sending…" : "Email me a sign-in code"}
            </Button>
          </form>
        ) : (
          <form onSubmit={verify} className="space-y-3">
            <p className="text-sm text-muted">
              We sent a code to <span className="font-medium text-foreground">{email}</span>. Enter it below or open the link in the
              email on this device.
            </p>
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              autoFocus
              required
              placeholder="6-digit code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              className={`${inputClass} text-center text-lg tracking-[0.3em]`}
            />
            <Button variant="primary" className="w-full" disabled={busy}>
              {busy ? "Checking…" : "Sign in"}
            </Button>
            <Button type="button" variant="ghost" className="w-full" onClick={() => setSent(false)}>
              Use another email
            </Button>
          </form>
        )}
        {error && <p className="mt-3 text-sm text-error">{error}</p>}
      </Card>
    </main>
  );
}
