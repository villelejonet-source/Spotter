-- Audit hardening (2026-10). Safe to re-run.
--
-- This is the last migration in order, so it owns the final definitions of the sync_rows
-- policies and grants, push_rows and delete_my_account. Earlier files still create them
-- (so a fresh database works at every step) but say at the top that this file supersedes
-- them. Compatible with 0.10.x apps: push_rows keeps its signature, allow-list and
-- last-write-wins behaviour; every change is additive or a stricter check on bad input.
--
-- 1. RLS policies evaluate auth.uid() once per statement, not once per row.
-- 2. sync_rows is closed to anon entirely, and to everything but select for authenticated.
-- 3. push_rows also checks the shape of each row (object payload, numeric client times
--    within a sane window, no row twice in one batch) and a per-account row cap.
-- 4. delete_my_account deletes the backup rows explicitly before the auth user.
-- 5. v_exercise_usage: workouts per exercise, grouped on the server (exercise library).
--
-- Kept on purpose (noted for the advisors):
-- - push_rows and delete_my_account are SECURITY DEFINER: the first so the table can stay
--   closed to direct writes, the second because only a definer can delete from auth.users.
--   Both pin search_path = '' and act only on auth.uid()'s rows.
-- - sync_rows_set_entry_exercise_idx (20261024000000) stays: it serves the set_entry →
--   session_exercise join in v_set_history and v_session_summary once a user has more sets
--   than fit a sequential scan, even if the advisor shows it unused on today's volumes.

-- ---- 1. Row-level security: (select auth.uid()) is an InitPlan, run once per query ----

alter table public.sync_rows enable row level security;

drop policy if exists "sync_rows: read own" on public.sync_rows;
create policy "sync_rows: read own" on public.sync_rows
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "sync_rows: insert own" on public.sync_rows;
create policy "sync_rows: insert own" on public.sync_rows
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "sync_rows: update own" on public.sync_rows;
create policy "sync_rows: update own" on public.sync_rows
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "sync_rows: delete own" on public.sync_rows;
create policy "sync_rows: delete own" on public.sync_rows
    for delete to authenticated using ((select auth.uid()) = user_id);

-- ---- 2. Table grants: Supabase's defaults granted everything to anon and authenticated ----

revoke all on table public.sync_rows from public, anon;
revoke insert, update, delete, truncate, references, trigger on table public.sync_rows from authenticated;
grant select on table public.sync_rows to authenticated;

-- ---- 3. push_rows ----

-- Runs as its owner so the table can stay closed to direct writes. It only ever writes the
-- caller's own rows: user_id is always auth.uid(), and the conflict target includes
-- user_id, so another user's row can never be matched. Every check raises a plain message
-- with a stable SQLSTATE (22023 bad input, 54000 over the cap, 42501 not signed in) instead
-- of letting a cast or constraint fail with database text.
create or replace function public.push_rows(rows jsonb)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    -- Client clocks decide last-write-wins; a clock more than a day ahead is a broken
    -- device, and letting it through would freeze every other device's writes to the row.
    max_updated_at bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint + 86400000;
    row_cap constant integer := 500000;
    existing bigint;
    written integer;
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    if rows is null or jsonb_typeof(rows) <> 'array' or jsonb_array_length(rows) > 1000 then
        raise exception 'push_rows takes an array of at most 1000 rows' using errcode = '22023';
    end if;
    if exists (select 1 from jsonb_array_elements(rows) r where jsonb_typeof(r) <> 'object') then
        raise exception 'push_rows: every row must be a JSON object' using errcode = '22023';
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
    -- A live row carries its columns as an object; a deleted row has no payload.
    if exists (
        select 1 from jsonb_array_elements(rows) r
        where not (
            coalesce(jsonb_typeof(r->'payload'), 'null') = 'object'
            or (coalesce(jsonb_typeof(r->'payload'), 'null') = 'null'
                and coalesce(jsonb_typeof(r->'deleted_at'), 'null') = 'number')
        )
    ) then
        raise exception 'push_rows: payload must be a JSON object, or null for a deleted row' using errcode = '22023';
    end if;
    -- Times are whole milliseconds. Checked for type first so the range check can cast.
    if exists (
        select 1 from jsonb_array_elements(rows) r
        where coalesce(jsonb_typeof(r->'updated_at'), 'null') <> 'number'
           or coalesce(jsonb_typeof(r->'deleted_at'), 'null') not in ('number', 'null')
    ) then
        raise exception 'push_rows: updated_at and deleted_at must be times in milliseconds' using errcode = '22023';
    end if;
    if exists (
        select 1 from jsonb_array_elements(rows) r
        where (r->>'updated_at')::numeric <> floor((r->>'updated_at')::numeric)
           or (r->>'updated_at')::numeric < 0
           or (r->>'updated_at')::numeric > max_updated_at
           or (r->>'deleted_at' is not null and (
                   (r->>'deleted_at')::numeric <> floor((r->>'deleted_at')::numeric)
                or (r->>'deleted_at')::numeric < 0
                or (r->>'deleted_at')::numeric > max_updated_at))
    ) then
        raise exception 'push_rows: updated_at and deleted_at must be whole milliseconds, not more than a day in the future' using errcode = '22023';
    end if;
    -- The upsert can't change one row twice in a statement; say so instead of failing on it.
    if exists (
        select 1 from jsonb_array_elements(rows) r
        group by r->>'table_name', r->>'sync_id'
        having count(*) > 1
    ) then
        raise exception 'push_rows: the same row appears more than once in one batch' using errcode = '22023';
    end if;
    -- Per-account cap: a few years of daily training is well under 100 000 rows.
    select count(*) into existing from public.sync_rows where user_id = uid;
    if existing + jsonb_array_length(rows) > row_cap then
        raise exception 'push_rows: this account has reached its limit of % synced rows', row_cap using errcode = '54000';
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

-- ---- 4. Account deletion ----

-- SECURITY DEFINER on purpose: deleting from auth.users needs the owner's rights, and only
-- the caller's own user can ever match. The backup rows go first so nothing relies on the
-- foreign key's cascade (the advisor's WARN on this function is expected).
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    delete from public.sync_rows where user_id = uid;
    delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- ---- 5. Exercise usage, grouped on the server ----

-- One row per exercise the user has done in a finished workout: how many workouts, and when
-- the latest one started (epoch ms). The library page used to count this in JavaScript over
-- every session_exercise row.
create or replace view public.v_exercise_usage with (security_invoker = true) as
select
    e.user_id,
    e.exercise_id,
    count(distinct e.session_id)::integer as session_count,
    max(s.started_at)                     as last_used
from public.v_session_exercise e
join public.v_session s on s.user_id = e.user_id and s.sync_id = e.session_id
where s.ended_at is not null
group by e.user_id, e.exercise_id;

grant select on public.v_exercise_usage to authenticated;
revoke all on public.v_exercise_usage from public, anon;
