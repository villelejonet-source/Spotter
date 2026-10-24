import type { EmailOtpType } from "@supabase/supabase-js";
import { NextResponse, type NextRequest } from "next/server";
import { RECOVERY_COOKIE, recoveryCookieOptions, safeNext } from "@/lib/auth";
import { createClient } from "@/lib/supabase/server";

/** The email link kinds Spotter sends. Invites, email and phone changes aren't used, so they're refused. */
const TOKEN_HASH_TYPES: ReadonlySet<string> = new Set<EmailOtpType>(["signup", "email", "recovery", "magiclink"]);

function isEmailLinkType(type: string): type is EmailOtpType {
  return TOKEN_HASH_TYPES.has(type);
}

/**
 * Where links in Spotter's emails land: sign-up confirmation, sign-in links and password
 * reset. Handles both link styles Supabase can send:
 * - `?code=` (the default {{ .ConfirmationURL }}): exchanged for a session. This needs the
 *   browser that asked for the email, so a link opened elsewhere can't sign in, although
 *   a sign-up is still confirmed by then. The stored verifier also remembers whether the
 *   email was a password reset (`redirectType`).
 * - `?token_hash=&type=`: verified directly, from any browser.
 * Then continues to `next` (same-site paths only). A password reset always continues to
 * /reset-password and sets the recovery cookie that page requires (see RECOVERY_COOKIE);
 * any other completed link clears a leftover one.
 */
export async function GET(request: NextRequest) {
  const { searchParams, origin } = request.nextUrl;
  const next = safeNext(searchParams.get("next"));
  const secure = origin.startsWith("https://");

  const fail = (notice: string) => {
    const response = NextResponse.redirect(`${origin}/login?notice=${notice}`);
    response.cookies.delete(RECOVERY_COOKIE);
    return response;
  };
  const done = (recovery: boolean) => {
    const response = NextResponse.redirect(`${origin}${recovery ? "/reset-password" : next}`);
    if (recovery) {
      response.cookies.set(RECOVERY_COOKIE, "1", recoveryCookieOptions(secure));
    } else {
      response.cookies.delete(RECOVERY_COOKIE);
    }
    return response;
  };

  // Supabase redirects here with an error when a link is expired or already used.
  if (searchParams.get("error")) return fail("link");

  const supabase = await createClient();
  const tokenHash = searchParams.get("token_hash");
  const type = searchParams.get("type");
  if (tokenHash && type) {
    if (!isEmailLinkType(type)) return fail("link");
    const { error } = await supabase.auth.verifyOtp({ token_hash: tokenHash, type });
    if (error) {
      console.warn("auth callback: verifyOtp failed", error.code ?? error.status);
      return fail("link");
    }
    return done(type === "recovery");
  }

  const code = searchParams.get("code");
  if (code) {
    const { data, error } = await supabase.auth.exchangeCodeForSession(code);
    if (error) {
      console.warn("auth callback: code exchange failed", error.code ?? error.status);
      return fail(error.code === "bad_code_verifier" || /code verifier/i.test(error.message) ? "link-other-browser" : "link");
    }
    // auth-js stores "<verifier>/<flow>" when the email was requested and returns the flow
    // here; the response type doesn't declare it.
    const flow = (data as { redirectType?: string | null }).redirectType ?? null;
    return done(flow === "recovery");
  }
  return fail("link");
}
