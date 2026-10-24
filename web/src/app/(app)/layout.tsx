import { ConfirmButton } from "@/components/confirm-button";
import { MobileSubNav, MobileTabBar, SidebarNav } from "@/components/nav";
import { requireUser } from "@/lib/supabase/server";
import { signOut } from "../actions/account";

/**
 * The signed-in shell: a sidebar on wide screens; on phones a header, the current tab's
 * pages under it and the app's four tabs along the bottom.
 */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const { user } = await requireUser();
  const signOutButton = (
    <ConfirmButton
      action={signOut}
      variant="ghost"
      className="text-muted"
      title="Sign out?"
      body="You can sign in again any time. Your backup stays in the cloud and keeps syncing from your phone."
      confirmLabel="Sign out"
    >
      Sign out
    </ConfirmButton>
  );

  return (
    <div className="mx-auto flex min-h-screen max-w-7xl flex-col md:flex-row">
      <header className="border-b border-outline md:sticky md:top-0 md:flex md:h-screen md:w-60 md:shrink-0 md:flex-col md:border-r md:border-b-0 md:px-4 md:py-8">
        <div className="flex items-center justify-between px-4 py-3 md:mb-8 md:block md:px-0 md:py-0">
          <div className="text-lg font-semibold text-primary md:px-4">Spotter</div>
          <div className="md:hidden">{signOutButton}</div>
        </div>
        <div className="hidden md:block">
          <SidebarNav />
        </div>
        <div className="md:hidden">
          <MobileSubNav />
        </div>
        <div className="mt-auto hidden px-4 md:block">
          <div className="truncate text-xs text-muted" title={user.email}>
            {user.email}
          </div>
          <div className="-ml-4 mt-1">{signOutButton}</div>
        </div>
      </header>
      <main className="min-w-0 flex-1 px-4 py-6 pb-24 md:px-10 md:py-10">{children}</main>
      <MobileTabBar />
    </div>
  );
}
