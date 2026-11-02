-- Checks v_session_play (levels and badges, web/src/lib/gamification.ts) after every migration
-- in supabase/migrations/ has been applied. Like the other tests it runs in one transaction that
-- is rolled back; CI runs it (the Database job). Run it against a local stack, never production:
--   docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < supabase/tests/session_play.sql
-- A failing check raises an exception naming the case; success prints
-- "session_play: all checks passed".

begin;

-- U has one finished workout and one still running; O has a workout U must not see.
insert into auth.users (id, email, created_at) values
    ('00000000-0000-4000-8000-000000000021', 'u@example.test', now()),
    ('00000000-0000-4000-8000-000000000022', 'o@example.test', now());

create function pg_temp.expect(label text, actual anyelement, expected anyelement) returns void
language plpgsql as $$
begin
    if actual is distinct from expected then
        raise exception '% : expected %, got %', label, expected, actual;
    end if;
end;
$$;

insert into public.sync_rows (user_id, table_name, sync_id, payload, updated_at) values
    -- U's finished workout: bench (a warm-up, two working sets, one not done) and a plank hold.
    ('00000000-0000-4000-8000-000000000021', 'workout_session', 's1',
        jsonb_build_object('startedAt', 1798300800000, 'endedAt', 1798304400000, 'isDeload', 1, 'readiness', 2), 1),
    ('00000000-0000-4000-8000-000000000021', 'session_exercise', 'e1',
        jsonb_build_object('sessionId', 's1', 'exerciseId', 'barbell-bench-press', 'position', 0), 1),
    ('00000000-0000-4000-8000-000000000021', 'session_exercise', 'e2',
        jsonb_build_object('sessionId', 's1', 'exerciseId', 'plank', 'position', 1), 1),
    ('00000000-0000-4000-8000-000000000021', 'set_entry', 't1',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 0, 'weightKg', 40, 'reps', 10, 'setType', 'WARMUP', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000021', 'set_entry', 't2',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 1, 'weightKg', 100, 'reps', 5, 'setType', 'WORKING', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000021', 'set_entry', 't3',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 2, 'weightKg', 100, 'reps', 5, 'setType', 'WORKING', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000021', 'set_entry', 't4',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 3, 'weightKg', 100, 'reps', 5, 'setType', 'WORKING'), 1),
    ('00000000-0000-4000-8000-000000000021', 'set_entry', 't5',
        jsonb_build_object('sessionExerciseId', 'e2', 'position', 0, 'weightKg', 10, 'reps', 45, 'setType', 'WORKING', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000021', 'personal_record', 'r1',
        jsonb_build_object('exerciseId', 'barbell-bench-press', 'type', 'ESTIMATED_1RM', 'value', 116.7, 'achievedAt', 1, 'sessionId', 's1'), 1),
    ('00000000-0000-4000-8000-000000000021', 'personal_record', 'r2',
        jsonb_build_object('exerciseId', 'barbell-bench-press', 'type', 'VOLUME', 'value', 1000, 'achievedAt', 1, 'sessionId', 's1'), 1),
    -- U's running workout: not counted yet.
    ('00000000-0000-4000-8000-000000000021', 'workout_session', 's2',
        jsonb_build_object('startedAt', 1798390800000), 1),
    -- O's workout.
    ('00000000-0000-4000-8000-000000000022', 'workout_session', 'o1',
        jsonb_build_object('startedAt', 1798300800000, 'endedAt', 1798304400000), 1);

set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', '00000000-0000-4000-8000-000000000021', 'role', 'authenticated')::text, true);

select pg_temp.expect('only own finished workouts', (select array_agg(sync_id) from public.v_session_play), array['s1']);
select pg_temp.expect('working sets leave the warm-up and the unfinished set out',
    (select working_sets from public.v_session_play where sync_id = 's1'), 3);
select pg_temp.expect('volume leaves warm-ups and holds out',
    (select volume_kg from public.v_session_play where sync_id = 's1'), 1000::double precision);
select pg_temp.expect('exercises with a working set',
    (select exercise_ids from public.v_session_play where sync_id = 's1'), array['barbell-bench-press', 'plank']);
select pg_temp.expect('one entry per record',
    (select array(select unnest(record_types) order by 1) from public.v_session_play where sync_id = 's1'), array['ESTIMATED_1RM', 'VOLUME']);
select pg_temp.expect('lighter session comes through', (select is_deload from public.v_session_play where sync_id = 's1'), true);
select pg_temp.expect('check-in comes through', (select readiness from public.v_session_play where sync_id = 's1'), 2);
select pg_temp.expect('no rating', (select feel from public.v_session_play where sync_id = 's1'), null::integer);

reset role;

select 'session_play: all checks passed' as result;

rollback;
