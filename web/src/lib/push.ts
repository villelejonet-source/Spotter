import "server-only";
import { revalidatePath } from "next/cache";
import { unstable_rethrow } from "next/navigation";
import { createClient, requireUser } from "./supabase/server";
import { orderForPush, type SyncRowInput } from "./sync-writer";

/** Raised when the server kept a newer version of some rows than the ones sent. */
export class StaleWriteError extends Error {}

export const STALE_WRITE_MESSAGE = "Your phone changed this since you opened it. Reload to see the latest.";
export const SAVE_FAILED_MESSAGE = "Couldn't save. Try again.";
export const STORAGE_FULL_MESSAGE = "This account has reached its limit of synced rows.";

/**
 * Writes rows through push_rows (last-write-wins, own rows only) and refreshes every page.
 * Every row should be newer than what's stored; if the server skipped any, something else
 * wrote in between, so this throws StaleWriteError rather than half-saving silently. Any
 * other failure is logged here and rethrown with a message that's safe to show.
 */
export async function push(rows: SyncRowInput[]): Promise<void> {
  await requireUser();
  if (!rows.length) return;
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("push_rows", { rows: orderForPush(rows) });
  if (error) {
    console.error("push_rows failed", error.code, error.message);
    // push_rows raises 54000 (program_limit_exceeded) at the per-account row cap.
    throw new Error(error.code === "54000" ? STORAGE_FULL_MESSAGE : SAVE_FAILED_MESSAGE);
  }
  revalidatePath("/", "layout");
  if (data !== rows.length) throw new StaleWriteError(STALE_WRITE_MESSAGE);
}

const SHOWN_AS_IS = new Set([STALE_WRITE_MESSAGE, SAVE_FAILED_MESSAGE, STORAGE_FULL_MESSAGE]);

/**
 * The text an action returns for a failed save: push's own messages pass through, anything
 * else (a network failure, a bug) is logged and replaced, so no backend text reaches users.
 * Next's redirect and not-found signals are rethrown, since they aren't errors.
 */
export function saveErrorMessage(e: unknown): string {
  unstable_rethrow(e);
  if (e instanceof Error && SHOWN_AS_IS.has(e.message)) return e.message;
  console.error("save failed", e);
  return SAVE_FAILED_MESSAGE;
}
