import type { EmailOtpType } from "@supabase/supabase-js";
import { NextResponse, type NextRequest } from "next/server";
import { safeNext } from "@/lib/auth";
import { createClient } from "@/lib/supabase/server";

/**
 * Where links in Spotter's emails land: sign-up confirmation, sign-in links and password
 * reset. Handles both link styles Supabase can send:
 * - `?code=` (the default {{ .ConfirmationURL }}): exchanged for a session. This needs the
 *   browser that asked for the email, so a link opened elsewhere can't sign in, although
 *   a sign-up is still confirmed by then.
 * - `?token_hash=&type=`: verified directly, from any browser.
 * Then continues to `next` (same-site paths only).
 */
export async function GET(request: NextRequest) {
  const { searchParams, origin } = request.nextUrl;
  const next = safeNext(searchParams.get("next"));
  const fail = (notice: string) => NextResponse.redirect(`${origin}/login?notice=${notice}`);

  // Supabase redirects here with an error when a link is expired or already used.
  if (searchParams.get("error")) return fail("link");

  const supabase = await createClient();
  const tokenHash = searchParams.get("token_hash");
  const type = searchParams.get("type") as EmailOtpType | null;
  if (tokenHash && type) {
    const { error } = await supabase.auth.verifyOtp({ token_hash: tokenHash, type });
    if (error) return fail("link");
    return NextResponse.redirect(`${origin}${type === "recovery" ? "/reset-password" : next}`);
  }

  const code = searchParams.get("code");
  if (code) {
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (error) return fail(error.code === "bad_code_verifier" || /code verifier/i.test(error.message) ? "link-other-browser" : "link");
    return NextResponse.redirect(`${origin}${next}`);
  }
  return fail("link");
}
