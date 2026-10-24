-- History: push_rows defined here (adds scheduled_session to the allow-list) is superseded by
-- 20261026000000_audit_hardening.sql, which runs last and keeps the same list. Don't edit:
-- add a new migration.
--
-- Scheduled workouts (app schema 7): workouts planned on dates, from the website's
-- calendar, shown on the app's Today tab. Safe to re-run.
--
-- 1. push_rows accepts the new table (same function as before, one more allowed name).
-- 2. v_scheduled_session for the website, with an index for date lookups.

-- ---- 1. Writes ----

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
                'workout_session', 'session_exercise', 'set_entry', 'personal_record', 'recommendation',
                'scheduled_session')
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

-- ---- 2. Read view ----

create index if not exists sync_rows_scheduled_date_idx
    on public.sync_rows (user_id, ((payload->>'date')::integer))
    where table_name = 'scheduled_session';

-- date is an epoch day (the app's LocalDate). plan_day_id and session_id are sync ids;
-- either can point at a deleted row, which the website treats as "free workout" and
-- "not started".
create or replace view public.v_scheduled_session with (security_invoker = true) as
select
    user_id,
    sync_id,
    (payload->>'date')::integer  as epoch_day,
    payload->>'planDayId'        as plan_day_id,
    payload->>'notes'            as notes,
    payload->>'sessionId'        as session_id,
    updated_at
from public.sync_rows
where table_name = 'scheduled_session' and deleted_at is null;

grant select on public.v_scheduled_session to authenticated;
revoke all on public.v_scheduled_session from anon;
