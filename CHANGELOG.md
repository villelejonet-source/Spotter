# Changelog

What changed for people using Spotter, newest first. Every pull request that changes something a
user or the website would notice adds a line under **Unreleased**; `/release` turns that section
into the new version's entry and writes the store notes from it. Versions are the Android
`versionName` (`versionCode` in brackets); the full build record is the version table in
`docs/play-store/README.md`.

## Unreleased

### Website
- Saving the profile on the website no longer undoes a body weight you just logged on the same
  page (or a change synced from the phone): fields you didn't touch take the newest values.
- The website's data export lists each workout's exercises in the order you did them, keeps
  exercises with no sets logged, includes each exercise's notes, superset, rest and swap, and
  now also exports your planned (scheduled) workouts. The CSV gains an exercise number and keeps
  each exercise's sets together.
- On the website:
  - a starting weight with a decimal comma ("62,5") in the plan editor is kept instead of
    silently cleared;
  - "Plan weeks ahead" no longer puts a workout on a day you've already trained;
  - workouts started just after midnight on the oldest Monday of a chart or the calendar are
    no longer left out;
  - a long week streak shows as "11+" on a Monday before your first workout of the week,
    instead of "11";
  - deleting your latest body weight moves your profile weight back to the entry before it;
  - being sent to the login page, or away from it, no longer risks signing you out.
- New timed holds in the plan editor progress like every other exercise (a few seconds longer
  each time) instead of defaulting to "None". "RIR autoregulation" is now labelled "Double
  progression (RIR)", which is what it does.
- The website fits phones better: editing a plan no longer scrolls sideways (each exercise's
  fields stack under its name), the calendar shows dots in the month and lists the month's
  workouts below it, text fields no longer make iPhones zoom in, buttons, chips and links are
  easier to tap, lists and tables use more of the screen's width, and the bars stay clear of
  the iPhone's home indicator.
- The website switches between pages faster: a page you've just visited opens instantly instead
  of loading again, Today and Insights fetch all their cards at once, every section shows its
  outline the moment you tap it, and scrolling on phones is smoother.
- The website has a new look: dark-first with a glowing orange accent, monospace numbers, a new
  sidebar, and a "Jump to…" command palette (⌘K) to reach any page or action. Light mode
  follows your system setting.
- The website's Today page is a dashboard: stat tiles with trend lines and the change in volume
  against the previous four weeks, a weekly volume chart with a 4W/12W/6M/1Y switch, your next
  workout with this week's progress as a ring, a training heatmap, records and recent workouts.
- The website's charts match: progress lines and body weight glow over a fading area, bar charts
  fade with the latest bar highlighted, and muscle balance shows its target range as a dashed band.

### iOS (not released yet)
- The whole app runs on iOS from the same shared code as Android (#13–#20).
- Fixed a crash on iOS whenever the app logged a warning, such as a failed sync (#27).
- Builds without cloud backup no longer show account buttons that couldn't work (#21).
- The app has its icon on iOS too (it showed a blank placeholder).

## 1.2.1 (10) — 2026-11-01

### Fixed
- Restoring your backup on a new or reinstalled phone no longer leaves out workouts done on a
  plan, or a plan day, that you later deleted on the website. They come back as workouts that
  are no longer linked to that plan.
- With Spotter on two phones, applying a suggestion ("add a set", a new rep range, a variation,
  an extra exercise) now changes the right exercise in your plan on either phone. The two
  phones also stop replacing each other's suggestion cards and no longer show the same card
  twice.
- An exercise set to "None" progression on the website now keeps last session's weight and
  reps, instead of adding weight like double progression. It's no longer flagged as stalled
  either, so it doesn't bring plateau suggestions or a deload.
- Skipping setup on first launch no longer leaves an empty screen without the bottom bar; it
  opens Today.
- A set ticked with 0 reps, or with the reps left blank, no longer counts as a lift: it isn't
  the best set, doesn't give a 1RM or a record, doesn't block later records, and doesn't raise
  the next workout's weight. The website's exercise progress skips it too.
- A change made at the very moment a sync started could be left out of the backup until it was
  edited again. Now it goes with the next sync.
- Weighted holds (a plank with a plate, for example) no longer get estimated 1RM or volume
  records, since their reps are seconds. A longer hold at the same weight is still a record.
- Adding weight to a bodyweight exercise (pull-ups, dips) no longer looks like a drop in
  strength: it doesn't count as a plateau, can't trigger a deload, and the plan review doesn't
  show the lift as weaker.
- Today keeps up with the date when the app stays open overnight: this week's workouts, the
  streak, the heatmap and today's planned workout move on to the new day instead of staying on
  the day the app was opened.
- An exercise added on the website that your app doesn't know yet (the website gets new
  exercises before the app update does) now shows up on the phone once the app is updated,
  instead of never. The first sync after updating fetches the backup again to pick it up.
- Restoring a large backup while another phone or the website saves changes no longer misses
  a record now and then.
- Starting a plan day, or repeating a past workout, while another workout is in progress now
  asks first ("A workout is in progress") with a button to go to it, instead of silently opening
  the running workout.
- New plans give main lifts on linear progression a first weight that fits the reps they ask
  for, instead of one a few percent too heavy.
- Warm-ups and weight rounding never go below the empty bar: 10 kg for an EZ bar, 25 kg for a
  trap bar.
- Weights like 1.005 now round up to 1.01 when shown.
- Logging body weight again for the same day no longer leaves the earlier entry behind in the
  backup.
- Tapping Finish twice no longer records the workout's personal records twice.
- If restoring a backup stops partway (the connection drops), the next sync carries on with the
  restore instead of asking whether to use the backup or merge.
- During a workout:
  - typing an effort outside the scale (RPE 11, or the "1" on the way to typing 10) no longer
    clears the effort already saved;
  - a set can be ticked straight after typing its reps;
  - "copy previous" closes the keypad, so it no longer shows the old typed value;
  - the swap sheet never shows the previous exercise's alternatives;
  - a save that fails while typing shows "Couldn't save" instead of closing the app.
- Quick double taps no longer close two screens (for example Done on the workout summary, or
  Add in the exercise picker, which could also add the exercises twice) or open the same
  workout twice.
- If saving your plan at the end of setup fails, Start works again so you can retry, instead of
  staying greyed out.

## 1.2.0 (9) — 2026-10-31

### Added
- A warm-up panel at the top of every workout: tap it for 5–6 mobility drills picked for what
  you're training (shoulder work on upper-body days, hips and ankles on leg days), and tick them
  off as you go.
- A plan review on Today every four weeks or so when a main lift has stalled (sooner if two
  stall together). It shows how each main lift has moved and suggests exercises for the muscles
  around a stalled one (for a stuck bench: incline press, close-grip bench, shoulder press) or a
  variation to swap in. Pick what you want and apply it, keep your plan, or come back later.
- Delete a finished workout, in the app (History → the workout → the bin icon) or on the website
  (the workout's page → Delete workout). Its sets and any records set in it go too, on every
  device; a scheduled day it completed shows as not done again.

### Changed
- "Add warm-up set" adds one set at a time, stepping up towards your working weight, and stays
  until you finish your first working set, so you can add as many as you like.
- The app takes on the website's look: Spotter's own colours (a near-black dark mode with the
  orange accent, and a matching light mode), the Geist fonts with monospace numbers, outlined
  cards, a glowing main button and a new tab bar. Android's wallpaper colours (Material You) are
  now off by default; turn them on in Profile → Appearance. Anyone who already chose keeps their
  choice.
- The app's Today tab is a dashboard: your next workout with this week's progress as a ring,
  stat tiles with trend lines (this week against your goal, volume over four weeks with the change
  against the four before, week streak, all-time workouts) and a heatmap of training days. Start
  workout now sits at the bottom of the screen, in reach of your thumb.
- Logging a workout is easier on the eyes and thumbs: taller set rows and fields, weights and reps
  in monospace digits, finished sets tinted with a glowing tick, and a short vibration when you
  tick or untick a set. The rest timer shows big digits under a glowing progress line, with larger
  −15 s, +15 s and Skip buttons.
- Progress and History match the new look: charts draw a glowing line over a fading area with
  monospace axis labels, muscle balance shows the target range as a band on each bar (and no longer
  breaks long names like "Shoulders" with large text), records and workout stats use monospace
  numbers, and the History calendar marks today with a ring.
- Profile, onboarding, the plan and the account screens finish the new look: settings grouped in
  outlined cards, choices highlighted in the accent, the next plan day outlined in orange, and
  rounded text fields.

### Fixed
- Switching between the bottom tabs is instant. It used to fade for most of a second, so the
  previous tab showed behind the new one, and tapping quickly between two tabs could leave them
  stuck mid-fade for a moment (most visible on iPhone).

## 1.1.0 (8) — 2026-10-30

### Fixed
- A set could lose a value or an edit: a tick made right after typing could be undone by the
  typed value or undo it, typing a weight could clear reps the screen hadn't caught up with, and
  "copy previous" could be overwritten by a value still waiting to save (#22).
- Typing a lone "." as a weight no longer turns the set into a bodyweight set without saying so;
  reps and effort reject "8,5" instead of clearing the field (#23).
- Exercise history no longer shows volume or a 1RM for weighted holds such as a plank with a
  plate, matching the website and the workout summary (#23).
- Starting weights from onboarding treat a single rep as its own 1RM, as everywhere else (#23).
- The keypad's +2.5 then Done no longer reverts the weight (#19).
- Between midnight and 01:00 (02:00 in summer) the app treated "today" as yesterday: a finished
  deload week still lightened workouts, yesterday's scheduled workout still showed as upcoming,
  and a deload applied then started a day early. "Today" is now the phone's date everywhere (#29).

### Changed
- A save or action that fails now says so ("Couldn't save. Try again.") instead of the app
  closing or nothing happening. A sign-in link that doesn't work says so too (#27).
- Decimal commas work everywhere a number is typed, including the website's body-weight log
  ("82,5") in every browser (#23).

### Also in this release (from 1.0.1, which was never uploaded)
- Backup and sync only ever download your own account's data, ready for coaching features.
- When it syncs, the app reports its version number (see the updated privacy policy).

## 1.0.1 (7)

Never uploaded; its changes shipped in 1.1.0.
- Backup and sync only ever download your own account's data, ready for coaching features.
- When it syncs, the app reports its version number (see the updated privacy policy).

## 1.0.0 (6) — 2026-10-25

- Fixed typing in the exercise search: letters no longer go missing or jump around.

## 0.11.0 (5) — 2026-10-25

- Time planks and other holds in seconds.
- Sign in to backup with a password as well as an emailed code.
- Safer sign-in: links only work on the phone that asked.
- Undo when you delete a set.
- Faster start-up and smoother logging.
- Clearer screens, loading states and messages throughout.

## 0.10.0 (3) — 2026-10-25

First production release (Sweden only).
