import { Nav } from "@/components/nav";
import { requireUser } from "@/lib/supabase/server";
import { signOut } from "../actions/account";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const { user } = await requireUser();
  return (
    <div className="mx-auto flex min-h-screen max-w-7xl flex-col md:flex-row">
      <aside className="border-b border-outline px-4 py-4 md:sticky md:top-0 md:h-screen md:w-60 md:shrink-0 md:border-r md:border-b-0 md:py-8">
        <div className="mb-4 flex items-center justify-between md:mb-8 md:block">
          <div className="px-4 text-lg font-semibold text-primary">Spotter</div>
          <form action={signOut} className="md:hidden">
            <button className="text-sm text-muted hover:text-foreground">Sign out</button>
          </form>
        </div>
        <Nav />
        <div className="mt-8 hidden px-4 md:block">
          <div className="truncate text-xs text-muted" title={user.email}>
            {user.email}
          </div>
          <form action={signOut}>
            <button className="mt-2 text-sm text-muted hover:text-foreground">Sign out</button>
          </form>
        </div>
      </aside>
      <main className="min-w-0 flex-1 px-4 py-6 md:px-10 md:py-10">{children}</main>
    </div>
  );
}
