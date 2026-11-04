# Ideas and follow-ups

A backlog of things worth doing later: open checks first, then ideas. Not a plan or a promise.
Add a line when an idea comes up; when one is picked, build it with `/feature` (or `/bugfix`)
and delete it here in the same pull request.

## To check by hand

- **Levels and badges on a phone** (#77): update over an install that already has workouts
  (Today's "New: levels and badges" note, a level that matches the history), finish a workout
  with a PR (XP card, bar, new badges, +25 XP after rating it), a level-up (confetti once),
  the Settings switch hiding it all, and the same level on a second phone after sync.
- **Levels and badges on the website** (#78): Today's level card and Progress → Levels show
  the same level, XP and badges as the app; the badge grid stacks at phone width.

## Ideas

### Levels and badges
- **Weekly quests**: two or three small goals that change every Monday ("3 workouts", "try a
  new exercise", "check in every time"), with bonus XP. Worked out from history like the rest.
- **Lift mastery**: bronze, silver and gold per lift, from the estimated 1RM relative to body
  weight (needs body weight logged; per sex and lift).
- **Shareable level-up card**: an image of the new level or badge to share from the summary.
- **Year in review** ("Spotter Wrapped"): workouts, volume, PRs, badges and the strongest
  month, shown in December.
- **Sync the "Levels and badges" switch** so the website hides them too. Today it's stored only
  on the phone; syncing it needs a `user_profile` column (expand, then contract).

### Cardio
- **Log cardio in the app**: running, cycling, rowing, walking and so on, on its own or as part
  of a workout, with duration, distance and optionally average heart rate or calories. Shows
  in history and on the website, and syncs like the rest (a Room and a Supabase migration,
  expand then contract).
- **Cardio in the workout rotation**: a plan can include cardio days or a cardio block at the
  end of a lifting day, and the rotation suggests them alongside the lifting days.

### Sport focus
- **Train for your sport**: pick a sport (kickboxing or another martial art, football,
  sprinting) and part of the plan adapts to it.
  - Exercises that help it: for kickboxing, rotational core (landmine twists, cable woodchops),
    hip and leg power (jump squats, kettlebell swings), neck, grip and single-leg work; for
    sprinting, hip extension (hip thrusts, Nordic curls), plyometrics, calves and ankles.
  - Sets and reps to match: fewer, explosive reps for power sports, more reps for
    conditioning-heavy ones, less leg volume close to sport sessions.
  - Optional season mode: more volume off-season, lighter maintenance in season or before a
    fight or meet.
- **Weekly schedule**: ask what the user does each day, so the app knows the whole week, not
  just the gym (Mon kickboxing, Tue gym, Wed kickboxing, Thu rest, Fri gym, Sat kickboxing,
  Sun gym).
  - A day can be gym, one or more sports, rest, or gym plus sport.
  - The plan fits gym days around it: no heavy legs the day before or after a kickboxing or
    sprint day, recovery placed sensibly, rest days kept free.
  - Sport sessions count towards the week's load, so a hard week can mean lighter gym
    sessions or a suggested deload.
  - Asked in onboarding (skippable), editable in Settings; when the week changes (a missed
    session, an extra match) the next gym day adjusts. Optionally log a sport session in a
    few taps (type, duration, how hard it felt).

### Feedback
- **Send feedback from the app**: one place in Settings (and on the website), with a type:
  missing exercise (name, optional equipment and muscles); wrong exercise info (a "Report a
  problem" button on each exercise, which names it: wrong instructions, muscles, equipment
  or other); feature request; or bug (app version and phone model attached). Works offline,
  says "Thanks, we got it", and lands somewhere the owner can review it (a Supabase table or
  a dashboard page).
- **Ask after 5 days of use**: once a user has used the app on 5 different days (not 5
  calendar days since installing), ask "How's Spotter working for you?" with a 1–5 rating
  and an optional "Anything missing or wrong?" box. Shown at a calm moment (after a workout
  or on Today, never mid-set), asked once, and if dismissed it can come back once a few days
  later. The count is stored with the account, so a new phone or reinstall doesn't ask again.
