import { redirect } from "next/navigation";

/**
 * Sign-up and sign-in are one step now: /login's email code makes the account for a new
 * address. Kept so old links and bookmarks still land somewhere useful.
 */
export default function SignupPage() {
  redirect("/login");
}
