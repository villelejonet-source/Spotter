import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RECOVERY_COOKIE } from "./lib/auth";
import { middleware } from "./middleware";

// The session check is the only thing the middleware asks Supabase; stub it per test.
const session = vi.hoisted(() => ({ signedIn: true }));
vi.mock("@supabase/ssr", () => ({
  createServerClient: () => ({
    auth: { getClaims: async () => ({ data: session.signedIn ? { claims: { sub: "user-1" } } : null }) },
  }),
}));

function request(path: string, cookies: Record<string, string> = {}) {
  const headers = new Headers();
  const cookie = Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
  if (cookie) headers.set("cookie", cookie);
  return new NextRequest(new URL(path, "https://spotter.test"), { headers });
}

/** Path and query of the redirect, or null when the request goes through. */
function redirectTarget(res: Response): string | null {
  const location = res.headers.get("location");
  if (!location) return null;
  const url = new URL(location);
  return url.pathname + url.search;
}

describe("middleware", () => {
  beforeEach(() => {
    session.signedIn = true;
  });

  it("opens /reset-password only with the recovery cookie", async () => {
    expect(redirectTarget(await middleware(request("/reset-password")))).toBe("/login?notice=link");
    expect(redirectTarget(await middleware(request("/reset-password", { [RECOVERY_COOKIE]: "1" })))).toBeNull();
  });

  it("sends signed-out visitors to /login, recovery cookie or not", async () => {
    session.signedIn = false;
    expect(redirectTarget(await middleware(request("/reset-password", { [RECOVERY_COOKIE]: "1" })))).toBe("/login");
    expect(redirectTarget(await middleware(request("/")))).toBe("/login");
  });

  it("remembers the page a signed-out visitor wanted", async () => {
    session.signedIn = false;
    expect(redirectTarget(await middleware(request("/plans")))).toBe("/login?next=%2Fplans");
    expect(redirectTarget(await middleware(request("/history/42?tab=sets")))).toBe("/login?next=%2Fhistory%2F42%3Ftab%3Dsets");
  });

  it("keeps the email-code and link endpoints reachable signed out", async () => {
    session.signedIn = false;
    expect(redirectTarget(await middleware(request("/auth/verify")))).toBeNull();
    expect(redirectTarget(await middleware(request("/auth/callback?code=x")))).toBeNull();
  });

  it("serves the privacy policy signed in and signed out", async () => {
    expect(redirectTarget(await middleware(request("/privacy")))).toBeNull();
    session.signedIn = false;
    expect(redirectTarget(await middleware(request("/privacy")))).toBeNull();
    expect(redirectTarget(await middleware(request("/privacy-not")))).toBe("/login?next=%2Fprivacy-not");
  });

  it("guards paths that merely look like static files", async () => {
    session.signedIn = false;
    expect(redirectTarget(await middleware(request("/export.png")))).toBe("/login?next=%2Fexport.png");
  });

  it("forwards signed-in users away from /login, to where they were going", async () => {
    expect(redirectTarget(await middleware(request("/login")))).toBe("/");
    expect(redirectTarget(await middleware(request("/login?next=/x")))).toBe("/x");
    expect(redirectTarget(await middleware(request("/login?next=//evil.example")))).toBe("/");
    expect(redirectTarget(await middleware(request("/signup")))).toBe("/");
  });
});
