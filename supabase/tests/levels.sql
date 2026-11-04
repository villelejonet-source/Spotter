-- Checks v_profile.show_levels and v_lift_best (levels and badges, web/src/lib/gamification.ts)
-- after every migration in supabase/migrations/ has been applied. Like the other tests it runs
-- in one transaction that is rolled back; CI runs it (the Database job). Run it against a local
-- stack, never production:
--   docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < supabase/tests/levels.sql
-- A failing check raises an exception naming the case; success prints "levels: all checks passed".

begin;

-- U turned levels off in the app (1/0), W on the website (true/false), O's profile is from an
-- app older than the switch; O also has a squat U must not see.
insert into auth.users (id, email, created_at) values
    ('00000000-0000-4000-8000-000000000031', 'u@example.test', now()),
    ('00000000-0000-4000-8000-000000000032', 'w@example.test', now()),
    ('00000000-0000-4000-8000-000000000033', 'o@example.test', now());

create function pg_temp.expect(label text, actual anyelement, expected anyelement) returns void
language plpgsql as $$
begin
    if actual is distinct from expected then
        raise exception '% : expected %, got %', label, expected, actual;
    end if;
end;
$$;

insert into public.sync_rows (user_id, table_name, sync_id, payload, updated_at) values
    ('00000000-0000-4000-8000-000000000031', 'user_profile', 'user-profile', jsonb_build_object('sex', 'MALE', 'showLevels', 0), 1),
    ('00000000-0000-4000-8000-000000000032', 'user_profile', 'user-profile', jsonb_build_object('sex', 'FEMALE', 'showLevels', false), 1),
    ('00000000-0000-4000-8000-000000000033', 'user_profile', 'user-profile', jsonb_build_object('sex', 'MALE'), 1),
    -- U's finished workout: a squat warm-up, 100 × 5, a 110 single and a set not done.
    ('00000000-0000-4000-8000-000000000031', 'workout_session', 's1',
        jsonb_build_object('startedAt', 1798300800000, 'endedAt', 1798304400000), 1),
    ('00000000-0000-4000-8000-000000000031', 'session_exercise', 'e1',
        jsonb_build_object('sessionId', 's1', 'exerciseId', 'back-squat', 'position', 0), 1),
    ('00000000-0000-4000-8000-000000000031', 'set_entry', 't1',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 0, 'weightKg', 140, 'reps', 3, 'setType', 'WARMUP', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000031', 'set_entry', 't2',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 1, 'weightKg', 100, 'reps', 5, 'setType', 'WORKING', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000031', 'set_entry', 't3',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 2, 'weightKg', 110, 'reps', 1, 'setType', 'WORKING', 'completedAt', 1), 1),
    ('00000000-0000-4000-8000-000000000031', 'set_entry', 't4',
        jsonb_build_object('sessionExerciseId', 'e1', 'position', 3, 'weightKg', 150, 'reps', 5, 'setType', 'WORKING'), 1),
    -- U's running workout: a heavy bench that doesn't count yet.
    ('00000000-0000-4000-8000-000000000031', 'workout_session', 's2', jsonb_build_object('startedAt', 1798390800000), 1),
    ('00000000-0000-4000-8000-000000000031', 'session_exercise', 'e2',
        jsonb_build_object('sessionId', 's2', 'exerciseId', 'barbell-bench-press', 'position', 0), 1),
    ('00000000-0000-4000-8000-000000000031', 'set_entry', 't5',
        jsonb_build_object('sessionExerciseId', 'e2', 'position', 0, 'weightKg', 100, 'reps', 5, 'setType', 'WORKING', 'completedAt', 1), 1),
    -- O's squat.
    ('00000000-0000-4000-8000-000000000033', 'workout_session', 'o1',
        jsonb_build_object('startedAt', 1798300800000, 'endedAt', 1798304400000), 1),
    ('00000000-0000-4000-8000-000000000033', 'session_exercise', 'oe1',
        jsonb_build_object('sessionId', 'o1', 'exerciseId', 'back-squat', 'position', 0), 1),
    ('00000000-0000-4000-8000-000000000033', 'set_entry', 'ot1',
        jsonb_build_object('sessionExerciseId', 'oe1', 'position', 0, 'weightKg', 200, 'reps', 5, 'setType', 'WORKING', 'completedAt', 1), 1);

set local role authenticated;

select set_config('request.jwt.claims', json_build_object('sub', '00000000-0000-4000-8000-000000000031', 'role', 'authenticated')::text, true);
select pg_temp.expect('the app writes 0 for off', (select show_levels from public.v_profile), false);
select pg_temp.expect('only own lifts, finished workouts only', (select array_agg(exercise_id) from public.v_lift_best), array['back-squat']);
select pg_temp.expect('best estimated 1RM leaves warm-ups and unfinished sets out',
    (select round(best_e1rm_kg::numeric, 2) from public.v_lift_best where exercise_id = 'back-squat'), 116.67);

select set_config('request.jwt.claims', json_build_object('sub', '00000000-0000-4000-8000-000000000032', 'role', 'authenticated')::text, true);
select pg_temp.expect('the website writes false for off', (select show_levels from public.v_profile), false);

select set_config('request.jwt.claims', json_build_object('sub', '00000000-0000-4000-8000-000000000033', 'role', 'authenticated')::text, true);
select pg_temp.expect('a profile from before the switch shows levels', (select show_levels from public.v_profile), true);
select pg_temp.expect('O sees its own squat', (select round(best_e1rm_kg::numeric, 2) from public.v_lift_best), 233.33);

reset role;

select 'levels: all checks passed' as result;

rollback;
