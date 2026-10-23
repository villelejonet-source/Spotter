"use server";

import { getBodyWeight, getProfile } from "@/lib/data";
import { push } from "@/lib/push";
import { requireUser } from "@/lib/supabase/server";
import {
  bodyWeightPayload,
  bodyWeightSchema,
  newSyncId,
  PROFILE_SYNC_ID,
  profilePayload,
  profileDraftFrom,
  profileSchema,
  stamp,
  type ProfileDraft,
  type SyncRowInput,
} from "@/lib/sync-writer";
import type { ActionResult } from "./plans";

/**
 * Saves the training profile. Only an existing profile is edited; onboarding stays in the
 * app. [baseUpdatedAt] is the version the form loaded, so a change made on the phone in
 * the meantime isn't overwritten.
 */
export async function updateProfile(input: ProfileDraft, baseUpdatedAt: number): Promise<ActionResult> {
  await requireUser();
  const parsed = profileSchema.safeParse(input);
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? "Invalid profile" };
  const current = await getProfile();
  if (!current) return { ok: false, error: "Set up your profile in the app first" };
  if (current.updated_at > baseUpdatedAt) {
    return { ok: false, error: "Your profile changed on another device since you opened this page. Reload to get the latest version." };
  }
  try {
    await push([profileRow(parsed.data, current.updated_at)]);
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
  return { ok: true };
}

function profileRow(p: ProfileDraft, previous: number): SyncRowInput {
  return { table_name: "user_profile", sync_id: PROFILE_SYNC_ID, payload: profilePayload(p), updated_at: stamp(Date.now(), previous), deleted_at: null };
}

/**
 * Logs body weight for a day, replacing that day's entry like the app does, and moves the
 * profile's body weight to the latest entry (BodyWeightRepository.log).
 */
export async function logBodyWeight(epochDay: number, weightKg: number): Promise<ActionResult> {
  await requireUser();
  const parsed = bodyWeightSchema.safeParse({ epoch_day: epochDay, weight_kg: weightKg });
  if (!parsed.success) return { ok: false, error: parsed.error.issues[0]?.message ?? "Invalid weight" };
  const [entries, profile] = await Promise.all([getBodyWeight(), getProfile()]);
  const existing = entries.find((e) => e.epoch_day === epochDay);
  const now = Date.now();
  const rows: SyncRowInput[] = [
    {
      table_name: "body_weight_entry",
      sync_id: existing?.sync_id ?? newSyncId(),
      payload: bodyWeightPayload(epochDay, parsed.data.weight_kg),
      updated_at: stamp(now, existing?.updated_at),
      deleted_at: null,
    },
  ];
  const latestDay = Math.max(epochDay, ...entries.map((e) => e.epoch_day));
  if (profile && latestDay === epochDay) rows.push(profileRow({ ...profileDraftFrom(profile), body_weight_kg: parsed.data.weight_kg }, profile.updated_at));
  try {
    await push(rows);
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
  return { ok: true };
}

export async function deleteBodyWeight(syncId: string): Promise<ActionResult> {
  await requireUser();
  const entry = (await getBodyWeight()).find((e) => e.sync_id === syncId);
  if (!entry) return { ok: true };
  const at = stamp(Date.now(), entry.updated_at);
  await push([{ table_name: "body_weight_entry", sync_id: syncId, payload: null, updated_at: at, deleted_at: at }]);
  return { ok: true };
}
