# Changelog

What changed for people using Spotter, newest first. Every pull request that changes something a
user or the website would notice adds a line under **Unreleased**; `/release` turns that section
into the new version's entry and writes the store notes from it. Versions are the Android
`versionName` (`versionCode` in brackets); the full build record is the version table in
`docs/play-store/README.md`.

## Unreleased

### Added
- Delete a finished workout, in the app (History → the workout → the bin icon) or on the website
  (the workout's page → Delete workout). Its sets and any records set in it go too, on every
  device; a scheduled day it completed shows as not done again.

### Changed
- The website has a new look: dark-first with a glowing orange accent, monospace numbers, a new
  sidebar, and a "Jump to…" command palette (⌘K) to reach any page or action. Light mode
  follows your system setting.
- The website's Today page is a dashboard: stat tiles with trend lines and the change in volume
  against the previous four weeks, a weekly volume chart with a 4W/12W/6M/1Y switch, your next
  workout with this week's progress as a ring, a training heatmap, records and recent workouts.
- The website's charts match: progress lines and body weight glow over a fading area, bar charts
  fade with the latest bar highlighted, and muscle balance shows its target range as a dashed band.
- The app takes on the website's look: Spotter's own colours (a near-black dark mode with the
  orange accent, and a matching light mode), the Geist fonts with monospace numbers, outlined
  cards, a glowing main button and a new tab bar. Android's wallpaper colours (Material You) are
  now off by default; turn them on in Profile → Appearance. Anyone who already chose keeps their
  choice.
- The app's Today tab is a dashboard: your next workout with this week's progress as a ring,
  stat tiles with trend lines (this week against your goal, volume over four weeks with the change
  against the four before, week streak, all-time workouts) and a heatmap of training days. Start
  workout now sits at the bottom of the screen, in reach of your thumb.

### iOS (not released yet)
- The whole app runs on iOS from the same shared code as Android (#13–#20).
- Fixed a crash on iOS whenever the app logged a warning, such as a failed sync (#27).
- Builds without cloud backup no longer show account buttons that couldn't work (#21).
- The app has its icon on iOS too (it showed a blank placeholder).

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
