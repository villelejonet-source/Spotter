// The website's design system: the same roles, spacing and names as the app's core/ui
// components (cards 16 dp padding, one primary button, chips, section headers), so a page
// here reads like a screen there. Everything is server-safe; Dialog lives in dialog.tsx.
import clsx from "clsx";
import Link from "next/link";
import { cloneElement, isValidElement, useId, type ComponentProps, type ReactElement, type ReactNode } from "react";

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <div className="mt-1 text-sm text-muted">{subtitle}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

/** Back to the parent page: the app's top-bar back arrow. */
export function BackLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="mb-2 inline-flex items-center gap-1 rounded-md text-sm text-muted hover:text-foreground">
      <span aria-hidden="true">←</span> {children}
    </Link>
  );
}

/** A content card (the app's surfaceContainerLow cards). */
export function Card({ className, ...props }: ComponentProps<"div">) {
  return <div className={clsx("rounded-2xl border border-outline bg-surface-low p-4", className)} {...props} />;
}

/** A card's section header, with an optional action on the right. */
export function CardTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-2">
      <h2 className="text-sm font-medium text-muted">{children}</h2>
      {action}
    </div>
  );
}

/** A section heading between cards. */
export function SectionTitle({ children }: { children: ReactNode }) {
  return <h2 className="mb-2 text-sm font-medium text-muted">{children}</h2>;
}

/** A number with its label (the app's StatTile, on surfaceContainerHigh). */
export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <div className="rounded-2xl bg-surface-high p-4">
      <div className="text-sm text-muted">{label}</div>
      <div className="mt-1 text-3xl font-semibold tabular-nums">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted">{hint}</div>}
    </div>
  );
}

const buttonStyles = {
  primary: "bg-primary text-on-primary hover:opacity-90",
  secondary: "border border-outline-strong bg-transparent hover:bg-surface",
  ghost: "hover:bg-surface",
  danger: "border border-outline-strong text-error hover:bg-error-container hover:text-on-error-container",
};

export type ButtonVariant = keyof typeof buttonStyles;

export function buttonClass(variant: ButtonVariant = "secondary", className?: string) {
  return clsx(
    "inline-flex min-h-10 items-center justify-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50",
    buttonStyles[variant],
    className,
  );
}

export function Button({ variant, className, type = "button", ...props }: ComponentProps<"button"> & { variant?: ButtonVariant }) {
  return <button type={type} className={buttonClass(variant, className)} {...props} />;
}

export function LinkButton({ variant, className, ...props }: ComponentProps<typeof Link> & { variant?: ButtonVariant }) {
  return <Link className={buttonClass(variant, className)} {...props} />;
}

/** A control that is only an icon: [label] is what it does, read by screen readers and shown on hover. */
export function IconButton({ label, className, type = "button", children, ...props }: ComponentProps<"button"> & { label: string }) {
  return (
    <button
      type={type}
      aria-label={label}
      title={label}
      className={clsx(
        "inline-flex h-9 w-9 items-center justify-center rounded-full text-muted transition hover:bg-surface hover:text-foreground disabled:cursor-not-allowed disabled:opacity-30",
        className,
      )}
      {...props}
    >
      <span aria-hidden="true">{children}</span>
    </button>
  );
}

/** A link that is only an icon, with the same treatment as [IconButton]. */
export function IconLink({ label, className, children, ...props }: ComponentProps<typeof Link> & { label: string }) {
  return (
    <Link
      aria-label={label}
      title={label}
      className={clsx("inline-flex h-9 w-9 items-center justify-center rounded-full border border-outline-strong text-sm transition hover:bg-surface", className)}
      {...props}
    >
      <span aria-hidden="true">{children}</span>
    </Link>
  );
}

export function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "primary" | "tertiary" | "error" }) {
  return (
    <span
      className={clsx(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        tone === "primary" && "bg-primary-container text-on-primary-container",
        tone === "tertiary" && "bg-tertiary-container text-on-tertiary-container",
        tone === "error" && "bg-error-container text-on-error-container",
        tone === "neutral" && "bg-surface-high text-muted",
      )}
    >
      {children}
    </span>
  );
}

/** A toggle in a row of filters or multi-choice options (the app's FilterChip). */
export function Chip({ on, className, type = "button", ...props }: ComponentProps<"button"> & { on: boolean }) {
  return (
    <button
      type={type}
      aria-pressed={on}
      className={clsx(
        "whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-40",
        on ? "bg-primary-container text-on-primary-container" : "bg-surface-high text-muted hover:text-foreground",
        className,
      )}
      {...props}
    />
  );
}

/** A dashed pill for a planned (not yet done) workout, in the plan day's colour. */
export function OutlinePill({ className, children, title }: { className?: string; children: ReactNode; title?: string }) {
  return (
    <span title={title} className={clsx("inline-block truncate rounded border border-dashed px-2 py-0.5 text-sm font-medium", className)}>
      {children}
    </span>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <Card className="py-10 text-center">
      <div className="font-medium">{title}</div>
      {children && <div className="mx-auto mt-2 max-w-md text-sm text-muted">{children}</div>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </Card>
  );
}

/** Feedback under a form or in a card: an error (announced at once) or a note. */
export function InlineMessage({ tone, children, className }: { tone: "error" | "info" | "success"; children: ReactNode; className?: string }) {
  return (
    <p
      role={tone === "error" ? "alert" : "status"}
      className={clsx(
        "rounded-lg px-3 py-2 text-sm",
        tone === "error" && "bg-error-container text-on-error-container",
        tone === "info" && "bg-surface-high text-foreground",
        tone === "success" && "bg-primary-container text-on-primary-container",
        className,
      )}
    >
      {children}
    </p>
  );
}

export function inputClasses({ compact = false }: { compact?: boolean } = {}) {
  return clsx(
    "w-full rounded-lg border border-outline-strong outline-none transition focus:border-primary focus:ring-1 focus:ring-primary aria-[invalid=true]:border-error disabled:opacity-50",
    compact ? "px-2 py-1 text-sm tabular-nums" : "px-3 py-2 text-sm",
  );
}

export const inputClass = inputClasses();

/**
 * A labelled control. The child input gets the label's id, and the hint and error are
 * linked to it with aria-describedby so a screen reader reads them with the field.
 */
export function Field({ label, hint, error, className, children }: { label: ReactNode; hint?: ReactNode; error?: string | null; className?: string; children: ReactElement }) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  const control = isValidElement<Record<string, unknown>>(children)
    ? cloneElement(children, { id, "aria-describedby": describedBy, "aria-invalid": error ? true : undefined })
    : children;
  return (
    <div className={clsx("space-y-1 text-sm", className)}>
      <label htmlFor={id} className="block text-muted">
        {label}
      </label>
      {control}
      {hint && (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-xs text-error">
          {error}
        </p>
      )}
    </div>
  );
}

/** A group of chips with a visible name, for multi-choice fields. */
export function ChipGroup({ label, hint, children }: { label: ReactNode; hint?: ReactNode; children: ReactNode }) {
  const id = useId();
  return (
    <div className="space-y-1.5 text-sm">
      <div id={id} className="text-muted">
        {label}
      </div>
      <div role="group" aria-labelledby={id} className="flex flex-wrap gap-1.5">
        {children}
      </div>
      {hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  );
}
