-- Checks for profiles, trainer_links, their functions, can_read_user and delete_my_account
-- after every migration in supabase/migrations/ has been applied. Like push_rows.sql it runs
-- in one transaction that is rolled back, and CI does not run it. Run it the same way,
-- against a local stack or a throwaway project, never production:
--   docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < supabase/tests/trainer_links.sql
-- (docs/platform/infrastructure.md, "Database migrations", has the full local steps).
-- A failing check raises an exception naming the case; success prints
-- "trainer_links: all checks passed".

begin;

-- T trains C; S is a stranger to both.
insert into auth.users (id) values
    ('00000000-0000-4000-8000-00000000000c'),
    ('00000000-0000-4000-8000-00000000000d'),
    ('00000000-0000-4000-8000-00000000000e');

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

-- Ids and codes carried between cases.
create temp table ids (name text primary key, value text);
grant all on ids to authenticated;

-- ---- Signed out ----

select set_config('request.jwt.claims', '', true);
select pg_temp.expect_error('signed out invite', $$select public.create_trainer_invite()$$, '42501');
select pg_temp.expect_error('signed out accept', $$select public.accept_trainer_invite('x')$$, '42501');
select pg_temp.expect_error('signed out end', $$select public.end_trainer_link(gen_random_uuid())$$, '42501');
select pg_temp.expect('signed out can read no one', public.can_read_user('00000000-0000-4000-8000-00000000000c') is true, false);

-- ---- Profiles, as the API role ----

set local role authenticated;

select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
insert into public.profiles (display_name) values ('Coach T');
select pg_temp.expect('own profile is readable', (select display_name from public.profiles), 'Coach T');
update public.profiles set display_name = 'Coach Tee';
select pg_temp.expect('own profile is writable', (select display_name from public.profiles), 'Coach Tee');
select pg_temp.expect_error('user_id is not writable', $$update public.profiles set user_id = '00000000-0000-4000-8000-00000000000e'$$, '42501');
select pg_temp.expect_error('empty name rejected', $$update public.profiles set display_name = ''$$, '23514');

select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
select pg_temp.expect('stranger sees no profiles', (select count(*) from public.profiles), 0::bigint);
update public.profiles set display_name = 'Hacked' where user_id = '00000000-0000-4000-8000-00000000000c';
select pg_temp.expect_error('stranger cannot create a profile for someone else',
    $$insert into public.profiles (user_id, display_name) values ('00000000-0000-4000-8000-00000000000c', 'X')$$, '42501');
select pg_temp.expect_error('stranger cannot delete profiles', $$delete from public.profiles$$, '42501');

select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
select pg_temp.expect('stranger''s update changed nothing', (select display_name from public.profiles), 'Coach Tee');

reset role;
select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
insert into public.profiles (user_id, display_name) values ('00000000-0000-4000-8000-00000000000d', 'Client C');

set local role anon;
select set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
select pg_temp.expect_error('anon cannot read profiles', $$select count(*) from public.profiles$$, '42501');
select pg_temp.expect_error('anon cannot read links', $$select count(*) from public.trainer_links$$, '42501');
select pg_temp.expect_error('anon cannot create invites', $$select public.create_trainer_invite()$$, '42501');
reset role;

-- ---- Invites ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
insert into ids select 'link1', id::text from public.create_trainer_invite('Client C');
select pg_temp.expect('invite keeps its label', (select label from public.trainer_links where id = (select value from ids where name = 'link1')::uuid), 'Client C');
select pg_temp.expect_error('label too long', format('select public.create_trainer_invite(%L)', repeat('x', 61)), '23514');
select pg_temp.expect_error('empty label', $$select public.create_trainer_invite('')$$, '23514');
insert into ids select 'code1', invite_code from public.trainer_links where id = (select value from ids where name = 'link1')::uuid;
select pg_temp.expect('invite is pending', (select status from public.trainer_links where id = (select value from ids where name = 'link1')::uuid), 'pending');
select pg_temp.expect('invite code is 32 hex characters', (select value ~ '^[0-9a-f]{32}$' from ids where name = 'code1'), true);
select pg_temp.expect_error('trainer cannot accept their own invite',
    format('select public.accept_trainer_invite(%L)', (select value from ids where name = 'code1')), 'P0002');
select pg_temp.expect_error('unknown code', $$select public.accept_trainer_invite('00000000000000000000000000000000')$$, 'P0002');

select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select pg_temp.expect('client accepts',
    (select status from public.accept_trainer_invite((select value from ids where name = 'code1'))), 'active');
select pg_temp.expect('client is the caller',
    (select client_id from public.trainer_links where id = (select value from ids where name = 'link1')::uuid), '00000000-0000-4000-8000-00000000000d'::uuid);
select pg_temp.expect('label is cleared on accept',
    (select label from public.trainer_links where id = (select value from ids where name = 'link1')::uuid), null::text);
select pg_temp.expect_error('a used code cannot be accepted again',
    format('select public.accept_trainer_invite(%L)', (select value from ids where name = 'code1')), 'P0002');

-- An invite older than 14 days has expired.
select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
insert into ids select 'link_old', id::text from public.create_trainer_invite();
update public.trainer_links set created_at = now() - interval '15 days' where id = (select value from ids where name = 'link_old')::uuid;
select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
select pg_temp.expect_error('expired code',
    format('select public.accept_trainer_invite(%L)', (select invite_code from public.trainer_links where id = (select value from ids where name = 'link_old')::uuid)), 'P0002');

-- ---- Who sees and changes links, as the API role ----

set local role authenticated;

select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
select pg_temp.expect('stranger reads no links', (select count(*) from public.trainer_links), 0::bigint);
select pg_temp.expect_error('stranger cannot end someone else''s link',
    format('select public.end_trainer_link(%L)', (select value from ids where name = 'link1')), 'P0002');
select pg_temp.expect_error('stranger cannot accept a used code',
    format('select public.accept_trainer_invite(%L)', (select value from ids where name = 'code1')), 'P0002');
select pg_temp.expect_error('no direct insert',
    $$insert into public.trainer_links (trainer_id, client_id, status, accepted_at) values ('00000000-0000-4000-8000-00000000000e', '00000000-0000-4000-8000-00000000000d', 'active', now())$$, '42501');

select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
select pg_temp.expect('trainer reads their links', (select count(*) from public.trainer_links), 2::bigint);
select pg_temp.expect_error('no direct update', $$update public.trainer_links set status = 'ended'$$, '42501');
select pg_temp.expect_error('no direct delete', $$delete from public.trainer_links$$, '42501');

select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select pg_temp.expect('client reads their link', (select count(*) from public.trainer_links), 1::bigint);

-- ---- Reading synced rows stays owner-only, even with an active link ----

reset role;
select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
select public.push_rows('[{"table_name":"plan","sync_id":"t1","payload":{"name":"T"},"updated_at":1000,"deleted_at":null}]'::jsonb);
select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select public.push_rows('[{"table_name":"plan","sync_id":"c1","payload":{"name":"C"},"updated_at":1000,"deleted_at":null}]'::jsonb);

set local role authenticated;
select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
select pg_temp.expect('can_read_user: own id', public.can_read_user('00000000-0000-4000-8000-00000000000c'), true);
select pg_temp.expect('can_read_user: not an active client yet', public.can_read_user('00000000-0000-4000-8000-00000000000d'), false);
select pg_temp.expect('trainer pulls only their own rows', (select count(*) from public.sync_rows), 1::bigint);
select pg_temp.expect('trainer''s views show only their own rows', (select count(*) from public.v_plan), 1::bigint);
select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
select pg_temp.expect('can_read_user: stranger', public.can_read_user('00000000-0000-4000-8000-00000000000d'), false);
reset role;

-- ---- Ending and re-linking ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select public.end_trainer_link((select value from ids where name = 'link1')::uuid);
select pg_temp.expect('client ended the link',
    (select status from public.trainer_links where id = (select value from ids where name = 'link1')::uuid), 'ended');
select public.end_trainer_link((select value from ids where name = 'link1')::uuid);
select pg_temp.expect('ending twice is a no-op',
    (select status from public.trainer_links where id = (select value from ids where name = 'link1')::uuid), 'ended');

select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
insert into ids select 'code2', invite_code from public.create_trainer_invite();
insert into ids select 'code3', invite_code from public.create_trainer_invite();
select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select pg_temp.expect('the pair can link again after ending',
    (select status from public.accept_trainer_invite((select value from ids where name = 'code2'))), 'active');
select pg_temp.expect_error('one link per pair',
    format('select public.accept_trainer_invite(%L)', (select value from ids where name = 'code3')), '23505');

-- A client has one trainer at a time: S's invite fails while C is linked to T.
select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
insert into ids select 'link_s', id::text from public.create_trainer_invite();
select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select pg_temp.expect_error('one trainer at a time',
    format('select public.accept_trainer_invite(%L)', (select invite_code from public.trainer_links where id = (select value from ids where name = 'link_s')::uuid)), '23505');
select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
select public.end_trainer_link((select value from ids where name = 'link_s')::uuid);

-- The trainer can withdraw a pending invite.
select pg_temp.act_as('00000000-0000-4000-8000-00000000000c');
select public.end_trainer_link((select id from public.trainer_links where invite_code = (select value from ids where name = 'code3')));
select pg_temp.expect('trainer withdrew the invite',
    (select status from public.trainer_links where invite_code = (select value from ids where name = 'code3')), 'ended');

-- ---- Invite cap ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000e');
select count(*) from (select public.create_trainer_invite() from generate_series(1, 20)) invites;
select pg_temp.expect_error('at most 20 open invites', $$select public.create_trainer_invite()$$, '54000');

-- ---- Account deletion (client C) ----

select pg_temp.act_as('00000000-0000-4000-8000-00000000000d');
select public.delete_my_account();
select pg_temp.expect('C''s links are gone',
    (select count(*) from public.trainer_links where client_id = '00000000-0000-4000-8000-00000000000d'), 0::bigint);
select pg_temp.expect('C''s profile is gone',
    (select count(*) from public.profiles where user_id = '00000000-0000-4000-8000-00000000000d'), 0::bigint);
select pg_temp.expect('C''s rows are gone',
    (select count(*) from public.sync_rows where user_id = '00000000-0000-4000-8000-00000000000d'), 0::bigint);
select pg_temp.expect('T''s profile remains',
    (select count(*) from public.profiles where user_id = '00000000-0000-4000-8000-00000000000c'), 1::bigint);
select pg_temp.expect('T''s rows remain',
    (select count(*) from public.sync_rows where user_id = '00000000-0000-4000-8000-00000000000c'), 1::bigint);

select 'trainer_links: all checks passed' as result;

rollback;
