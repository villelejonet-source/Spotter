"use client";

import clsx from "clsx";
import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Dashboard" },
  { href: "/calendar", label: "Calendar" },
  { href: "/history", label: "History" },
  { href: "/progress", label: "Progress" },
  { href: "/insights", label: "Insights" },
  { href: "/plans", label: "Plans" },
  { href: "/library", label: "Exercises" },
  { href: "/profile", label: "Profile" },
];

export function Nav() {
  const path = usePathname();
  const active = (href: string) => (href === "/" ? path === "/" : path === href || path.startsWith(`${href}/`));
  return (
    <nav className="flex gap-1 overflow-x-auto md:flex-col">
      {LINKS.map((l) => (
        <Link
          key={l.href}
          href={l.href}
          className={clsx(
            "whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition",
            active(l.href) ? "bg-primary-container text-on-primary-container" : "text-muted hover:bg-surface-high",
          )}
        >
          {l.label}
        </Link>
      ))}
    </nav>
  );
}
