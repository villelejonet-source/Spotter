import Link from "next/link";
import { CommandPalette } from "@/components/command-palette";
import { MobileTabBar, SectionTabs, SidebarNav } from "@/components/nav";
import { requireUser } from "@/lib/supabase/server";

/**
 * The signed-in shell. The same five sections everywhere: a sidebar on wide screens, a
 * bottom bar on phones. A section with more than one page shows its pages as tabs above the
 * content. Signing out lives on the Profile page, with the rest of the account. Accounts tagged
 * admin also get Admin, the usage overview.
 */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const { user } = await requireUser();
  const brand = (
    <Link href="/" className="inline-flex items-center gap-2.5 rounded-md text-[1.0625rem] font-semibold tracking-[-0.02em]">
      <span aria-hidden="true" className="btn-primary grid h-7 w-7 place-items-center rounded-lg">
        <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
          <path d="M6 12h12M4 8v8M8 6v12M16 6v12M20 8v8" />
        </svg>
      </span>
      Spotter
    </Link>
  );

  return (
    <div className="mx-auto flex min-h-screen max-w-7xl flex-col md:flex-row">
      <header className="sticky top-0 z-30 flex h-14 items-center border-b border-outline bg-raised/85 px-4 backdrop-blur-xl md:hidden">{brand}</header>
      <aside className="hidden md:sticky md:top-0 md:flex md:h-screen md:w-60 md:shrink-0 md:flex-col md:border-r md:border-outline md:bg-raised/40 md:px-3 md:py-6 md:backdrop-blur-xl">
        <div className="mb-6 px-3">{brand}</div>
        <div className="mb-5">
          <CommandPalette admin={user.isAdmin} />
        </div>
        <SidebarNav admin={user.isAdmin} />
        <Link
          href="/profile"
          className="panel mt-auto block truncate rounded-xl px-3 py-2.5 text-xs text-muted hover:text-foreground"
          title={user.email}
        >
          <span className="label block text-faint">Signed in</span>
          <span className="mt-1 block truncate">{user.email}</span>
        </Link>
      </aside>
      <main className="min-w-0 flex-1 px-4 pt-6 pb-28 md:px-10 md:py-10">
        <SectionTabs />
        {children}
      </main>
      <MobileTabBar admin={user.isAdmin} />
    </div>
  );
}
