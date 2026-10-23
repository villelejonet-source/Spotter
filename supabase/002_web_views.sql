-- Spotter web: typed read views over sync_rows.
-- Run once in the Supabase dashboard (SQL Editor → New query → paste → Run), after
-- 001_sync.sql. Safe to re-run.
--
-- Each view unpacks one synced table's JSON payload into typed columns. The views run
-- with the caller's rights (security_invoker), so the RLS on sync_rows still decides
-- whose rows are visible. Deleted rows are left out. Columns keep the app's names
-- (snake_cased); foreign keys are the parent's sync id; times are epoch ms.

create or replace view public.v_profile with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'sex'                              as sex,
    (payload->>'heightCm')::double precision     as height_cm,
    (payload->>'birthDate')::integer             as birth_date_epoch_day,
    (payload->>'bodyWeightKg')::double precision as body_weight_kg,
    payload->>'units'                            as units,
    payload->>'experience'                       as experience,
    payload->>'goal'                             as goal,
    (payload->>'daysPerWeek')::integer           as days_per_week,
    (payload->>'sessionLengthMinutes')::integer  as session_length_minutes,
    payload->>'equipment'                        as equipment,
    payload->>'focusAreas'                       as focus_areas,
    payload->>'limitations'                      as limitations,
    updated_at
from public.sync_rows
where table_name = 'user_profile' and deleted_at is null;

create or replace view public.v_body_weight with (security_invoker = true) as
select
    user_id,
    sync_id,
    (payload->>'date')::integer              as epoch_day,
    (payload->>'weightKg')::double precision as weight_kg,
    updated_at
from public.sync_rows
where table_name = 'body_weight_entry' and deleted_at is null;

create or replace view public.v_custom_exercise with (security_invoker = true) as
select
    user_id,
    sync_id                                  as id,
    payload->>'name'                         as name,
    payload->>'primaryMuscles'               as primary_muscles,
    payload->>'secondaryMuscles'             as secondary_muscles,
    payload->>'movementPattern'              as movement_pattern,
    payload->>'equipment'                    as equipment,
    payload->>'mechanics'                    as mechanics,
    payload->>'difficulty'                   as difficulty,
    (payload->>'unilateral')::integer = 1    as unilateral,
    payload->>'instructions'                 as instructions,
    updated_at
from public.sync_rows
where table_name = 'exercise' and deleted_at is null;

create or replace view public.v_plan with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'name'                       as name,
    payload->>'splitType'                  as split_type,
    payload->>'goal'                       as goal,
    (payload->>'createdAt')::bigint        as created_at,
    (payload->>'isActive')::integer = 1    as is_active,
    updated_at
from public.sync_rows
where table_name = 'plan' and deleted_at is null;

create or replace view public.v_plan_day with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'planId'                 as plan_id,
    (payload->>'position')::integer    as position,
    payload->>'name'                   as name,
    updated_at
from public.sync_rows
where table_name = 'plan_day' and deleted_at is null;

create or replace view public.v_plan_exercise with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'planDayId'                        as plan_day_id,
    payload->>'exerciseId'                       as exercise_id,
    (payload->>'position')::integer              as position,
    (payload->>'sets')::integer                  as sets,
    (payload->>'repMin')::integer                as rep_min,
    (payload->>'repMax')::integer                as rep_max,
    (payload->>'targetRir')::integer             as target_rir,
    (payload->>'restSeconds')::integer           as rest_seconds,
    payload->>'progressionRule'                  as progression_rule,
    (payload->>'supersetGroup')::integer         as superset_group,
    (payload->>'startingWeightKg')::double precision as starting_weight_kg,
    updated_at
from public.sync_rows
where table_name = 'plan_exercise' and deleted_at is null;

create or replace view public.v_session with (security_invoker = true) as
select
    user_id,
    sync_id,
    (payload->>'startedAt')::bigint              as started_at,
    (payload->>'endedAt')::bigint                as ended_at,
    payload->>'planDayId'                        as plan_day_id,
    payload->>'notes'                            as notes,
    (payload->>'perceivedDifficulty')::integer   as perceived_difficulty,
    (payload->>'isDeload')::integer = 1          as is_deload,
    updated_at
from public.sync_rows
where table_name = 'workout_session' and deleted_at is null;

create or replace view public.v_session_exercise with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'sessionId'                        as session_id,
    payload->>'exerciseId'                       as exercise_id,
    (payload->>'position')::integer              as position,
    payload->>'substitutedFromExerciseId'        as substituted_from_exercise_id,
    (payload->>'supersetGroup')::integer         as superset_group,
    payload->>'notes'                            as notes,
    (payload->>'restSeconds')::integer           as rest_seconds,
    payload->>'planExerciseId'                   as plan_exercise_id,
    payload->>'progressionReason'                as progression_reason,
    updated_at
from public.sync_rows
where table_name = 'session_exercise' and deleted_at is null;

create or replace view public.v_set_entry with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'sessionExerciseId'                as session_exercise_id,
    (payload->>'position')::integer              as position,
    (payload->>'weightKg')::double precision     as weight_kg,
    (payload->>'reps')::integer                  as reps,
    (payload->>'rir')::integer                   as rir,
    (payload->>'rpe')::double precision          as rpe,
    payload->>'setType'                          as set_type,
    (payload->>'completedAt')::bigint            as completed_at,
    payload->>'notes'                            as notes,
    updated_at
from public.sync_rows
where table_name = 'set_entry' and deleted_at is null;

create or replace view public.v_personal_record with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'exerciseId'                       as exercise_id,
    payload->>'type'                             as type,
    (payload->>'value')::double precision        as value,
    (payload->>'weightKg')::double precision     as weight_kg,
    (payload->>'reps')::integer                  as reps,
    (payload->>'achievedAt')::bigint             as achieved_at,
    payload->>'sessionId'                        as session_id,
    updated_at
from public.sync_rows
where table_name = 'personal_record' and deleted_at is null;

-- One row per finished or running session, for lists and dashboards. Volume follows
-- the app's WorkoutSummary: completed non-warm-up sets, weight × reps, holds excluded.
create or replace view public.v_session_summary with (security_invoker = true) as
select
    s.user_id,
    s.sync_id,
    s.started_at,
    s.ended_at,
    s.plan_day_id,
    d.name                                                         as plan_day_name,
    d.plan_id,
    s.is_deload,
    s.notes,
    (select count(*) from public.v_session_exercise e where e.session_id = s.sync_id and e.user_id = s.user_id) as exercise_count,
    coalesce(agg.completed_sets, 0)                                as completed_sets,
    coalesce(agg.volume_kg, 0)                                     as volume_kg
from public.v_session s
left join public.v_plan_day d on d.sync_id = s.plan_day_id and d.user_id = s.user_id
left join lateral (
    select
        count(*) filter (where st.completed_at is not null) as completed_sets,
        sum(coalesce(st.weight_kg, 0) * coalesce(st.reps, 0)) filter (
            where st.completed_at is not null
              and st.set_type <> 'WARMUP'
              and e.exercise_id not in ('plank', 'side-plank', 'copenhagen-plank', 'hollow-body-hold')
        ) as volume_kg
    from public.v_session_exercise e
    join public.v_set_entry st on st.session_exercise_id = e.sync_id and st.user_id = e.user_id
    where e.session_id = s.sync_id and e.user_id = s.user_id
) agg on true;

grant select on
    public.v_profile, public.v_body_weight, public.v_custom_exercise,
    public.v_plan, public.v_plan_day, public.v_plan_exercise,
    public.v_session, public.v_session_exercise, public.v_set_entry,
    public.v_personal_record, public.v_session_summary
to authenticated;

revoke all on
    public.v_profile, public.v_body_weight, public.v_custom_exercise,
    public.v_plan, public.v_plan_day, public.v_plan_exercise,
    public.v_session, public.v_session_exercise, public.v_set_entry,
    public.v_personal_record, public.v_session_summary
from anon;
