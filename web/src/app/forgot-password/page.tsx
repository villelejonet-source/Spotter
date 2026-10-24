"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, FormMessage } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, inputClass } from "@/components/ui";
import { authErrorMessage } from "@/lib/auth";
import { createClient } from "@/lib/supabase/client";

export default function ForgotPasswordPage() {
  return (
    <Suspense>
      <ForgotPassword />
    </Suspense>
  );
}

/**
 * Emails a reset link (and code). Either signs the user in for one purpose: choosing a new
 * password on /reset-password. Also how accounts made in the app get their first password.
 */
function ForgotPassword() {
  const router = useRouter();
  const [email, setEmail] = useState(useSearchParams().get("email") ?? "");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const supabase = createClient();

  async function sendLink(): Promise<string | null> {
    const { error } = await supabase.auth.resetPasswordForEmail(email.trim(), {
      redirectTo: `${location.origin}/auth/callback?next=/reset-password`,
    });
    return error ? authErrorMessage(error) : null;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const problem = await sendLink();
    setBusy(false);
    if (problem) setError(problem);
    else setSent(true);
  }

  if (sent) {
    return (
      <AuthCard
        title="Check your email"
        subtitle={
          <>
            If there&apos;s an account for <span className="font-medium text-foreground">{email}</span>, we sent a link to choose a
            new password. Open it on this device, or enter the code from the email.
          </>
        }
        footer={<Link href="/login" className="hover:text-foreground">← Back to sign in</Link>}
      >
        <CodeForm
          email={email}
          submitLabel="Continue"
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email: email.trim(), token, type: "recovery" });
            if (error) return authErrorMessage(error);
            router.replace("/reset-password");
            return null;
          }}
          onResend={sendLink}
        />
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Reset your password"
      subtitle="We'll email you a link to choose a new one. Works for accounts made in the app too."
      footer={<Link href="/login" className="hover:text-foreground">← Back to sign in</Link>}
    >
      <form onSubmit={submit} className="space-y-3">
        <input type="email" required autoFocus autoComplete="email" placeholder="Email" aria-label="Email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        <Button variant="primary" className="w-full" disabled={busy}>
          {busy ? "Sending…" : "Email me a reset link"}
        </Button>
        {error && <FormMessage tone="error">{error}</FormMessage>}
      </form>
    </AuthCard>
  );
}
