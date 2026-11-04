-- Levels and badges, the rest (core/engine Gamification.kt, web/src/lib/gamification.ts).
-- Expand only: one new column at the end of v_profile and one new view; nothing that older
-- app versions or the website read changes.
--
-- v_profile.show_levels: the "Levels and badges" switch, synced in the profile payload since
-- app schema 11. The app writes booleans as 1/0; a profile from an older app has no field and
-- shows levels, as it always did.
--
-- v_lift_best: each exercise's best estimated 1RM (Epley, a single is its own) over finished
-- workouts' completed working sets, for lift mastery. From the sets, not the records: a first
-- workout sets no record. Holds aren't lifts in the catalog's mastery lists, so no filter here.

create or replace view public.v_profile with (security_invoker = true) as
select
    user_id,
    sync_id,
    payload->>'sex'                              as sex,
    (payload->>'heightCm')::double precision     as height_cm,
    (payload->>'birthDate')::integer             as birth_date_epoch_day,
    (payload->>'bodyWeightKg')::double precision as body_weight_kg,
    payload->>'units'                            as units,
    payload->>'experience'                       as experience,
    payload->>'goal'                             as goal,
    (payload->>'daysPerWeek')::integer           as days_per_week,
    (payload->>'sessionLengthMinutes')::integer  as session_length_minutes,
    payload->>'equipment'                        as equipment,
    payload->>'focusAreas'                       as focus_areas,
    payload->>'limitations'                      as limitations,
    updated_at,
    coalesce(payload->>'showLevels' not in ('0', 'false'), true) as show_levels
from public.sync_rows
where table_name = 'user_profile' and deleted_at is null;

create view public.v_lift_best with (security_invoker = true) as
select
    e.user_id,
    e.exercise_id,
    max(case when st.reps <= 1 then st.weight_kg else st.weight_kg * (1 + st.reps / 30.0) end) as best_e1rm_kg
from public.v_session_exercise e
join public.v_set_entry st on st.user_id = e.user_id and st.session_exercise_id = e.sync_id
join public.v_session s on s.user_id = e.user_id and s.sync_id = e.session_id
where s.ended_at is not null
  and st.completed_at is not null and st.set_type <> 'WARMUP'
  and st.reps > 0 and st.weight_kg is not null
group by e.user_id, e.exercise_id;

grant select on public.v_profile, public.v_lift_best to authenticated;
revoke all on public.v_profile, public.v_lift_best from public, anon;
