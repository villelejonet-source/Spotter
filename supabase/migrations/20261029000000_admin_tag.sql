-- Admin as a tag on the account (2026-10). Safe to re-run.
--
-- 20261028000000 kept admins in a table. An admin is now any account whose auth
-- app_metadata has "admin": true. app_metadata is set only with the SQL editor or the
-- service key, never by the user (unlike user_metadata, which a signed-in user can change),
-- and it travels in the session token, so the website shows the Admin menu without a query.
-- admin_stats checks auth.users itself, so removing the tag closes the data at once; the
-- menu goes when the session token next refreshes (within the hour).
--
-- Tag or untag an account in the SQL editor (never in a migration: the repository is public):
--   update auth.users set raw_app_meta_data = coalesce(raw_app_meta_data, '{}') || '{"admin": true}'
--   where email = '…';
--   update auth.users set raw_app_meta_data = raw_app_meta_data - 'admin' where email = '…';
--
-- 1. Accounts in public.admins get the tag, and the table is dropped (only admin_stats read it).
-- 2. admin_stats checks the tag. Otherwise as in 20261028000000, which this file supersedes for
--    admin_stats.

-- ---- 1. From the admins table to the tag ----

do $$
begin
    if to_regclass('public.admins') is not null then
        update auth.users u
        set raw_app_meta_data = coalesce(u.raw_app_meta_data, '{}'::jsonb) || '{"admin": true}'::jsonb
        where u.id in (select a.user_id from public.admins a);
        drop table public.admins;
    end if;
end;
$$;

-- ---- 2. Usage overview ----

-- One JSON object for the /admin page, covering the last [weeks] weeks (1-104; weeks start
-- on Monday, UTC). Workouts count finished, non-deleted workout_session rows by their
-- start date; their contents are never read. "Last active" is the later of the newest
-- synced row (written by the app or the website) and the newest device check-in. Errors:
-- 42501 not signed in or not tagged admin, 22023 weeks out of range.
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
    if uid is null or not exists (
        select 1 from auth.users u where u.id = uid and u.raw_app_meta_data->>'admin' = 'true'
    ) then
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
