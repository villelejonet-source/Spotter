import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import { isAdminClaims } from "@/lib/admin";

/** Supabase client for server components, route handlers and server actions. */
export async function createClient() {
  const cookieStore = await cookies();
  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll: () => cookieStore.getAll(),
        setAll(toSet) {
          try {
            toSet.forEach(({ name, value, options }) => cookieStore.set(name, value, options));
          } catch {
            // Called from a server component: cookies are read-only there; the
            // middleware refreshes the session instead.
          }
        },
      },
    },
  );
}

/**
 * The signed-in user's id, email and admin tag (checked locally, see middleware), or a redirect to
 * /login. Memoised per request with React's cache(), so the layout, the page, an action and
 * push() share one getClaims call (the middleware runs separately and can't share it).
 */
export const requireUser = cache(async () => {
  const supabase = await createClient();
  const { data } = await supabase.auth.getClaims();
  if (!data) redirect("/login");
  return {
    supabase,
    user: { id: data.claims.sub, email: data.claims.email as string | undefined, isAdmin: isAdminClaims(data.claims) },
  };
});
