"use server";

import { redirect } from "next/navigation";
import { getWorkoutForDelete } from "@/lib/data";
import { push, saveErrorMessage } from "@/lib/push";
import { requireUser } from "@/lib/supabase/server";
import { deleteWorkoutRows, syncIdSchema } from "@/lib/sync-writer";
import type { ActionResult } from "./plans";

/**
 * Deletes a finished workout, its sets and the records set in it, everywhere (the app drops it
 * on its next sync). A workout still in progress is the phone's: it's left alone.
 */
export async function deleteWorkout(syncId: string): Promise<ActionResult> {
  await requireUser();
  if (!syncIdSchema.safeParse(syncId).success) redirect("/history");
  const workout = await getWorkoutForDelete(syncId);
  if (!workout) redirect("/history"); // already gone
  if (workout.session.ended_at == null) return { ok: false, error: "This workout is still in progress on your phone." };
  try {
    await push(deleteWorkoutRows(workout, Date.now()));
  } catch (e) {
    return { ok: false, error: saveErrorMessage(e) };
  }
  redirect("/history");
}
