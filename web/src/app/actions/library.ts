"use server";

import { getCatalog } from "@/lib/data";
import { push } from "@/lib/push";
import { customExercisePayload, customExerciseSchema, newCustomExerciseId, type CustomExerciseDraft } from "@/lib/sync-writer";
import type { ActionResult } from "./plans";

/** Creates a custom exercise; it syncs to the phone like one made in the app. */
export async function createCustomExercise(input: CustomExerciseDraft): Promise<ActionResult & { id?: string }> {
  const parsed = customExerciseSchema.safeParse(input);
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? "Invalid exercise" };
  const name = parsed.data.name.toLowerCase();
  if ([...(await getCatalog()).values()].some((e) => e.name.toLowerCase() === name)) {
    return { ok: false, error: "An exercise with that name already exists" };
  }
  const id = newCustomExerciseId();
  try {
    await push([{ table_name: "exercise", sync_id: id, payload: customExercisePayload(id, parsed.data), updated_at: Date.now(), deleted_at: null }]);
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
  return { ok: true, id };
}
