import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";
// Relative, not "@/lib/auth": vitest has no path aliases configured (see middleware.test.ts).
import { RECOVERY_COOKIE } from "./lib/auth";

/** Reachable signed out. /reset-password is not: the reset email's link or code signs in first. */
const PUBLIC_PATHS = ["/login", "/signup", "/forgot-password", "/auth"];
/** Pointless when signed in, so they forward to the dashboard. */
const SIGNED_OUT_ONLY = ["/login", "/signup"];

/**
 * Refreshes the Supabase session cookie on every request and guards the app pages.
 * /reset-password additionally needs the recovery cookie that only /auth/callback sets after
 * a password-reset link or code, so an ordinary session can't open it by URL (the cookie
 * expires by itself; see RECOVERY_COOKIE in lib/auth.ts).
 */
export async function middleware(request: NextRequest) {
  let response = NextResponse.next({ request });

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll: () => request.cookies.getAll(),
        setAll(toSet) {
          toSet.forEach(({ name, value }) => request.cookies.set(name, value));
          response = NextResponse.next({ request });
          toSet.forEach(({ name, value, options }) => response.cookies.set(name, value, options));
        },
      },
    },
  );

  // getClaims verifies the session JWT locally against the project's published signing
  // keys (refreshing it when expired), so most requests skip a round trip to Supabase.
  const { data } = await supabase.auth.getClaims();
  const user = data?.claims.sub ?? null;

  const path = request.nextUrl.pathname;
  const isPublic = PUBLIC_PATHS.some((p) => path === p || path.startsWith(`${p}/`));
  if (!user && !isPublic) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.search = "";
    return NextResponse.redirect(url);
  }
  if (user && SIGNED_OUT_ONLY.includes(path)) {
    const url = request.nextUrl.clone();
    url.pathname = "/";
    url.search = "";
    return NextResponse.redirect(url);
  }
  if (path === "/reset-password" && !request.cookies.has(RECOVERY_COOKIE)) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.search = "?notice=link";
    return NextResponse.redirect(url);
  }
  return response;
}

export const config = {
  // Everything except Next's own static output and the favicon. There is no public/ folder;
  // if one is added, list its files here rather than skipping by file extension, so a
  // future route can't slip past the guard by ending in ".png".
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
