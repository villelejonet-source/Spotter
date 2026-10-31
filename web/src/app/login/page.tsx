"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, useAuthForm } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, Field, InlineMessage, inputClass } from "@/components/ui";
import { authErrorMessage } from "@/lib/auth";

/** Messages the auth callback (A) and sign-out send here with ?notice= or ?error=. */
const NOTICES: Record<string, string> = {
  link: "That link has expired or was already used. Sign in, or ask for a new email.",
  "link-other-browser": "That link couldn't sign you in here, usually because it opened in a different browser. If you were confirming your email, it's confirmed: sign in below.",
  "signed-out": "You're signed out.",
  "account-deleted": "Your account and backup are deleted. The data on your phone stays there.",
};

type Mode =
  | { kind: "password" }
  | { kind: "unconfirmed" } // email not confirmed yet: enter the sign-up code
  | { kind: "code-sent" }; // passwordless: enter the sign-in code

export default function LoginPage() {
  return (
    <Suspense>
      <Login />
    </Suspense>
  );
}

/**
 * Email and password. Accounts made in the app have no password yet; they sign in with a
 * code instead (and can add a password on the Profile page), or use "Forgot password".
 */
function Login() {
  const router = useRouter();
  const params = useSearchParams();
  const notice = NOTICES[params.get("notice") ?? params.get("error") ?? ""];
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<Mode>({ kind: "password" });
  const { email, rawEmail, setEmail, busy, error, setError, run, supabase } = useAuthForm();
  const done = () => router.replace("/");
  const callback = () => `${location.origin}/auth/callback`;

  async function signIn(e: React.FormEvent) {
    e.preventDefault();
    await run(async () => {
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      if (!error) {
        done();
        return null;
      }
      if (error.code !== "email_not_confirmed") return authErrorMessage(error);
      // Not confirmed yet: send the confirmation again and ask for its code. If that
      // email can't be sent (rate limit, say), show why instead of an empty code form.
      const problem = await resendConfirmation();
      if (problem) return problem;
      setMode({ kind: "unconfirmed" });
      return null;
    });
  }

  /**
   * Emails a sign-in code. shouldCreateUser is off: new accounts go through sign-up, which
   * sets a password. Supabase answers an unknown email with otp_disabled; that's treated as
   * sent, so the form doesn't reveal who has an account.
   */
  async function emailSignInCode(): Promise<string | null> {
    const { error } = await supabase.auth.signInWithOtp({ email, options: { emailRedirectTo: callback(), shouldCreateUser: false } });
    return error && error.code !== "otp_disabled" ? authErrorMessage(error) : null;
  }

  async function sendCode() {
    if (!email) return setError("Enter your email first.");
    if (await run(emailSignInCode)) setMode({ kind: "code-sent" });
  }

  async function resendConfirmation(): Promise<string | null> {
    const { error } = await supabase.auth.resend({ type: "signup", email, options: { emailRedirectTo: callback() } });
    return error ? authErrorMessage(error) : null;
  }

  if (mode.kind !== "password") {
    const signup = mode.kind === "unconfirmed";
    return (
      <AuthCard
        title={signup ? "Confirm your email" : "Check your email"}
        subtitle={
          <>
            {signup ? "Your account isn't confirmed yet. We sent a new email to " : "If there's an account for "}
            <span className="font-medium text-foreground">{email}</span>
            {signup ? ". Enter the code from it, or open its link on this device." : ", we sent a sign-in code. Enter it below, or open the link in the email on this device."}
          </>
        }
        footer={
          <Button variant="ghost" onClick={() => setMode({ kind: "password" })}>
            ← Back to sign in
          </Button>
        }
      >
        <CodeForm
          email={email}
          submitLabel={signup ? "Confirm and sign in" : "Sign in"}
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email, token, type: signup ? "signup" : "email" });
            if (error) return authErrorMessage(error);
            done();
            return null;
          }}
          onResend={signup ? resendConfirmation : emailSignInCode}
        />
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Sign in"
      subtitle="Same account as cloud backup in the app."
      footer={
        <>
          New to Spotter?{" "}
          <Link href="/signup" className="rounded-md font-medium text-primary">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={signIn} className="space-y-3">
        {notice && <InlineMessage tone="info">{notice}</InlineMessage>}
        <Field label="Email">
          <input type="email" required autoFocus autoComplete="email" value={rawEmail} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        </Field>
        <Field label="Password">
          <input type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass} />
        </Field>
        <div className="text-right">
          <Link href={`/forgot-password${email ? `?email=${encodeURIComponent(email)}` : ""}`} className="tap rounded-md text-sm text-muted hover:text-foreground">
            Forgot password?
          </Link>
        </div>
        <Button type="submit" variant="primary" className="w-full" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </Button>
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
      </form>
      <div className="my-5 flex items-center gap-3 text-xs text-muted">
        <span className="h-px flex-1 bg-outline" /> or <span className="h-px flex-1 bg-outline" />
      </div>
      <Button className="w-full" onClick={sendCode} disabled={busy}>
        Email me a sign-in code
      </Button>
      <p className="mt-2 text-center text-xs text-muted">For accounts made in the app without a password.</p>
    </AuthCard>
  );
}
