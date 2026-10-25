import Link from "next/link";
import { MobileTabBar, SectionTabs, SidebarNav } from "@/components/nav";
import { requireUser } from "@/lib/supabase/server";

/**
 * The signed-in shell. The same five sections everywhere: a sidebar on wide screens, a
 * bottom bar on phones. A section with more than one page shows its pages as tabs above the
 * content. Signing out lives on the Profile page, with the rest of the account.
 */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const { user } = await requireUser();
  const brand = (
    <Link href="/" className="rounded-md text-lg font-semibold tracking-tight text-primary">
      Spotter
    </Link>
  );

  return (
    <div className="mx-auto flex min-h-screen max-w-7xl flex-col md:flex-row">
      <header className="sticky top-0 z-30 flex h-14 items-center border-b border-outline bg-background/95 px-4 backdrop-blur md:hidden">{brand}</header>
      <aside className="hidden md:sticky md:top-0 md:flex md:h-screen md:w-60 md:shrink-0 md:flex-col md:border-r md:border-outline md:px-3 md:py-8">
        <div className="mb-8 px-4">{brand}</div>
        <SidebarNav />
        <Link
          href="/profile"
          className="mt-auto truncate rounded-full px-4 py-2 text-xs text-muted hover:bg-surface hover:text-foreground"
          title={user.email}
        >
          {user.email}
        </Link>
      </aside>
      <main className="min-w-0 flex-1 px-4 pt-6 pb-28 md:px-10 md:py-10">
        <SectionTabs />
        {children}
      </main>
      <MobileTabBar />
    </div>
  );
}
