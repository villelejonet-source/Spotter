import Link from "next/link";
import type { ReactNode } from "react";
import { Card } from "./ui";

/** The centred card every sign-in, sign-up and password page sits in. */
export function AuthCard({ title, subtitle, children, footer }: { title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <Card className="p-8">
          <div className="mb-6">
            <Link href="/login" className="text-sm font-semibold text-primary">Spotter</Link>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">{title}</h1>
            {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
          </div>
          {children}
        </Card>
        {footer && <div className="mt-4 text-center text-sm text-muted">{footer}</div>}
      </div>
    </main>
  );
}

export function FormMessage({ tone, children }: { tone: "error" | "info"; children: ReactNode }) {
  return (
    <p role={tone === "error" ? "alert" : "status"} className={tone === "error" ? "text-sm text-error" : "rounded-lg bg-surface-high px-3 py-2 text-sm"}>
      {children}
    </p>
  );
}
