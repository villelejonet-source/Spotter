import { NextResponse, type NextRequest } from "next/server";
import { exerciseName } from "@/lib/catalog";
import { getBodyWeight, getCatalog, getPersonalRecords, getPlans, getProfile, getSessions, getSetHistory } from "@/lib/data";
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
    const [sessions, sets, catalog] = await Promise.all([getSessions(), getSetHistory(undefined, undefined, true), getCatalog()]);
    const byId = new Map(sessions.map((s) => [s.sync_id, s]));
    const rows = sets
      .filter((s) => byId.has(s.session_id))
      .sort((a, b) => byId.get(a.session_id)!.started_at - byId.get(b.session_id)!.started_at || a.position - b.position);
    const header = ["workout_started", "workout", "exercise", "exercise_id", "set", "set_type", "weight_kg", "reps_or_seconds", "rir", "rpe", "completed", "notes"];
    const lines = rows.map((s) => {
      const w = byId.get(s.session_id)!;
      return [iso(w.started_at), w.plan_day_name ?? "Free workout", exerciseName(catalog, s.exercise_id), s.exercise_id, s.position + 1, s.set_type, s.weight_kg, s.reps, s.rir, s.rpe, s.completed_at != null, s.notes]
        .map(csvCell)
        .join(",");
    });
    return new NextResponse([header.join(","), ...lines].join("\n"), {
      headers: { ...NO_STORE, "Content-Type": "text/csv; charset=utf-8", "Content-Disposition": `attachment; filename="spotter-sets-${stamp}.csv"` },
    });
  }

  // One wave: every query is independent and runs under the same RLS.
  const [sessions, sets, catalog, profile, plans, records, bodyWeight] = await Promise.all([
    getSessions(),
    getSetHistory(undefined, undefined, true),
    getCatalog(),
    getProfile(),
    getPlans(),
    getPersonalRecords(),
    getBodyWeight(),
  ]);
  const setsBySession = Map.groupBy(sets, (s) => s.session_id);
  const body = {
    exportedAt: new Date().toISOString(),
    profile,
    plans,
    workouts: sessions.map((w) => {
      const own = setsBySession.get(w.sync_id) ?? [];
      const exercises = Map.groupBy(own, (s) => s.session_exercise_id);
      return {
        ...w,
        started_at_iso: iso(w.started_at),
        ended_at_iso: iso(w.ended_at),
        exercises: [...exercises.values()].map((list) => ({
          exercise_id: list[0].exercise_id,
          exercise: exerciseName(catalog, list[0].exercise_id),
          sets: list
            .sort((a, b) => a.position - b.position)
            .map((x) => ({ position: x.position, set_type: x.set_type, weight_kg: x.weight_kg, reps: x.reps, rir: x.rir, rpe: x.rpe, completed_at: iso(x.completed_at), notes: x.notes })),
        })),
      };
    }),
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
