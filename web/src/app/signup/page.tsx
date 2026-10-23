"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AuthCard, FormMessage } from "@/components/auth-ui";
import { CodeForm } from "@/components/code-form";
import { Button, inputClass } from "@/components/ui";
import { authErrorMessage, MIN_PASSWORD_LENGTH, passwordProblem } from "@/lib/auth";
import { createClient } from "@/lib/supabase/client";

/**
 * Sign up with email and password. Supabase emails a confirmation with a link and a code;
 * either confirms the account and signs in. Until then, sign-in is refused.
 */
export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const supabase = createClient();
  const redirect = () => `${location.origin}/auth/callback`;

  async function signUp(e: React.FormEvent) {
    e.preventDefault();
    const problem = passwordProblem(password, confirm);
    if (problem) return setError(problem);
    setBusy(true);
    setError(null);
    const { data, error } = await supabase.auth.signUp({ email: email.trim(), password, options: { emailRedirectTo: redirect() } });
    setBusy(false);
    if (error) return setError(authErrorMessage(error));
    // With email confirmation on, Supabase hides whether the address is taken: an existing
    // account comes back as a user without identities and no email is sent.
    if (data.user && data.user.identities?.length === 0) {
      return setError("An account with this email already exists. Sign in, or reset your password.");
    }
    if (data.session) return router.replace("/"); // confirmation turned off in the project
    setSent(true);
  }

  if (sent) {
    return (
      <AuthCard
        title="Confirm your email"
        subtitle={
          <>
            We sent an email to <span className="font-medium text-foreground">{email}</span>. Enter the code from it, or open its
            link on this device.
          </>
        }
        footer={<button className="hover:text-foreground" onClick={() => setSent(false)}>← Use a different email</button>}
      >
        <CodeForm
          email={email}
          submitLabel="Confirm and sign in"
          onVerify={async (token) => {
            const { error } = await supabase.auth.verifyOtp({ email: email.trim(), token, type: "signup" });
            if (error) return authErrorMessage(error);
            router.replace("/");
            return null;
          }}
          onResend={async () => {
            const { error } = await supabase.auth.resend({ type: "signup", email: email.trim(), options: { emailRedirectTo: redirect() } });
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
          Already have an account? <Link href="/login" className="font-medium text-primary">Sign in</Link>
        </>
      }
    >
      <form onSubmit={signUp} className="space-y-3">
        <input type="email" required autoFocus autoComplete="email" placeholder="Email" aria-label="Email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        <input
          type="password"
          required
          minLength={MIN_PASSWORD_LENGTH}
          autoComplete="new-password"
          placeholder={`Password (at least ${MIN_PASSWORD_LENGTH} characters)`}
          aria-label="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className={inputClass}
        />
        <input
          type="password"
          required
          autoComplete="new-password"
          placeholder="Repeat password"
          aria-label="Repeat password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          className={inputClass}
        />
        <Button variant="primary" className="w-full" disabled={busy}>
          {busy ? "Creating account…" : "Create account"}
        </Button>
        {error && <FormMessage tone="error">{error}</FormMessage>}
        <p className="text-xs text-muted">Your training data is only used to run Spotter. It&apos;s never sold or shared.</p>
      </form>
    </AuthCard>
  );
}
