-- Checks for push_rows, delete_my_account and the sync_rows grants after every migration in
-- supabase/migrations/ has been applied. Everything runs in one transaction that is rolled
-- back, so the database is left as it was. CI does not run this file.
--
-- Run against a local stack or a throwaway project, never production:
--   supabase start
--   docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < supabase/tests/push_rows.sql
-- or paste the whole file into the SQL editor of a non-production project. A failing check
-- raises an exception naming the case; success prints "push_rows: all checks passed".
--
-- auth.uid() reads request.jwt.claims, so each case sets the claims it needs; the functions
-- are SECURITY DEFINER, so the role only matters for the direct reads at the end.

begin;

-- Two throwaway users (auth.users columns beyond id are nullable or defaulted).
insert into auth.users (id) values
    ('00000000-0000-4000-8000-00000000000a'),
    ('00000000-0000-4000-8000-00000000000b');

-- Runs [sql] and checks that it fails with [state]; raises if it succeeds or fails otherwise.
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
select pg_temp.expect_error('signed out push', $$select public.push_rows('[]'::jsonb)$$, '42501');
select pg_temp.expect_error('signed out delete', $$select public.delete_my_account()$$, '42501');

-- ---- Shape checks (user A) ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000a');

select pg_temp.expect_error('not an array', $$select public.push_rows('{"a":1}'::jsonb)$$, '22023');
select pg_temp.expect_error('row not an object', $$select public.push_rows('[1]'::jsonb)$$, '22023');
select pg_temp.expect_error('unknown table',
    $$select public.push_rows('[{"table_name":"users","sync_id":"x","payload":{},"updated_at":1000,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('missing sync_id',
    $$select public.push_rows('[{"table_name":"plan","payload":{},"updated_at":1000,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('payload is a string',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":"x","updated_at":1000,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('payload is an array',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":[1],"updated_at":1000,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('live row without payload',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":null,"updated_at":1000,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('updated_at missing',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":{},"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('updated_at is a string',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":{},"updated_at":"1000","deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('updated_at fractional',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":{},"updated_at":1000.5,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('updated_at negative',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":{},"updated_at":-1,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('updated_at two days ahead',
    format($$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":{},"updated_at":%s,"deleted_at":null}]'::jsonb)$$,
           (extract(epoch from clock_timestamp()) * 1000)::bigint + 2 * 86400000), '22023');
select pg_temp.expect_error('deleted_at is a string',
    $$select public.push_rows('[{"table_name":"plan","sync_id":"p1","payload":null,"updated_at":1000,"deleted_at":"1000"}]'::jsonb)$$, '22023');
select pg_temp.expect_error('same row twice in a batch',
    $$select public.push_rows('[
        {"table_name":"plan","sync_id":"p1","payload":{"name":"a"},"updated_at":1000,"deleted_at":null},
        {"table_name":"plan","sync_id":"p1","payload":{"name":"b"},"updated_at":1001,"deleted_at":null}]'::jsonb)$$, '22023');
select pg_temp.expect_error('too many rows',
    $$select public.push_rows((select jsonb_agg(jsonb_build_object('table_name','plan','sync_id','p'||i,'payload','{}'::jsonb,'updated_at',1000,'deleted_at',null)) from generate_series(1, 1001) i))$$, '22023');
select pg_temp.expect_error('oversized payload',
    $$select public.push_rows(jsonb_build_array(jsonb_build_object('table_name','plan','sync_id','p1','payload',jsonb_build_object('notes', repeat('x', 17000)),'updated_at',1000,'deleted_at',null)))$$, '22023');

-- ---- Last-write-wins (user A) ----

select pg_temp.expect('first write', public.push_rows('[
    {"table_name":"plan","sync_id":"p1","payload":{"name":"Upper"},"updated_at":1000,"deleted_at":null},
    {"table_name":"plan_day","sync_id":"d1","payload":{"planId":"p1","position":0,"name":"A"},"updated_at":1000,"deleted_at":null}]'::jsonb), 2);
select pg_temp.expect('unchanged row is a no-op', public.push_rows('[
    {"table_name":"plan","sync_id":"p1","payload":{"name":"Upper"},"updated_at":1000,"deleted_at":null}]'::jsonb), 0);
select pg_temp.expect('older row is skipped', public.push_rows('[
    {"table_name":"plan","sync_id":"p1","payload":{"name":"Older"},"updated_at":999,"deleted_at":null}]'::jsonb), 0);
select pg_temp.expect('newer row wins', public.push_rows('[
    {"table_name":"plan","sync_id":"p1","payload":{"name":"Newer"},"updated_at":1001,"deleted_at":null}]'::jsonb), 1);
select pg_temp.expect('stored payload', (select payload->>'name' from public.sync_rows where table_name = 'plan' and sync_id = 'p1'), 'Newer');
select pg_temp.expect('tombstone without payload is accepted', public.push_rows('[
    {"table_name":"plan_day","sync_id":"d1","payload":null,"updated_at":1002,"deleted_at":1002}]'::jsonb), 1);
select pg_temp.expect('tombstone stored', (select deleted_at from public.sync_rows where table_name = 'plan_day' and sync_id = 'd1'), 1002::bigint);
select pg_temp.expect('rows belong to the caller', (select count(*) from public.sync_rows where user_id = '00000000-0000-4000-8000-00000000000a'), 2::bigint);

-- ---- Isolation (user B) ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000b');
select pg_temp.expect('B writes own row', public.push_rows('[
    {"table_name":"plan","sync_id":"p1","payload":{"name":"Mine"},"updated_at":5000,"deleted_at":null}]'::jsonb), 1);
select pg_temp.expect('same sync id, separate rows per user', (select count(*) from public.sync_rows where table_name = 'plan' and sync_id = 'p1'), 2::bigint);
select pg_temp.expect('A''s row untouched', (select payload->>'name' from public.sync_rows where user_id = '00000000-0000-4000-8000-00000000000a' and table_name = 'plan' and sync_id = 'p1'), 'Newer');

-- RLS and grants as the API roles see them.
set local role authenticated;
select pg_temp.expect('authenticated sees only own rows', (select count(*) from public.sync_rows), 1::bigint);
select pg_temp.expect('views follow RLS', (select count(*) from public.v_plan), 1::bigint);
select pg_temp.expect_error('authenticated cannot insert directly',
    $$insert into public.sync_rows (user_id, table_name, sync_id, payload, updated_at) values ('00000000-0000-4000-8000-00000000000b', 'plan', 'p9', '{}', 1)$$, '42501');
select pg_temp.expect_error('authenticated cannot delete directly', $$delete from public.sync_rows$$, '42501');
reset role;

set local role anon;
select set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
select pg_temp.expect_error('anon cannot read sync_rows', $$select count(*) from public.sync_rows$$, '42501');
select pg_temp.expect_error('anon cannot read views', $$select count(*) from public.v_plan$$, '42501');
select pg_temp.expect_error('anon cannot call push_rows', $$select public.push_rows('[]'::jsonb)$$, '42501');
reset role;

-- ---- Account deletion (user A) ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000a');
select public.delete_my_account();
select pg_temp.expect('A''s rows are gone', (select count(*) from public.sync_rows where user_id = '00000000-0000-4000-8000-00000000000a'), 0::bigint);
select pg_temp.expect('A''s user is gone', (select count(*) from auth.users where id = '00000000-0000-4000-8000-00000000000a'), 0::bigint);
select pg_temp.expect('B''s rows remain', (select count(*) from public.sync_rows where user_id = '00000000-0000-4000-8000-00000000000b'), 1::bigint);

-- The 500 000-row cap (SQLSTATE 54000) isn't exercised here: filling an account to the cap
-- takes minutes. To check it by hand, lower row_cap in a scratch copy of push_rows.

select 'push_rows: all checks passed' as result;

rollback;
