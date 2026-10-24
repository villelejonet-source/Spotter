"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { DELETE_CONFIRMATION, RECOVERY_COOKIE } from "@/lib/auth";
import { createClient, requireUser } from "@/lib/supabase/server";
import type { ActionResult } from "./plans";

export async function signOut() {
  const supabase = await createClient();
  await supabase.auth.signOut();
  // A password-reset session that signs out shouldn't leave the reset page open.
  (await cookies()).delete(RECOVERY_COOKIE);
  redirect("/login?notice=signed-out");
}

/**
 * Deletes the account and every backed-up row (delete_my_account in
 * supabase/migrations/20261026000000_audit_hardening.sql). Needs the signed-in user to have
 * typed DELETE_CONFIRMATION or their email, as a form field named "confirm" or as the
 * argument, so a stray click or a forged request can't do it. Returns the reason when it
 * doesn't delete; redirects to /login when it does.
 */
export async function confirmDeleteAccount(confirmation: FormData | string): Promise<ActionResult> {
  const { supabase, user } = await requireUser();
  const typed = (confirmation instanceof FormData ? confirmation.get("confirm") : confirmation) ?? "";
  const confirmed =
    typeof typed === "string" &&
    (typed.trim() === DELETE_CONFIRMATION || (!!user.email && typed.trim().toLowerCase() === user.email.toLowerCase()));
  if (!confirmed) return { ok: false, error: `Type ${DELETE_CONFIRMATION} or your email to confirm.` };

  const { error } = await supabase.rpc("delete_my_account");
  if (error) {
    console.error("delete_my_account failed", error.code);
    return { ok: false, error: "Couldn't delete the account. Try again." };
  }
  await supabase.auth.signOut();
  (await cookies()).delete(RECOVERY_COOKIE);
  redirect("/login?notice=account-deleted");
}

/**
 * Same as confirmDeleteAccount, for callers that can't show a result (a form action or a
 * transition): a missing or wrong confirmation throws, so nothing is deleted without one.
 */
export async function deleteAccount(confirmation?: FormData | string): Promise<void> {
  const result = await confirmDeleteAccount(confirmation ?? "");
  if (!result.ok) throw new Error(result.error);
}
