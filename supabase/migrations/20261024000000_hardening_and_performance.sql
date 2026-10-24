-- History: first hardening pass. push_rows and delete_my_account defined here are superseded
-- by 20261025000000_scheduled_sessions.sql and finally 20261026000000_audit_hardening.sql,
-- which runs last. Don't edit: add a new migration.
--
-- Hardening and performance for sync_rows and the web views. Safe to re-run.
--
-- 1. Writes go only through push_rows, which now checks table names and sizes.
-- 2. Indexes for the joins the views make on JSON fields.
-- 3. v_session_summary aggregates with grouped joins instead of a subquery per session.
-- 4. v_set_history: sets with their exercise and workout start, filterable on the server.
-- 5. v_body_weight shows one entry per day even if two devices logged the same date.

-- ---- 1. Writes ----

-- push_rows runs as its owner so the tables can be closed to direct writes. It only ever
-- writes the caller's own rows: user_id is always auth.uid(), and the conflict target
-- includes user_id, so another user's row can never be matched.
create or replace function public.push_rows(rows jsonb)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    written integer;
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    if jsonb_typeof(rows) <> 'array' or jsonb_array_length(rows) > 1000 then
        raise exception 'push_rows takes an array of at most 1000 rows' using errcode = '22023';
    end if;
    if exists (
        select 1 from jsonb_array_elements(rows) r
        where r->>'table_name' not in (
                'exercise', 'user_profile', 'body_weight_entry', 'plan', 'plan_day', 'plan_exercise',
                'workout_session', 'session_exercise', 'set_entry', 'personal_record', 'recommendation')
           or coalesce(octet_length(r->>'payload'), 0) > 16384
           or r->>'sync_id' is null or length(r->>'sync_id') > 100
    ) then
        raise exception 'push_rows: unknown table, missing id or oversized row' using errcode = '22023';
    end if;

    with incoming as (
        select *
        from jsonb_to_recordset(rows) as r(table_name text, sync_id text, payload jsonb, updated_at bigint, deleted_at bigint)
    ),
    upserted as (
        insert into public.sync_rows (user_id, table_name, sync_id, payload, updated_at, deleted_at, server_updated_at)
        select uid, table_name, sync_id, payload, updated_at, deleted_at, clock_timestamp()
        from incoming
        on conflict (user_id, table_name, sync_id) do update
            set payload = excluded.payload,
                updated_at = excluded.updated_at,
                deleted_at = excluded.deleted_at,
                server_updated_at = clock_timestamp()
            where excluded.updated_at > public.sync_rows.updated_at
        returning 1
    )
    select count(*)::integer into written from upserted;
    return written;
end;
$$;

revoke all on function public.push_rows(jsonb) from public, anon;
grant execute on function public.push_rows(jsonb) to authenticated;

-- Reads stay open to the owner (RLS); inserts, updates and deletes go through the functions.
revoke insert, update, delete on public.sync_rows from anon, authenticated;

create or replace function public.delete_my_account()
returns void
language sql
security definer
set search_path = ''
as $$
    delete from auth.users where id = auth.uid();
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- ---- 2. Indexes for the views' joins ----

create index if not exists sync_rows_session_exercise_session_idx
    on public.sync_rows (user_id, (payload->>'sessionId'))
    where table_name = 'session_exercise';

create index if not exists sync_rows_set_entry_exercise_idx
    on public.sync_rows (user_id, (payload->>'sessionExerciseId'))
    where table_name = 'set_entry';

create index if not exists sync_rows_plan_day_plan_idx
    on public.sync_rows (user_id, (payload->>'planId'))
    where table_name = 'plan_day';

create index if not exists sync_rows_plan_exercise_day_idx
    on public.sync_rows (user_id, (payload->>'planDayId'))
    where table_name = 'plan_exercise';

-- ---- 3. Session summary: aggregate once, then join ----

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
    coalesce(t.volume_kg, 0)            as volume_kg
from public.v_session s
left join public.v_plan_day d on d.user_id = s.user_id and d.sync_id = s.plan_day_id
left join exercises x on x.user_id = s.user_id and x.session_id = s.sync_id
left join sets t on t.user_id = s.user_id and t.session_id = s.sync_id;

-- ---- 4. Set history ----

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
    st.notes
from public.v_set_entry st
join public.v_session_exercise e on e.user_id = st.user_id and e.sync_id = st.session_exercise_id
join public.v_session s on s.user_id = e.user_id and s.sync_id = e.session_id;

-- ---- 5. One body weight entry per day ----

create or replace view public.v_body_weight with (security_invoker = true) as
select distinct on (user_id, (payload->>'date')::integer)
    user_id,
    sync_id,
    (payload->>'date')::integer              as epoch_day,
    (payload->>'weightKg')::double precision as weight_kg,
    updated_at
from public.sync_rows
where table_name = 'body_weight_entry' and deleted_at is null
order by user_id, (payload->>'date')::integer, updated_at desc;

grant select on public.v_session_summary, public.v_set_history, public.v_body_weight to authenticated;
revoke all on public.v_session_summary, public.v_set_history, public.v_body_weight from anon;
