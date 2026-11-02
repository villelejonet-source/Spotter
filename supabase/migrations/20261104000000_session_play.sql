-- One row per finished workout with what levels and badges count (core/engine Gamification.kt,
-- web/src/lib/gamification.ts), so the website reads one small row per workout instead of every
-- set. A new view only: nothing that exists changes.
--
-- working_sets leaves warm-ups out (v_session_summary.completed_sets doesn't), exercise_ids are
-- the exercises with a completed working set, and record_types has one entry per record.

create view public.v_session_play with (security_invoker = true) as
with sets as (
    select
        e.user_id,
        e.session_id,
        count(*)::integer as working_sets,
        array_agg(distinct e.exercise_id) as exercise_ids
    from public.v_session_exercise e
    join public.v_set_entry st on st.user_id = e.user_id and st.session_exercise_id = e.sync_id
    where st.completed_at is not null and st.set_type <> 'WARMUP'
    group by e.user_id, e.session_id
),
records as (
    select user_id, session_id, array_agg(type) as record_types
    from public.v_personal_record
    where session_id is not null
    group by user_id, session_id
)
select
    s.user_id,
    s.sync_id,
    s.started_at,
    s.ended_at,
    s.is_deload,
    s.readiness,
    s.feel,
    coalesce(t.working_sets, 0)                 as working_sets,
    s.volume_kg,
    coalesce(t.exercise_ids, '{}'::text[])      as exercise_ids,
    coalesce(r.record_types, '{}'::text[])      as record_types
from public.v_session_summary s
left join sets t on t.user_id = s.user_id and t.session_id = s.sync_id
left join records r on r.user_id = s.user_id and r.session_id = s.sync_id
where s.ended_at is not null;

grant select on public.v_session_play to authenticated;
revoke all on public.v_session_play from public, anon;
