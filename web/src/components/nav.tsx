"use client";

import clsx from "clsx";
import Link from "next/link";
import { usePathname } from "next/navigation";

interface NavLink {
  href: string;
  label: string;
}

/** The app's four tabs (Today, History, Progress, Profile), each with the web pages that belong under it. */
const GROUPS: { label: string; links: NavLink[] }[] = [
  { label: "Today", links: [{ href: "/", label: "Dashboard" }, { href: "/calendar", label: "Calendar" }] },
  { label: "History", links: [{ href: "/history", label: "History" }] },
  { label: "Progress", links: [{ href: "/progress", label: "Progress" }, { href: "/insights", label: "Insights" }, { href: "/library", label: "Exercises" }] },
  { label: "Profile", links: [{ href: "/profile", label: "Profile" }, { href: "/plans", label: "Plans" }] },
];

const isActive = (path: string, href: string) => (href === "/" ? path === "/" : path === href || path.startsWith(`${href}/`));

function NavItem({ link, active, className }: { link: NavLink; active: boolean; className?: string }) {
  return (
    <Link
      href={link.href}
      aria-current={active ? "page" : undefined}
      className={clsx(
        "whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition",
        active ? "bg-primary-container text-on-primary-container" : "text-muted hover:bg-surface",
        className,
      )}
    >
      {link.label}
    </Link>
  );
}

/** Every page, grouped like the app's tabs, for the desktop sidebar. */
export function SidebarNav() {
  const path = usePathname();
  return (
    <nav aria-label="Pages" className="space-y-4">
      {GROUPS.map((g) => (
        <div key={g.label}>
          <div className="mb-1 px-4 text-xs font-medium uppercase tracking-wide text-muted">{g.label}</div>
          <div className="flex flex-col gap-0.5">
            {g.links.map((l) => (
              <NavItem key={l.href} link={l} active={isActive(path, l.href)} />
            ))}
          </div>
        </div>
      ))}
    </nav>
  );
}

/** The pages of the current group, shown under the mobile header when the group has more than one. */
export function MobileSubNav() {
  const path = usePathname();
  const group = GROUPS.find((g) => g.links.some((l) => isActive(path, l.href)));
  if (!group || group.links.length < 2) return null;
  return (
    <nav aria-label={`${group.label} pages`} className="flex gap-1 overflow-x-auto px-4 pb-3">
      {group.links.map((l) => (
        <NavItem key={l.href} link={l} active={isActive(path, l.href)} className="px-3 py-1.5 text-xs" />
      ))}
    </nav>
  );
}

/** A bottom bar with the app's four tabs, for phones. */
export function MobileTabBar() {
  const path = usePathname();
  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-40 border-t border-outline bg-background/95 backdrop-blur md:hidden" style={{ paddingBottom: "env(safe-area-inset-bottom)" }}>
      <div className="mx-auto grid max-w-lg grid-cols-4">
        {GROUPS.map((g) => {
          const active = g.links.some((l) => isActive(path, l.href));
          return (
            <Link
              key={g.label}
              href={g.links[0].href}
              aria-current={active ? "page" : undefined}
              className={clsx("flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs font-medium", active ? "text-primary" : "text-muted")}
            >
              <span className={clsx("rounded-full px-4 py-0.5", active && "bg-primary-container text-on-primary-container")}>{g.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
