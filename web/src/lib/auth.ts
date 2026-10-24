// Plain helpers for the sign-in, sign-up and password pages (no UI, so they're unit-tested).
// No server-only imports: the middleware uses the cookie constants too.

export const MIN_PASSWORD_LENGTH = 8;

/** What users see for any auth error we don't have better words for (the app's account_error_other). */
export const GENERIC_AUTH_ERROR = "Something went wrong. Try again.";

/**
 * Set by the server when a password-reset email signed the user in (/auth/callback for the
 * link, POST /auth/verify for the code), and required by the middleware to open
 * /reset-password, so a session that didn't come from a reset email can't reach that page by
 * URL. It expires on its own after RECOVERY_COOKIE_MAX_AGE seconds: the password is saved by
 * the browser calling updateUser, which the server can't observe, so there is no reliable
 * moment to clear it earlier. Both routes clear it whenever another kind of sign-in
 * completes, and signing out clears it too.
 */
export const RECOVERY_COOKIE = "spotter_recovery";
export const RECOVERY_COOKIE_MAX_AGE = 15 * 60;

/** Cookie options for RECOVERY_COOKIE; `secure` whenever the site is served over https. */
export function recoveryCookieOptions(secure: boolean) {
  return { httpOnly: true, sameSite: "lax", secure, path: "/", maxAge: RECOVERY_COOKIE_MAX_AGE } as const;
}

/** The emailed-code kinds POST /auth/verify accepts. */
export const EMAIL_CODE_TYPES = ["recovery", "signup", "email"] as const;
export type EmailCodeType = (typeof EMAIL_CODE_TYPES)[number];
export interface EmailCodeInput {
  email: string;
  token: string;
  type: EmailCodeType;
}

/** The body of POST /auth/verify if it is well formed, else null. Codes are 6–10 digits. */
export function parseEmailCode(body: unknown): EmailCodeInput | null {
  if (!body || typeof body !== "object") return null;
  const { email, token, type } = body as Record<string, unknown>;
  if (typeof email !== "string" || email.length > 320 || !/^[^\s@]+@[^\s@]+$/.test(email.trim())) return null;
  if (typeof token !== "string" || !/^\d{6,10}$/.test(token)) return null;
  if (typeof type !== "string" || !(EMAIL_CODE_TYPES as readonly string[]).includes(type)) return null;
  return { email: email.trim(), token, type: type as EmailCodeType };
}

/**
 * Verifies an emailed code on the server (POST /auth/verify), so the session cookies and,
 * for a password reset, the recovery cookie are set by the response. Returns an error to
 * show, or null once signed in. Runs in the browser.
 */
export async function verifyEmailCode(email: string, token: string, type: EmailCodeType): Promise<string | null> {
  try {
    const res = await fetch("/auth/verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, token, type }),
    });
    if (res.ok) return null;
    const body = (await res.json().catch(() => null)) as { error?: unknown } | null;
    return typeof body?.error === "string" ? body.error : GENERIC_AUTH_ERROR;
  } catch {
    return "Couldn't reach Spotter. Check your connection and try again.";
  }
}

/** What the delete-account form must send back, typed by the user: this word or the account's email. */
export const DELETE_CONFIRMATION = "DELETE";

/** Why a new password isn't acceptable, or null. Supabase checks again on its side. */
export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  if (password !== confirm) return "The passwords don't match.";
  return null;
}

/**
 * Supabase's error codes in plain words. Unknown errors get a fixed message: the raw text
 * can name internals, and the code is what's worth logging where this runs on the server.
 */
export function authErrorMessage(error: { message: string; code?: string }): string {
  switch (error.code) {
    case "invalid_credentials":
      return "Wrong email or password.";
    case "email_not_confirmed":
      return "Confirm your email first. We can send the confirmation again.";
    case "otp_expired":
      return "That code has expired or was already used. Ask for a new one.";
    case "weak_password":
      return "That password is too weak. Use a longer one, mixing words and numbers.";
    case "same_password":
      return "That's your current password. Choose a new one.";
    case "over_email_send_rate_limit":
    case "over_request_rate_limit":
      return "Too many attempts. Wait a minute and try again.";
    case "user_already_exists":
    case "email_exists":
      return "An account with this email already exists. Sign in or reset your password.";
    default:
      return GENERIC_AUTH_ERROR;
  }
}

/**
 * Only same-site paths, so a crafted link can't send people elsewhere after signing in:
 * one leading slash, then no second slash or backslash (protocol-relative), and no control
 * characters or backslashes anywhere (browsers normalise "\" to "/" and strip tabs and
 * newlines, so "/\t/evil.example" would become "//evil.example").
 */
export function safeNext(next: string | null | undefined, fallback = "/"): string {
  return next && /^\/(?![/\\])[^\x00-\x1f\x7f\\]*$/.test(next) ? next : fallback;
}
