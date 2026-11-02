-- 5/3/1 (app database version 10): a plan exercise's training max and current week, the week a
-- workout's exercise was started as, and which set was AMRAP (as many reps as possible). All
-- live in the synced payloads; older apps don't send them, so they read as null (or false).
-- The views only gain columns, at the end, as create or replace view requires.

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
    updated_at,
    (payload->>'trainingMaxKg')::double precision as training_max_kg,
    coalesce((payload->>'cycleWeek')::integer, 1) as cycle_week
from public.sync_rows
where table_name = 'plan_exercise' and deleted_at is null;

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
    updated_at,
    (payload->>'cycleWeek')::integer             as cycle_week
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
    updated_at,
    coalesce((payload->>'isAmrap')::integer = 1, false) as is_amrap
from public.sync_rows
where table_name = 'set_entry' and deleted_at is null;

-- The website's workout and exercise pages read sets through v_set_history.
create or replace view public.v_set_history with (security_invoker = true) as
select
    st.user_id,
    st.sync_id,
    st.session_exercise_id,
    e.session_id,
    e.exercise_id,
    s.started_at          as session_started_at,
    s.ended_at is not null as session_finished,
    st.position,
    st.weight_kg,
    st.reps,
    st.rir,
    st.rpe,
    st.set_type,
    st.completed_at,
    st.notes,
    coalesce(s.is_deload, false) as session_is_deload,
    s.readiness            as session_readiness,
    s.feel                 as session_feel,
    st.is_amrap
from public.v_set_entry st
join public.v_session_exercise e on e.user_id = st.user_id and e.sync_id = st.session_exercise_id
join public.v_session s on s.user_id = e.user_id and s.sync_id = e.session_id;
