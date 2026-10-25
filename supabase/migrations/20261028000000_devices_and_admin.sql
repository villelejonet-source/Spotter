-- Devices and the owner's usage overview (2026-10). Safe to re-run.
--
-- 1. devices: one row per app install that syncs. A random id the app makes on install, the
--    app's versionCode and Room schema version, and when it last synced. Apps from
--    versionCode 7 call check_in_device at the end of every successful sync; nothing else
--    about the phone is sent. This answers "which app versions are still in use?" before
--    the schema contracts or trainers are switched on (docs/platform/infrastructure.md,
--    "Versioning" step 6 and "Trainer foundation").
-- 2. admins: the accounts that may see the usage overview. Filled by hand in the SQL editor
--    (never in a migration: the repository is public and holds no account ids or emails).
-- 3. admin_stats(): dates and counts for the website's /admin page: accounts, sign-ups,
--    last sign-in, last active, workouts per week, weekly active users and the versionCode
--    spread. Never any workout contents.
-- 4. delete_my_account also removes the account's devices.
--
-- This is now the last migration in order, so it owns the final definition of
-- delete_my_account (20261027000000_trainer_foundation.sql had it before).
--
-- Kept on purpose (noted for the advisors):
-- - check_in_device, admin_stats and delete_my_account are SECURITY DEFINER with
--   search_path = '': devices is closed to direct writes, check_in_device only ever writes
--   auth.uid()'s rows, and admin_stats reads across accounts only for a caller listed in
--   admins (which no API role can read or write).

-- ---- 1. Devices ----

create table if not exists public.devices (
    user_id        uuid        not null references auth.users (id) on delete cascade,
    -- Random per install (a UUID the app makes once); not a hardware or advertising id.
    device_id      uuid        not null,
    version_code   integer     not null check (version_code between 1 and 2100000000),
    schema_version integer     not null check (schema_version between 1 and 10000),
    last_synced_at timestamptz not null default now(),
    created_at     timestamptz not null default now(),
    primary key (user_id, device_id)
);

create index if not exists devices_last_synced_idx on public.devices (last_synced_at);

alter table public.devices enable row level security;

drop policy if exists "devices: read own" on public.devices;
create policy "devices: read own" on public.devices
    for select to authenticated using ((select auth.uid()) = user_id);

revoke all on table public.devices from public, anon, authenticated;
grant select on table public.devices to authenticated;

-- Records that the caller's install [device_id] just synced, running [version_code] with
-- Room schema [schema_version]. An account keeps its 20 most recently synced installs;
-- older ones (reinstalls, old phones) are dropped. Errors: 42501 not signed in, 22023 a
-- missing or out-of-range value.
create or replace function public.check_in_device(device_id uuid, version_code integer, schema_version integer)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    device_cap constant integer := 20;
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    if check_in_device.device_id is null
       or check_in_device.version_code is null or check_in_device.version_code not between 1 and 2100000000
       or check_in_device.schema_version is null or check_in_device.schema_version not between 1 and 10000 then
        raise exception 'check_in_device: device id, version code and schema version are required' using errcode = '22023';
    end if;
    insert into public.devices as d (user_id, device_id, version_code, schema_version, last_synced_at)
    values (uid, check_in_device.device_id, check_in_device.version_code, check_in_device.schema_version, now())
    -- The constraint, not a column list: device_id is also a parameter name here.
    on conflict on constraint devices_pkey do update
        set version_code = excluded.version_code,
            schema_version = excluded.schema_version,
            last_synced_at = excluded.last_synced_at;
    delete from public.devices d
    where d.user_id = uid
      and d.device_id not in (
          select k.device_id from public.devices k
          where k.user_id = uid
          order by k.last_synced_at desc, k.device_id
          limit device_cap);
end;
$$;

revoke all on function public.check_in_device(uuid, integer, integer) from public, anon;
grant execute on function public.check_in_device(uuid, integer, integer) to authenticated;

-- ---- 2. Admins ----

-- Who may call admin_stats. Add or remove someone in the SQL editor:
--   insert into public.admins (user_id) select id from auth.users where email = '…';
create table if not exists public.admins (
    user_id    uuid        primary key references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);

alter table public.admins enable row level security;
-- No policies and no grants: only definer functions and the SQL editor see it.
revoke all on table public.admins from public, anon, authenticated;

-- ---- 3. Usage overview ----

-- One JSON object for the /admin page, covering the last [weeks] weeks (1-104; weeks start
-- on Monday, UTC). Workouts count finished, non-deleted workout_session rows by their
-- start date; their contents are never read. "Last active" is the later of the newest
-- synced row (written by the app or the website) and the newest device check-in. Errors:
-- 42501 not signed in or not an admin, 22023 weeks out of range.
create or replace function public.admin_stats(weeks integer default 12)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    since timestamptz;
    result jsonb;
begin
    if uid is null or not exists (select 1 from public.admins a where a.user_id = uid) then
        raise exception 'admin_stats: not allowed' using errcode = '42501';
    end if;
    if weeks is null or weeks not between 1 and 104 then
        raise exception 'admin_stats: weeks must be 1-104' using errcode = '22023';
    end if;
    since := date_trunc('week', now()) - make_interval(weeks => weeks - 1);

    with activity as (
        select s.user_id, max(s.server_updated_at) as last_synced_row
        from public.sync_rows s
        group by s.user_id
    ),
    device_latest as (
        select distinct on (d.user_id) d.user_id, d.version_code, d.last_synced_at
        from public.devices d
        order by d.user_id, d.last_synced_at desc
    ),
    -- Start times of finished workouts. startedAt is a client value, so anything that isn't
    -- a plausible number of milliseconds is skipped instead of failing the cast.
    workouts as (
        select s.user_id, to_timestamp((s.payload->>'startedAt')::numeric / 1000) as started_at
        from public.sync_rows s
        where s.table_name = 'workout_session'
          and s.deleted_at is null
          and jsonb_typeof(s.payload->'startedAt') = 'number'
          and jsonb_typeof(s.payload->'endedAt') = 'number'
          and (s.payload->>'startedAt')::numeric between 0 and 4102444800000
    ),
    users as (
        select
            u.id,
            u.email,
            u.created_at,
            u.last_sign_in_at,
            nullif(greatest(coalesce(a.last_synced_row, '-infinity'), coalesce(dl.last_synced_at, '-infinity')), '-infinity') as last_active_at,
            dl.version_code,
            coalesce(wc.n, 0) as workouts,
            coalesce(dc.n, 0) as devices
        from auth.users u
        left join activity a on a.user_id = u.id
        left join device_latest dl on dl.user_id = u.id
        left join (select w.user_id, count(*) as n from workouts w group by w.user_id) wc on wc.user_id = u.id
        left join (select d.user_id, count(*) as n from public.devices d group by d.user_id) dc on dc.user_id = u.id
    ),
    week_list as (
        select generate_series(since, date_trunc('week', now()), interval '1 week') as week
    )
    select jsonb_build_object(
        'generated_at', now(),
        'weeks', weeks,
        'totals', (
            select jsonb_build_object(
                'accounts', count(*),
                'confirmed', count(*) filter (where u.email_confirmed_at is not null),
                'active_7d', count(*) filter (where x.last_active_at > now() - interval '7 days'),
                'active_30d', count(*) filter (where x.last_active_at > now() - interval '30 days'),
                'signed_in_30d', count(*) filter (where u.last_sign_in_at > now() - interval '30 days'),
                'with_backup', count(*) filter (where x.last_active_at is not null),
                'workouts', (select count(*) from workouts),
                'devices', (select count(*) from public.devices)
            )
            from auth.users u join users x on x.id = u.id
        ),
        'by_week', (
            select coalesce(jsonb_agg(jsonb_build_object(
                'week', to_char(wl.week, 'YYYY-MM-DD'),
                'signups', (select count(*) from auth.users u
                            where u.created_at >= wl.week and u.created_at < wl.week + interval '1 week'),
                'workouts', (select count(*) from workouts w
                             where w.started_at >= wl.week and w.started_at < wl.week + interval '1 week'),
                'active_users', (select count(distinct w.user_id) from workouts w
                                 where w.started_at >= wl.week and w.started_at < wl.week + interval '1 week')
            ) order by wl.week), '[]'::jsonb)
            from week_list wl
        ),
        -- Installs that checked in during the last 30 days, by versionCode. Accounts active in
        -- that time without any check-in run versionCode 6 or older (or only use the website).
        'versions', (
            select coalesce(jsonb_agg(jsonb_build_object('version_code', v.version_code, 'devices', v.devices, 'accounts', v.accounts)
                                      order by v.version_code desc), '[]'::jsonb)
            from (
                select d.version_code, count(*) as devices, count(distinct d.user_id) as accounts
                from public.devices d
                where d.last_synced_at > now() - interval '30 days'
                group by d.version_code
            ) v
        ),
        'active_30d_without_check_in', (
            select count(*) from users x
            where x.last_active_at > now() - interval '30 days' and x.devices = 0
        ),
        'users', (
            select coalesce(jsonb_agg(jsonb_build_object(
                'email', x.email,
                'created_at', x.created_at,
                'last_sign_in_at', x.last_sign_in_at,
                'last_active_at', x.last_active_at,
                'workouts', x.workouts,
                'devices', x.devices,
                'version_code', x.version_code
            ) order by x.last_active_at desc nulls last, x.created_at desc), '[]'::jsonb)
            from (select * from users order by last_active_at desc nulls last, created_at desc limit 500) x
        )
    ) into result;
    return result;
end;
$$;

revoke all on function public.admin_stats(integer) from public, anon;
grant execute on function public.admin_stats(integer) to authenticated;

-- ---- 4. Account deletion ----

-- As in 20261027000000, plus the account's devices. (admins rows go with the auth user,
-- through the foreign key.)
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
    delete from public.devices where user_id = uid;
    delete from public.trainer_links where trainer_id = uid or client_id = uid;
    delete from public.profiles where user_id = uid;
    delete from public.sync_rows where user_id = uid;
    delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
