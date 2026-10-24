"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AuthCard, useAuthForm } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, Field, InlineMessage, inputClass } from "@/components/ui";
import { authErrorMessage, MIN_PASSWORD_LENGTH, passwordProblem } from "@/lib/auth";

/**
 * Sign up with email and password. Supabase emails a confirmation with a link and a code;
 * either confirms the account and signs in. Until then, sign-in is refused.
 */
export default function SignupPage() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [sent, setSent] = useState(false);
  const { email, rawEmail, setEmail, busy, error, setError, run, supabase } = useAuthForm();
  const redirect = () => `${location.origin}/auth/callback`;

  async function signUp(e: React.FormEvent) {
    e.preventDefault();
    const problem = passwordProblem(password, confirm);
    if (problem) return setError(problem);
    await run(async () => {
      const { data, error } = await supabase.auth.signUp({ email, password, options: { emailRedirectTo: redirect() } });
      if (error) return authErrorMessage(error);
      // With email confirmation on, Supabase hides whether the address is taken: an existing
      // account comes back as a user without identities and no email is sent.
      if (data.user && data.user.identities?.length === 0) return "An account with this email already exists. Sign in, or reset your password.";
      if (data.session) router.replace("/"); // confirmation turned off in the project
      else setSent(true);
      return null;
    });
  }

  if (sent) {
    return (
      <AuthCard
        title="Confirm your email"
        subtitle={
          <>
            We sent an email to <span className="font-medium text-foreground">{email}</span>. Enter the code from it, or open its link on this device.
          </>
        }
        footer={
          <Button variant="ghost" onClick={() => setSent(false)}>
            ← Use a different email
          </Button>
        }
      >
        <CodeForm
          email={email}
          submitLabel="Confirm and sign in"
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email, token, type: "signup" });
            if (error) return authErrorMessage(error);
            router.replace("/");
            return null;
          }}
          onResend={async () => {
            const { error } = await supabase.auth.resend({ type: "signup", email, options: { emailRedirectTo: redirect() } });
            return error ? authErrorMessage(error) : null;
          }}
        />
        <p className="mt-4 text-xs text-muted">No email after a few minutes? Check spam, or resend.</p>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Use it here and for cloud backup in the app."
      footer={
        <>
          Already have an account?{" "}
          <Link href="/login" className="rounded-md font-medium text-primary">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={signUp} className="space-y-3">
        <Field label="Email">
          <input type="email" required autoFocus autoComplete="email" value={rawEmail} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        </Field>
        <Field label="Password" hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}>
          <input type="password" required minLength={MIN_PASSWORD_LENGTH} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass} />
        </Field>
        <Field label="Repeat password">
          <input type="password" required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} className={inputClass} />
        </Field>
        <Button type="submit" variant="primary" className="w-full" disabled={busy}>
          {busy ? "Creating account…" : "Create account"}
        </Button>
        {error && <InlineMessage tone="error">{error}</InlineMessage>}
        <p className="text-xs text-muted">Your training data is only used to run Spotter. It&apos;s never sold or shared.</p>
      </form>
    </AuthCard>
  );
}
