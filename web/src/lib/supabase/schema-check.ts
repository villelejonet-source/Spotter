/**
 * Compile-time check that the website's row types still match the database. database.types.ts
 * is generated from supabase/migrations (`npm run db:types`); if a migration renames, drops or
 * retypes a column a page reads, `npm run typecheck` fails here instead of the page breaking in
 * production. Nothing in this file runs.
 */
import type { CustomExerciseRow } from "@/lib/catalog";
import type {
  BodyWeightRow,
  PersonalRecordRow,
  PlanDayRow,
  PlanExerciseRow,
  PlanRow,
  ProfileRow,
  ScheduledRow,
  SessionExerciseRow,
  SessionSummaryRow,
  SetEntryRow,
} from "@/lib/model";
import type { SetWithExercise } from "@/lib/queries";
import type { Database } from "./database.types";

type Views = Database["public"]["Views"];

// View columns are always nullable in the generated types, and enums come out as plain strings,
// so compare the underlying kind: string, number, boolean or JSON.
type Kind<T> = [T] extends [string] ? string : [T] extends [number] ? number : [T] extends [boolean] ? boolean : unknown;

/** True when every field of T is a column of view V of the same kind; otherwise the failing field names. */
type Matches<T, V extends keyof Views> = {
  [K in keyof T]-?: K extends keyof Views[V]["Row"]
    ? Kind<NonNullable<Views[V]["Row"][K]>> extends Kind<NonNullable<T[K]>>
      ? never
      : K
    : K;
}[keyof T] extends never
  ? true
  : { view: V; mismatched: { [K in keyof T]-?: K extends keyof Views[V]["Row"] ? (Kind<NonNullable<Views[V]["Row"][K]>> extends Kind<NonNullable<T[K]>> ? never : K) : K }[keyof T] };

type Expect<T extends true> = T;

export type SchemaCheck = [
  Expect<Matches<ProfileRow, "v_profile">>,
  Expect<Matches<CustomExerciseRow, "v_custom_exercise">>,
  Expect<Matches<PlanRow, "v_plan">>,
  Expect<Matches<PlanDayRow, "v_plan_day">>,
  Expect<Matches<PlanExerciseRow, "v_plan_exercise">>,
  Expect<Matches<SessionSummaryRow, "v_session_summary">>,
  Expect<Matches<SessionExerciseRow, "v_session_exercise">>,
  Expect<Matches<SetEntryRow, "v_set_history">>,
  Expect<Matches<SetWithExercise, "v_set_history">>,
  Expect<Matches<PersonalRecordRow, "v_personal_record">>,
  Expect<Matches<ScheduledRow, "v_scheduled_session">>,
  Expect<Matches<BodyWeightRow, "v_body_weight">>,
];
