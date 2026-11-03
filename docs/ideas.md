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
- **Log cardio in the app**: a cardio entry (running, cycling, rowing, walking, …) with
  duration, distance and optionally average heart rate or calories, logged on its own or as
  part of a workout. Shows in history and on the website, and syncs like the rest (needs a
  Room migration and a Supabase migration, expand then contract).
- **Cardio in the workout rotation**: let a plan include cardio days or a cardio block at the
  end of a lifting day, so the rotation suggests them alongside the lifting days.
