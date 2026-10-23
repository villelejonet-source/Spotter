import "server-only";
import { revalidatePath } from "next/cache";
import { createClient, requireUser } from "./supabase/server";
import { orderForPush, type SyncRowInput } from "./sync-writer";

/** Raised when the server kept a newer version of some rows than the ones sent. */
export class StaleWriteError extends Error {}

/**
 * Writes rows through push_rows (last-write-wins, own rows only) and refreshes every page.
 * Every row should be newer than what's stored; if the server skipped any, something else
 * wrote in between, so this throws StaleWriteError rather than half-saving silently.
 */
export async function push(rows: SyncRowInput[]): Promise<void> {
  await requireUser();
  if (!rows.length) return;
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("push_rows", { rows: orderForPush(rows) });
  if (error) throw new Error(error.message);
  revalidatePath("/", "layout");
  if (data !== rows.length) throw new StaleWriteError("Some of your changes were older than what's saved. Reload and try again.");
}
