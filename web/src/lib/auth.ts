// Plain helpers for the sign-in, sign-up and password pages (no UI, so they're unit-tested).

export const MIN_PASSWORD_LENGTH = 8;

/** Why a new password isn't acceptable, or null. Supabase checks again on its side. */
export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  if (password !== confirm) return "The passwords don't match.";
  return null;
}

/** Supabase's error messages, in plain words. */
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
      return error.message;
  }
}

/** Only same-site paths, so a crafted link can't send people elsewhere after signing in. */
export function safeNext(next: string | null | undefined, fallback = "/"): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : fallback;
}
