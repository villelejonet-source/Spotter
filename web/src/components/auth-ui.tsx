"use client";

import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { createClient } from "@/lib/supabase/client";
import { Card } from "./ui";

/** The centred card every sign-in, sign-up and password page sits in, with the privacy policy below. */
export function AuthCard({ title, subtitle, children, footer }: { title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <Card className="p-6 sm:p-8">
          <div className="mb-6">
            <Link href="/login" className="rounded-md text-sm font-semibold text-primary">
              Spotter
            </Link>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">{title}</h1>
            {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
          </div>
          {children}
        </Card>
        {footer && <div className="mt-4 text-center text-sm text-muted">{footer}</div>}
        <div className="mt-6 text-center text-xs text-muted">
          <Link href="/privacy" className="rounded-md hover:text-foreground">
            Privacy policy
          </Link>
        </div>
      </div>
    </main>
  );
}

/**
 * The state every auth page shares: the email being typed, whether a request is running,
 * and the last problem. [run] wraps an auth call that answers with a message or null.
 */
export function useAuthForm(initialEmail = "") {
  const [email, setEmail] = useState(initialEmail);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const supabase = useMemo(createClient, []);

  async function run(call: () => Promise<string | null>): Promise<boolean> {
    setBusy(true);
    setError(null);
    const problem = await call();
    setBusy(false);
    setError(problem);
    return problem == null;
  }

  return { email: email.trim(), rawEmail: email, setEmail, busy, error, setError, run, supabase };
}
