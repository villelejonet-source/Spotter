"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, useAuthForm } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, Field, InlineMessage, inputClass } from "@/components/ui";
import { authErrorMessage, safeNext } from "@/lib/auth";

/** Messages the auth callback (A) and sign-out send here with ?notice= or ?error=. */
const NOTICES: Record<string, string> = {
  link: "That link has expired or was already used. Ask for a new code below.",
  "link-other-browser": "That link couldn't sign you in here, usually because it opened in a different browser. Ask for a new code below and type it in instead.",
  "signed-out": "You're signed out.",
  "account-deleted": "Your account and backup are deleted. The data on your phone stays there.",
};

type Mode =
  | { kind: "email" } // the way in for everyone: email, then the code
  | { kind: "password" } // for those who set one
  | { kind: "code-sent"; unconfirmed: boolean };

export default function LoginPage() {
  return (
    <Suspense>
      <Login />
    </Suspense>
  );
}

/**
 * One way in: an email, then the code from it. A new address gets an account, so there's no
 * separate sign-up, and the code also confirms an account that never was. A password is an
 * option for those who set one (in the app or on the Profile page).
 */
function Login() {
  const router = useRouter();
  const params = useSearchParams();
  const notice = NOTICES[params.get("notice") ?? params.get("error") ?? ""];
  const next = safeNext(params.get("next"));
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<Mode>({ kind: "email" });
  const { email, rawEmail, setEmail, busy, error, setError, run, supabase } = useAuthForm();
  const done = () => router.replace(next);
  const callback = () => `${location.origin}/auth/callback${next === "/" ? "" : `?next=${encodeURIComponent(next)}`}`;

  /** Emails a code and link. An unknown address gets a new account. */
  async function emailCode(): Promise<string | null> {
    const { error } = await supabase.auth.signInWithOtp({ email, options: { emailRedirectTo: callback(), shouldCreateUser: true } });
    return error ? authErrorMessage(error) : null;
  }

  async function sendCode(e: React.FormEvent) {
    e.preventDefault();
    if (await run(emailCode)) setMode({ kind: "code-sent", unconfirmed: false });
  }

  async function signIn(e: React.FormEvent) {
    e.preventDefault();
    await run(async () => {
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      if (!error) {
        done();
        return null;
      }
      if (error.code !== "email_not_confirmed") return authErrorMessage(error);
      // Not confirmed yet: the sign-in code confirms it. If that email can't be sent (rate
      // limit, say), show why instead of an empty code form.
      const problem = await emailCode();
      if (problem) return problem;
      setMode({ kind: "code-sent", unconfirmed: true });
      return null;
    });
  }

  if (mode.kind === "code-sent") {
    return (
      <AuthCard
        title="Check your email"
        subtitle={
          <>
            {mode.unconfirmed ? "Your account isn't confirmed yet. We sent a code to " : "We sent a code to "}
            <span className="font-medium text-foreground">{email}</span>. Enter it below, or open the link in the email on this device.
          </>
        }
        footer={
          <Button variant="ghost" onClick={() => setMode({ kind: "email" })}>
            ← Use another email
          </Button>
        }
      >
        <CodeForm
          email={email}
          submitLabel="Continue"
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email, token, type: "email" });
            if (error) return authErrorMessage(error);
            done();
            return null;
          }}
          onResend={emailCode}
        />
      </AuthCard>
    );
  }

  const emailField = (
    <Field label="Email">
      <input type="email" required autoFocus autoComplete="email" value={rawEmail} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
    </Field>
  );

  if (mode.kind === "password") {
    return (
      <AuthCard
        title="Sign in"
        subtitle="With your password, the same one you use in the app."
        footer={
          <Button variant="ghost" onClick={() => { setError(null); setMode({ kind: "email" }); }}>
            Email me a code instead
          </Button>
        }
      >
        <form onSubmit={signIn} className="space-y-3">
          {emailField}
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
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Continue with email"
      subtitle="We'll send you a code. New to Spotter? The same code makes your free account, shared with cloud backup in the app."
      footer={
        <Button variant="ghost" onClick={() => { setError(null); setMode({ kind: "password" }); }}>
          Use a password instead
        </Button>
      }
    >
      <form onSubmit={sendCode} className="space-y-3">
        {notice && <InlineMessage tone="info">{notice}</InlineMessage>}
        {emailField}
        <Button type="submit" variant="primary" className="w-full" disabled={busy}>
          {busy ? "Sending…" : "Send code"}
        </Button>
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
      </form>
    </AuthCard>
  );
}
