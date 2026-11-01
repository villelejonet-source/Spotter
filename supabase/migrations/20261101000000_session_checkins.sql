-- Check-ins: how the person felt before a workout (readiness, 1–5) and how it went after
-- (feel, 1–5), added by app database version 9. Both live in the synced workout_session
-- payload; older apps don't send them, so they read as null. The views only gain columns
-- (at the end, as create or replace view requires), so nothing that reads them changes.

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
    updated_at,
    (payload->>'readiness')::integer             as readiness,
    (payload->>'feel')::integer                  as feel
from public.sync_rows
where table_name = 'workout_session' and deleted_at is null;

create or replace view public.v_session_summary with (security_invoker = true) as
with exercises as (
    select user_id, session_id, count(*) as exercise_count
    from public.v_session_exercise
    group by user_id, session_id
),
sets as (
    select
        e.user_id,
        e.session_id,
        count(*) filter (where st.completed_at is not null) as completed_sets,
        sum(coalesce(st.weight_kg, 0) * coalesce(st.reps, 0)) filter (
            where st.completed_at is not null
              and st.set_type <> 'WARMUP'
              and e.exercise_id not in ('plank', 'side-plank', 'copenhagen-plank', 'hollow-body-hold')
        ) as volume_kg
    from public.v_session_exercise e
    join public.v_set_entry st on st.user_id = e.user_id and st.session_exercise_id = e.sync_id
    group by e.user_id, e.session_id
)
select
    s.user_id,
    s.sync_id,
    s.started_at,
    s.ended_at,
    s.plan_day_id,
    d.name                              as plan_day_name,
    d.plan_id,
    s.is_deload,
    s.notes,
    coalesce(x.exercise_count, 0)       as exercise_count,
    coalesce(t.completed_sets, 0)       as completed_sets,
    coalesce(t.volume_kg, 0)            as volume_kg,
    s.readiness,
    s.feel,
    s.perceived_difficulty
from public.v_session s
left join public.v_plan_day d on d.user_id = s.user_id and d.sync_id = s.plan_day_id
left join exercises x on x.user_id = s.user_id and x.session_id = s.sync_id
left join sets t on t.user_id = s.user_id and t.session_id = s.sync_id;
