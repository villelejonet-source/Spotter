"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, FormMessage } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, inputClass } from "@/components/ui";
import { authErrorMessage } from "@/lib/auth";
import { createClient } from "@/lib/supabase/client";

const NOTICES: Record<string, string> = {
  link: "That link has expired or was already used. Sign in, or ask for a new email.",
  "link-other-browser": "That link couldn't sign you in here, usually because it opened in a different browser. If you were confirming your email, it's confirmed: sign in below.",
  "password-updated": "Your password is updated. Sign in with it.",
  "signed-out": "You're signed out.",
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
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<Mode>({ kind: "password" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const supabase = createClient();
  const done = () => router.replace("/");

  async function signIn(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    setBusy(false);
    if (!error) return done();
    if (error.code === "email_not_confirmed") {
      await resendConfirmation();
      setMode({ kind: "unconfirmed" });
    } else setError(authErrorMessage(error));
  }

  /**
   * Emails a sign-in code. shouldCreateUser is off: new accounts go through sign-up, which
   * sets a password. Supabase answers an unknown email with otp_disabled; that's treated as
   * sent, so the form doesn't reveal who has an account.
   */
  async function emailSignInCode(): Promise<string | null> {
    const { error } = await supabase.auth.signInWithOtp({
      email: email.trim(),
      options: { emailRedirectTo: `${location.origin}/auth/callback`, shouldCreateUser: false },
    });
    return error && error.code !== "otp_disabled" ? authErrorMessage(error) : null;
  }

  async function sendCode() {
    if (!email.trim()) return setError("Enter your email first.");
    setBusy(true);
    setError(null);
    const problem = await emailSignInCode();
    setBusy(false);
    if (problem) setError(problem);
    else setMode({ kind: "code-sent" });
  }

  async function resendConfirmation(): Promise<string | null> {
    const { error } = await supabase.auth.resend({
      type: "signup",
      email: email.trim(),
      options: { emailRedirectTo: `${location.origin}/auth/callback` },
    });
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
        footer={<button className="hover:text-foreground" onClick={() => setMode({ kind: "password" })}>← Back to sign in</button>}
      >
        <CodeForm
          email={email}
          submitLabel={signup ? "Confirm and sign in" : "Sign in"}
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email: email.trim(), token, type: signup ? "signup" : "email" });
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
          New to Spotter? <Link href="/signup" className="font-medium text-primary">Create an account</Link>
        </>
      }
    >
      <form onSubmit={signIn} className="space-y-3">
        {notice && <FormMessage tone="info">{notice}</FormMessage>}
        <input
          type="email"
          required
          autoFocus
          autoComplete="email"
          placeholder="Email"
          aria-label="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className={inputClass}
        />
        <input
          type="password"
          required
          autoComplete="current-password"
          placeholder="Password"
          aria-label="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className={inputClass}
        />
        <div className="text-right">
          <Link href={`/forgot-password${email ? `?email=${encodeURIComponent(email.trim())}` : ""}`} className="text-sm text-muted hover:text-foreground">
            Forgot password?
          </Link>
        </div>
        <Button variant="primary" className="w-full" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </Button>
        {error && <FormMessage tone="error">{error}</FormMessage>}
      </form>
      <div className="my-5 flex items-center gap-3 text-xs text-muted">
        <span className="h-px flex-1 bg-outline" /> or <span className="h-px flex-1 bg-outline" />
      </div>
      <Button type="button" className="w-full" onClick={sendCode} disabled={busy}>
        Email me a sign-in code
      </Button>
      <p className="mt-2 text-center text-xs text-muted">For accounts made in the app without a password.</p>
    </AuthCard>
  );
}
