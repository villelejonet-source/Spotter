"use client";

import clsx from "clsx";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

interface NavLink {
  href: string;
  label: string;
}

interface Section {
  label: string;
  href: string;
  icon: ReactNode;
  /** Sibling pages shown as tabs at the top of the section; the first is the section itself. */
  tabs?: NavLink[];
}

/**
 * The five sections, the same on every screen size: the sidebar on wide screens, the bottom
 * bar on phones. Today, History, Progress and Profile are the app's tabs; Plans gets its own
 * section here because the web is where plans are edited.
 */
const SECTIONS: Section[] = [
  { label: "Today", href: "/", icon: <HomeIcon /> },
  {
    label: "History",
    href: "/history",
    icon: <HistoryIcon />,
    // The app's History tab holds the calendar too.
    tabs: [{ href: "/history", label: "History" }, { href: "/calendar", label: "Calendar" }],
  },
  {
    label: "Progress",
    href: "/progress",
    icon: <ProgressIcon />,
    tabs: [{ href: "/progress", label: "Progress" }, { href: "/insights", label: "Insights" }, { href: "/library", label: "Exercises" }],
  },
  { label: "Plans", href: "/plans", icon: <PlansIcon /> },
  { label: "Profile", href: "/profile", icon: <ProfileIcon /> },
];

/** Only for accounts tagged admin (app_metadata.admin): the usage overview. */
const ADMIN_SECTION: Section = { label: "Admin", href: "/admin", icon: <AdminIcon /> };

const sectionsFor = (admin: boolean) => (admin ? [...SECTIONS, ADMIN_SECTION] : SECTIONS);

/** Every page the command palette can jump to: each section and its tabs, in nav order. */
export function navPages(admin: boolean): { href: string; label: string; section: string }[] {
  return sectionsFor(admin).flatMap((s) =>
    s.tabs ? s.tabs.map((t) => ({ href: t.href, label: t.label, section: s.label })) : [{ href: s.href, label: s.label, section: s.label }],
  );
}

const matches = (path: string, href: string) => (href === "/" ? path === "/" : path === href || path.startsWith(`${href}/`));

const sectionOf = (path: string, sections = SECTIONS) => sections.find((s) => [s.href, ...(s.tabs ?? []).map((t) => t.href)].some((h) => matches(path, h)));

/** "page" on the section's own page; "true" on its other pages, where it is the current section. */
const currentFor = (path: string, s: Section, active: boolean) => (path === s.href ? "page" : active ? "true" : undefined);

/** The sections down the side, for wide screens. */
export function SidebarNav({ admin = false }: { admin?: boolean }) {
  const path = usePathname();
  const sections = sectionsFor(admin);
  const current = sectionOf(path, sections);
  return (
    <nav aria-label="Main" className="flex flex-col gap-0.5">
      {sections.map((s) => {
        const active = s === current;
        return (
          <Link
            key={s.href}
            href={s.href}
            aria-current={currentFor(path, s, active)}
            className={clsx(
              "relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition",
              active
                ? "bg-gradient-to-r from-primary-container to-transparent text-foreground before:absolute before:inset-y-2 before:-left-3 before:w-[3px] before:rounded-r before:bg-primary before:shadow-[0_0_12px_var(--glow)]"
                : "text-muted hover:bg-surface hover:text-foreground",
            )}
          >
            <span className={clsx(active && "text-primary")}>{s.icon}</span>
            {s.label}
          </Link>
        );
      })}
    </nav>
  );
}

/** The sections along the bottom, for phones (the app's navigation bar). */
export function MobileTabBar({ admin = false }: { admin?: boolean }) {
  const path = usePathname();
  const sections = sectionsFor(admin);
  const current = sectionOf(path, sections);
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-outline bg-raised/90 backdrop-blur-xl md:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      <div className={clsx("mx-auto grid max-w-lg", sections.length > 5 ? "grid-cols-6" : "grid-cols-5")}>
        {sections.map((s) => {
          const active = s === current;
          return (
            <Link
              key={s.href}
              href={s.href}
              aria-current={currentFor(path, s, active)}
              className={clsx("flex min-h-16 flex-col items-center justify-center gap-1 text-xs font-medium", active ? "text-foreground" : "text-muted")}
            >
              <span className={clsx("flex h-8 w-14 items-center justify-center rounded-lg transition", active && "bg-primary-container text-primary shadow-[0_0_16px_-4px_var(--glow)]")}>{s.icon}</span>
              {s.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

/**
 * Tabs between the pages of a section (History and Calendar under History), on every screen
 * size. Only on those pages themselves: a detail page has its back link instead.
 */
export function SectionTabs() {
  const path = usePathname();
  const tabs = sectionOf(path)?.tabs;
  if (!tabs?.some((t) => t.href === path)) return null;
  return (
    <nav aria-label="Section" className="-mx-4 mb-6 overflow-x-auto px-4 md:mx-0 md:px-0">
      <div className="flex w-max gap-1 rounded-xl border border-outline bg-surface-high p-1">
        {tabs.map((t) => {
          const active = t.href === path;
          return (
            <Link
              key={t.href}
              href={t.href}
              aria-current={active ? "page" : undefined}
              className={clsx(
                "whitespace-nowrap rounded-lg px-4 py-1.5 text-sm font-medium transition",
                active ? "bg-raised text-foreground shadow-sm ring-1 ring-outline" : "text-muted hover:text-foreground",
              )}
            >
              {t.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

// Material Symbols (outlined), the app's navigation icons.
function Icon({ d }: { d: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 -960 960 960" className="h-5 w-5 shrink-0 fill-current">
      <path d={d} />
    </svg>
  );
}

function HomeIcon() {
  return <Icon d="M240-200h120v-240h240v240h120v-360L480-740 240-560v360Zm-80 80v-480l320-240 320 240v480H520v-240h-80v240H160Zm320-350Z" />;
}

function HistoryIcon() {
  return (
    <Icon d="M480-120q-138 0-240.5-91.5T122-440h82q14 104 92.5 172T480-200q117 0 198.5-81.5T760-480q0-117-81.5-198.5T480-760q-69 0-129 32t-101 88h110v80H120v-240h80v94q51-64 124.5-99T480-840q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-480q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-120Zm112-192L440-464v-216h80v184l128 128-56 56Z" />
  );
}

function ProgressIcon() {
  return <Icon d="m136-240-56-56 296-298 160 160 208-206H640v-80h240v240h-80v-104L536-320 376-480 136-240Z" />;
}

function PlansIcon() {
  return (
    <Icon d="M200-80q-33 0-56.5-23.5T120-160v-560q0-33 23.5-56.5T200-800h40v-80h80v80h320v-80h80v80h40q33 0 56.5 23.5T840-720v560q0 33-23.5 56.5T760-80H200Zm0-80h560v-400H200v400Zm0-480h560v-80H200v80Zm0 0v-80 80Zm80 240v-80h400v80H280Zm0 160v-80h280v80H280Z" />
  );
}

function AdminIcon() {
  return <Icon d="M640-160v-280h160v280H640Zm-240 0v-640h160v640H400Zm-240 0v-440h160v440H160Z" />;
}

function ProfileIcon() {
  return (
    <Icon d="M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-160v-112q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v112H160Zm80-80h480v-32q0-11-5.5-20T700-306q-54-27-109-40.5T480-360q-56 0-111 13.5T260-306q-9 5-14.5 14t-5.5 20v32Zm240-320q33 0 56.5-23.5T560-640q0-33-23.5-56.5T480-720q-33 0-56.5 23.5T400-640q0 33 23.5 56.5T480-560Zm0 320Zm0-400Z" />
  );
}
