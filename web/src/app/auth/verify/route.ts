import { NextResponse, type NextRequest } from "next/server";
import { authErrorMessage, GENERIC_AUTH_ERROR, parseEmailCode, RECOVERY_COOKIE, recoveryCookieOptions } from "@/lib/auth";
import { createClient } from "@/lib/supabase/server";

/**
 * Verifies a code from one of Spotter's emails (the counterpart of /auth/callback for
 * links). It runs here rather than in the browser so the response sets the session cookies
 * and, for a password reset, the recovery cookie that /reset-password requires.
 *
 * Body: { email, token, type: "recovery" | "signup" | "email" } as JSON.
 * 200 { ok: true } when signed in; 400/403 { error } with text that's safe to show.
 *
 * Only same-origin JSON requests are accepted: a cross-site form can't send JSON without a
 * preflight, and the Origin check stops another site signing a visitor into an account of
 * its choosing.
 */
export async function POST(request: NextRequest) {
  const { origin } = request.nextUrl;
  if (request.headers.get("origin") !== origin || !request.headers.get("content-type")?.startsWith("application/json")) {
    return NextResponse.json({ error: GENERIC_AUTH_ERROR }, { status: 403 });
  }
  const input = parseEmailCode(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ error: "Enter the code from the email." }, { status: 400 });

  const supabase = await createClient();
  const { error } = await supabase.auth.verifyOtp(input);
  if (error) {
    console.warn("auth verify: verifyOtp failed", error.code ?? error.status);
    return NextResponse.json({ error: authErrorMessage(error) }, { status: 400 });
  }

  const response = NextResponse.json({ ok: true });
  if (input.type === "recovery") response.cookies.set(RECOVERY_COOKIE, "1", recoveryCookieOptions(origin.startsWith("https://")));
  else response.cookies.delete(RECOVERY_COOKIE);
  return response;
}
