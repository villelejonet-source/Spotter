"use server";

import { getCatalog } from "@/lib/data";
import { push, saveErrorMessage } from "@/lib/push";
import { requireUser } from "@/lib/supabase/server";
import { customExercisePayload, customExerciseSchema, newCustomExerciseId, type CustomExerciseDraft } from "@/lib/sync-writer";
import type { ActionResult } from "./plans";

/** Creates a custom exercise; it syncs to the phone like one made in the app. */
export async function createCustomExercise(input: CustomExerciseDraft): Promise<ActionResult & { id?: string }> {
  await requireUser();
  const parsed = customExerciseSchema.safeParse(input, { error: () => "Check the exercise and try again." });
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? "Check the exercise and try again." };
  const name = parsed.data.name.toLowerCase();
  if ([...(await getCatalog()).values()].some((e) => e.name.toLowerCase() === name)) {
    return { ok: false, error: "An exercise with that name already exists." };
  }
  const id = newCustomExerciseId();
  try {
    await push([{ table_name: "exercise", sync_id: id, payload: customExercisePayload(id, parsed.data), updated_at: Date.now(), deleted_at: null }]);
  } catch (e) {
    return { ok: false, error: saveErrorMessage(e) };
  }
  return { ok: true, id };
}
