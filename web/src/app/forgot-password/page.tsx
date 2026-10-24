"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, useAuthForm } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, Field, InlineMessage, inputClass } from "@/components/ui";
import { authErrorMessage } from "@/lib/auth";

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
  const [sent, setSent] = useState(false);
  const { email, rawEmail, setEmail, busy, error, run, supabase } = useAuthForm(useSearchParams().get("email") ?? "");

  async function sendLink(): Promise<string | null> {
    const { error } = await supabase.auth.resetPasswordForEmail(email, { redirectTo: `${location.origin}/auth/callback?next=/reset-password` });
    return error ? authErrorMessage(error) : null;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (await run(sendLink)) setSent(true);
  }

  const back = (
    <Link href="/login" className="rounded-md hover:text-foreground">
      ← Back to sign in
    </Link>
  );

  if (sent) {
    return (
      <AuthCard
        title="Check your email"
        subtitle={
          <>
            If there&apos;s an account for <span className="font-medium text-foreground">{email}</span>, we sent a link to choose a new password. Open it on this
            device, or enter the code from the email.
          </>
        }
        footer={back}
      >
        <CodeForm
          email={email}
          submitLabel="Continue"
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email, token, type: "recovery" });
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
    <AuthCard title="Reset your password" subtitle="We'll email you a link to choose a new one. Works for accounts made in the app too." footer={back}>
      <form onSubmit={submit} className="space-y-3">
        <Field label="Email">
          <input type="email" required autoFocus autoComplete="email" value={rawEmail} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        </Field>
        <Button type="submit" variant="primary" className="w-full" disabled={busy}>
          {busy ? "Sending…" : "Email me a reset link"}
        </Button>
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
      </form>
    </AuthCard>
  );
}
