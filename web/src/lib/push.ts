import "server-only";
import { revalidatePath } from "next/cache";
import { createClient } from "./supabase/server";
import { orderForPush, type SyncRowInput } from "./sync-writer";

/** Writes rows through push_rows (last-write-wins, RLS applies) and refreshes every page. */
export async function push(rows: SyncRowInput[]) {
  if (!rows.length) return;
  const supabase = await createClient();
  const { error } = await supabase.rpc("push_rows", { rows: orderForPush(rows) });
  if (error) throw new Error(error.message);
  revalidatePath("/", "layout");
}
