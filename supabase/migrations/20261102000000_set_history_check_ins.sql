-- Each set's workout flags, so the website's progress pages can leave lighter sessions and
-- rough days out of trends, as the app does (core/model CheckIns.kt). Only adds columns at the
-- end of v_set_history (create or replace view requires that), so nothing that reads it changes.

create or replace view public.v_set_history with (security_invoker = true) as
select
    st.user_id,
    st.sync_id,
    st.session_exercise_id,
    e.session_id,
    e.exercise_id,
    s.started_at          as session_started_at,
    s.ended_at is not null as session_finished,
    st.position,
    st.weight_kg,
    st.reps,
    st.rir,
    st.rpe,
    st.set_type,
    st.completed_at,
    st.notes,
    coalesce(s.is_deload, false) as session_is_deload,
    s.readiness            as session_readiness,
    s.feel                 as session_feel
from public.v_set_entry st
join public.v_session_exercise e on e.user_id = st.user_id and e.sync_id = st.session_exercise_id
join public.v_session s on s.user_id = e.user_id and s.sync_id = e.session_id;
