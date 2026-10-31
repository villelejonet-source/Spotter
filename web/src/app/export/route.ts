import { NextResponse, type NextRequest } from "next/server";
import { exerciseName } from "@/lib/catalog";
import { getAllSessionExercises, getBodyWeight, getCatalog, getPersonalRecords, getPlans, getProfile, getScheduled, getSessions, getSetHistory } from "@/lib/data";
import { exportCsvRows, exportWorkouts } from "@/lib/export";
import { createClient } from "@/lib/supabase/server";

/** Personal data on the wire: never cached by a browser or a proxy. */
const NO_STORE = { "Cache-Control": "no-store, max-age=0", Vary: "Cookie" };

/** Downloads all of the signed-in user's synced data: ?format=json (everything) or csv (one row per set). */
export async function GET(request: NextRequest) {
  const supabase = await createClient();
  const { data: auth } = await supabase.auth.getClaims();
  if (!auth) return new NextResponse("Not signed in", { status: 401, headers: NO_STORE });

  const format = request.nextUrl.searchParams.get("format") === "csv" ? "csv" : "json";
  const stamp = new Date().toISOString().slice(0, 10);
  const iso = (ms: number | null) => (ms == null ? null : new Date(ms).toISOString());

  if (format === "csv") {
    const [sessions, exercises, sets, catalog] = await Promise.all([getSessions(), getAllSessionExercises(), getSetHistory(undefined, undefined, true), getCatalog()]);
    const header = ["workout_started", "workout", "exercise_number", "exercise", "exercise_id", "set", "set_type", "weight_kg", "reps_or_seconds", "rir", "rpe", "completed", "notes"];
    const lines = exportCsvRows(sessions, exercises, sets).map(({ session: w, exercisePosition, set: s }) =>
      [iso(w.started_at), w.plan_day_name ?? "Free workout", exercisePosition + 1, exerciseName(catalog, s.exercise_id), s.exercise_id, s.position + 1, s.set_type, s.weight_kg, s.reps, s.rir, s.rpe, s.completed_at != null, s.notes]
        .map(csvCell)
        .join(","),
    );
    return new NextResponse([header.join(","), ...lines].join("\n"), {
      headers: { ...NO_STORE, "Content-Type": "text/csv; charset=utf-8", "Content-Disposition": `attachment; filename="spotter-sets-${stamp}.csv"` },
    });
  }

  // One wave: every query is independent and runs under the same RLS.
  const [sessions, exercises, sets, catalog, profile, plans, records, bodyWeight, scheduled] = await Promise.all([
    getSessions(),
    getAllSessionExercises(),
    getSetHistory(undefined, undefined, true),
    getCatalog(),
    getProfile(),
    getPlans(),
    getPersonalRecords(),
    getBodyWeight(),
    getScheduled(),
  ]);
  const body = {
    exportedAt: new Date().toISOString(),
    profile,
    plans,
    workouts: exportWorkouts(sessions, exercises, sets, (id) => exerciseName(catalog, id)),
    scheduledWorkouts: scheduled.map((s) => ({ ...s, date: new Date(s.epoch_day * 86_400_000).toISOString().slice(0, 10) })),
    personalRecords: records.map((r) => ({ ...r, exercise: exerciseName(catalog, r.exercise_id) })),
    bodyWeight: bodyWeight.map((b) => ({ ...b, date: new Date(b.epoch_day * 86_400_000).toISOString().slice(0, 10) })),
    customExercises: [...catalog.values()].filter((e) => e.isCustom),
  };
  // Compact JSON: the file is for importing elsewhere, and pretty-printing roughly doubles it.
  return new NextResponse(JSON.stringify(withoutUserId(body)), {
    headers: { ...NO_STORE, "Content-Type": "application/json", "Content-Disposition": `attachment; filename="spotter-export-${stamp}.json"` },
  });
}

/**
 * Drops `user_id` wherever it appears. The views return it on every row, but it's the
 * account's internal id and means nothing outside Spotter.
 */
function withoutUserId(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withoutUserId);
  if (value && typeof value === "object" && Object.getPrototypeOf(value) === Object.prototype) {
    return Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => key !== "user_id")
        .map(([key, v]) => [key, withoutUserId(v)]),
    );
  }
  return value;
}

function csvCell(value: unknown): string {
  if (value == null) return "";
  let s = String(value);
  // Spreadsheets run cells starting with these as formulas; a leading ' keeps them text.
  if (typeof value === "string" && /^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
