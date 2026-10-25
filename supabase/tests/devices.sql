-- Checks for devices, check_in_device, admins, admin_stats and delete_my_account after every
-- migration in supabase/migrations/ has been applied. Like push_rows.sql it runs in one
-- transaction that is rolled back, and CI does not run it. Run it the same way, against a
-- local stack or a throwaway project, never production:
--   docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < supabase/tests/devices.sql
-- (docs/platform/infrastructure.md, "Database migrations", has the full local steps).
-- A failing check raises an exception naming the case; success prints
-- "devices: all checks passed".

begin;

-- U is a user with two phones, O another user, and M the admin.
insert into auth.users (id, email, created_at) values
    ('00000000-0000-4000-8000-000000000011', 'u@example.test', now() - interval '20 days'),
    ('00000000-0000-4000-8000-000000000012', 'o@example.test', now()),
    ('00000000-0000-4000-8000-000000000013', 'm@example.test', now());

create function pg_temp.expect_error(label text, sql text, state text) returns void
language plpgsql as $$
begin
    execute sql;
    raise exception '% : expected SQLSTATE % but the statement succeeded', label, state;
exception
    when others then
        if sqlstate = state then return; end if;
        raise exception '% : expected SQLSTATE %, got % (%)', label, state, sqlstate, sqlerrm;
end;
$$;

create function pg_temp.expect(label text, actual anyelement, expected anyelement) returns void
language plpgsql as $$
begin
    if actual is distinct from expected then
        raise exception '% : expected %, got %', label, expected, actual;
    end if;
end;
$$;

create function pg_temp.act_as(uid uuid) returns void
language plpgsql as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
end;
$$;

-- ---- Signed out ----

select set_config('request.jwt.claims', '', true);
select pg_temp.expect_error('signed out check-in',
    $$select public.check_in_device('10000000-0000-4000-8000-000000000001', 7, 8)$$, '42501');
select pg_temp.expect_error('signed out stats', $$select public.admin_stats()$$, '42501');

-- ---- Check-in (user U) ----

select pg_temp.act_as('00000000-0000-4000-8000-000000000011');

select pg_temp.expect_error('missing device id', $$select public.check_in_device(null, 7, 8)$$, '22023');
select pg_temp.expect_error('version code zero',
    $$select public.check_in_device('10000000-0000-4000-8000-000000000001', 0, 8)$$, '22023');
select pg_temp.expect_error('missing schema version',
    $$select public.check_in_device('10000000-0000-4000-8000-000000000001', 7, null)$$, '22023');
select pg_temp.expect_error('schema version too large',
    $$select public.check_in_device('10000000-0000-4000-8000-000000000001', 7, 10001)$$, '22023');

select public.check_in_device('10000000-0000-4000-8000-000000000001', 7, 8);
select pg_temp.expect('first check-in stored',
    (select version_code from public.devices where device_id = '10000000-0000-4000-8000-000000000001'), 7);
select pg_temp.expect('row belongs to the caller',
    (select user_id from public.devices where device_id = '10000000-0000-4000-8000-000000000001'),
    '00000000-0000-4000-8000-000000000011'::uuid);

-- An update moves the same row forward instead of adding one.
update public.devices set last_synced_at = now() - interval '3 days', created_at = now() - interval '3 days'
where device_id = '10000000-0000-4000-8000-000000000001';
select public.check_in_device('10000000-0000-4000-8000-000000000001', 8, 9);
select pg_temp.expect('one row per install', (select count(*) from public.devices where user_id = '00000000-0000-4000-8000-000000000011'), 1::bigint);
select pg_temp.expect('version updated',
    (select version_code from public.devices where device_id = '10000000-0000-4000-8000-000000000001'), 8);
select pg_temp.expect('schema updated',
    (select schema_version from public.devices where device_id = '10000000-0000-4000-8000-000000000001'), 9);
select pg_temp.expect('last synced moved forward',
    (select last_synced_at > now() - interval '1 minute' from public.devices where device_id = '10000000-0000-4000-8000-000000000001'), true);
select pg_temp.expect('created kept',
    (select created_at < now() - interval '2 days' from public.devices where device_id = '10000000-0000-4000-8000-000000000001'), true);

-- A second phone.
select public.check_in_device('10000000-0000-4000-8000-000000000002', 7, 8);
select pg_temp.expect('two installs', (select count(*) from public.devices where user_id = '00000000-0000-4000-8000-000000000011'), 2::bigint);

-- At most 20 installs per account: the least recently synced go first.
update public.devices set last_synced_at = now() - interval '10 days' where device_id = '10000000-0000-4000-8000-000000000002';
select public.check_in_device(('20000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid, 7, 8) from generate_series(1, 19) i;
select pg_temp.expect('capped at 20', (select count(*) from public.devices where user_id = '00000000-0000-4000-8000-000000000011'), 20::bigint);
select pg_temp.expect('oldest install dropped',
    (select count(*) from public.devices where device_id = '10000000-0000-4000-8000-000000000002'), 0::bigint);
delete from public.devices where device_id::text like '20000000-%';

-- ---- Isolation (user O) ----

select pg_temp.act_as('00000000-0000-4000-8000-000000000012');
-- The same install id under another account is a separate row; U's is untouched.
select public.check_in_device('10000000-0000-4000-8000-000000000001', 6, 8);
select pg_temp.expect('separate rows per account',
    (select count(*) from public.devices where device_id = '10000000-0000-4000-8000-000000000001'), 2::bigint);
select pg_temp.expect('U''s row untouched',
    (select version_code from public.devices where user_id = '00000000-0000-4000-8000-000000000011'
     and device_id = '10000000-0000-4000-8000-000000000001'), 8);

set local role authenticated;
select pg_temp.expect('authenticated sees only own devices', (select count(*) from public.devices), 1::bigint);
select pg_temp.expect_error('no direct insert',
    $$insert into public.devices (user_id, device_id, version_code, schema_version) values ('00000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000009', 7, 8)$$, '42501');
select pg_temp.expect_error('no direct update', $$update public.devices set version_code = 99$$, '42501');
select pg_temp.expect_error('no direct delete', $$delete from public.devices$$, '42501');
select pg_temp.expect_error('admins is closed', $$select count(*) from public.admins$$, '42501');
select pg_temp.expect_error('cannot make oneself admin',
    $$insert into public.admins (user_id) values ('00000000-0000-4000-8000-000000000012')$$, '42501');
select pg_temp.expect_error('non-admin stats', $$select public.admin_stats()$$, '42501');
reset role;

set local role anon;
select set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
select pg_temp.expect_error('anon cannot read devices', $$select count(*) from public.devices$$, '42501');
select pg_temp.expect_error('anon cannot check in',
    $$select public.check_in_device('10000000-0000-4000-8000-000000000001', 7, 8)$$, '42501');
select pg_temp.expect_error('anon cannot read stats', $$select public.admin_stats()$$, '42501');
reset role;

-- ---- Usage overview (admin M) ----

-- U: two finished workouts this week and one last week, one unfinished, one deleted, and one
-- with a hostile startedAt that must be skipped rather than break the overview.
insert into public.sync_rows (user_id, table_name, sync_id, payload, updated_at, deleted_at) values
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w1',
        jsonb_build_object('startedAt', (extract(epoch from date_trunc('week', now())) * 1000)::bigint + 3600000, 'endedAt', 1, 'notes', 'secret'), 1, null),
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w2',
        jsonb_build_object('startedAt', (extract(epoch from date_trunc('week', now())) * 1000)::bigint + 7200000, 'endedAt', 1), 1, null),
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w3',
        jsonb_build_object('startedAt', (extract(epoch from date_trunc('week', now()) - interval '4 days') * 1000)::bigint, 'endedAt', 1), 1, null),
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w4',
        jsonb_build_object('startedAt', (extract(epoch from now()) * 1000)::bigint, 'endedAt', null), 1, null),
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w5', null, 1, 1),
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w6',
        jsonb_build_object('startedAt', 'soon', 'endedAt', 1e300), 1, null),
    ('00000000-0000-4000-8000-000000000011', 'workout_session', 'w7',
        jsonb_build_object('startedAt', 1e300, 'endedAt', 1), 1, null);

insert into public.admins (user_id) values ('00000000-0000-4000-8000-000000000013');
select pg_temp.act_as('00000000-0000-4000-8000-000000000013');

select pg_temp.expect_error('weeks zero', $$select public.admin_stats(0)$$, '22023');
select pg_temp.expect_error('weeks too many', $$select public.admin_stats(105)$$, '22023');

create temp table stats as select public.admin_stats(4) as s;

select pg_temp.expect('no workout contents', (select position('secret' in s::text) from stats), 0);
select pg_temp.expect('four weeks', (select jsonb_array_length(s->'by_week') from stats), 4);
select pg_temp.expect('accounts', (select (s->'totals'->>'accounts')::int >= 3 from stats), true);
select pg_temp.expect('workouts this week',
    (select (s->'by_week'->3->>'workouts')::int from stats), 2);
select pg_temp.expect('workouts last week',
    (select (s->'by_week'->2->>'workouts')::int from stats), 1);
select pg_temp.expect('active users this week',
    (select (s->'by_week'->3->>'active_users')::int from stats), 1);
select pg_temp.expect('sign-ups this week include O and M',
    (select (s->'by_week'->3->>'signups')::int >= 2 from stats), true);
select pg_temp.expect('U in the list',
    (select (u->>'workouts')::int from stats, jsonb_array_elements(s->'users') u where u->>'email' = 'u@example.test'), 3);
select pg_temp.expect('U''s latest version',
    (select (u->>'version_code')::int from stats, jsonb_array_elements(s->'users') u where u->>'email' = 'u@example.test'), 8);
select pg_temp.expect('U is active',
    (select u->>'last_active_at' is not null from stats, jsonb_array_elements(s->'users') u where u->>'email' = 'u@example.test'), true);
select pg_temp.expect('M never synced',
    (select u->>'last_active_at' is null from stats, jsonb_array_elements(s->'users') u where u->>'email' = 'm@example.test'), true);
select pg_temp.expect('version 8 on one install',
    (select (v->>'devices')::int from stats, jsonb_array_elements(s->'versions') v where (v->>'version_code')::int = 8), 1);
select pg_temp.expect('version 6 on one install',
    (select (v->>'devices')::int from stats, jsonb_array_elements(s->'versions') v where (v->>'version_code')::int = 6), 1);

-- ---- Account deletion (user U) ----

select pg_temp.act_as('00000000-0000-4000-8000-000000000011');
select public.delete_my_account();
select pg_temp.expect('U''s devices are gone',
    (select count(*) from public.devices where user_id = '00000000-0000-4000-8000-000000000011'), 0::bigint);
select pg_temp.expect('O''s device remains',
    (select count(*) from public.devices where user_id = '00000000-0000-4000-8000-000000000012'), 1::bigint);

select 'devices: all checks passed' as result;

rollback;
