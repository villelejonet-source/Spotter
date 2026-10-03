# Spotter (Android): implementation plan

_Approved 2026-10-03._

## Context
You want an Android-only, mobile-only gym app where logging a workout is quick and getting around is simple. It should:
- Run an onboarding questionnaire (profile and goals) and build a tailored plan from the answers.
- Support full-body and split workouts.
- Let you swap an exercise for a similar one when the equipment is busy or missing.
- Keep history per workout and per exercise.
- Spot plateaus and suggest specific fixes.
- Include a stopwatch and rest timer, plus dark and light mode.

This is a new project and has nothing to do with the HubSpot Helper repo. It lives in its own folder, `~/private/dev/gym`, and on GitHub at `villelejonet-source/Spotter`.

## Assumptions (defaults, easy to change)
- **Stack:** native Kotlin with Jetpack Compose and Material 3. Android-only rules out the main reason to go cross-platform. Compose gives the best performance, a foreground-service timer, Material You dynamic color and dark mode for free.
- **Offline-first, single user.** Room/SQLite on the device is always the source of truth, and the app works fully without an account or a connection. **Supabase** (decided 2026-10-03) adds optional sign-in and cloud backup/sync in milestone 8, after the core app works. Health data only leaves the device if the user signs in to back up.
- **No accounts and no AI service.** Recommendations come from a deterministic rules engine that runs on the device, so the app is explainable, works offline and costs nothing.
- Min SDK 26, target the latest SDK.

## Tech stack
| Concern | Choice |
|---|---|
| UI | Jetpack Compose, Material 3, dynamic color |
| Architecture | MVVM + unidirectional data flow (ViewModel → `StateFlow<UiState>`) |
| DI | Hilt |
| DB | Room (+ migrations), Kotlin coroutines/Flow |
| Settings | DataStore (theme, units, rest defaults) |
| Navigation | Navigation-Compose (type-safe routes) |
| Charts | Vico (Compose-native charts) |
| Timer | Foreground service + ongoing notification, vibration/sound on finish |
| Tests | JUnit5 for the engine, Room in-memory tests, Compose UI tests, Paparazzi screenshots |

## Module layout
```
app/                    navigation host, theme, DI wiring
core/model              pure Kotlin domain models
core/data               Room DB, DAOs, repositories, seed exercise library (JSON asset)
core/engine             pure Kotlin: plan generator, progression, plateau detection, substitution
core/ui                 design system: components, theme tokens, timer UI
feature/onboarding      setup wizard
feature/today           home / start workout
feature/session         active workout logger + timers
feature/history         calendar, per-workout, per-exercise history
feature/progress        charts, PRs, recommendations
feature/library         exercise browser + swap sheet
feature/settings        profile, units, theme, backup
```
`core/engine` has no Android dependencies, so the whole recommendation brain can be unit-tested.

## Navigation (keep it simple)
A bottom bar with 4 tabs, designed for one-handed use:
1. **Today**: the next planned workout and a big "Start workout" button, plus a quick "Empty workout".
2. **History**: a calendar or list of past sessions. Tap a session to see its detail; tap an exercise to see that exercise's history.
3. **Progress**: charts, PRs, body weight and a recommendations feed.
4. **Profile**: profile, plan settings, theme and units.

An active workout is a full-screen route with a persistent mini-bar ("Workout in progress · 32:10"). If you leave it, you can jump back from any tab.

## Data model (Room)
- `UserProfile`: sex, height, birthDate, bodyWeight, units (kg/lb), experience level, goal, daysPerWeek, sessionLength, equipment access, limitations/injuries.
- `BodyWeightEntry`: date, weight.
- `Exercise`: id, name, primaryMuscles, secondaryMuscles, movementPattern (horizontal push, vertical pull, squat, hinge, lunge, carry, isolation…), equipment, mechanics (compound/isolation), difficulty, unilateral flag, instructions, `isCustom`.
- `ExerciseSimilarity` (derived or seeded): exerciseA, exerciseB, score.
- `Plan` → `PlanDay` → `PlanExercise` (exercise, order, sets, repRange, targetRIR, restSeconds, progressionRule, supersetGroup).
- `WorkoutSession`: start, end, planDayId?, notes, perceived difficulty.
- `SessionExercise`: sessionId, exerciseId, order, `substitutedFromExerciseId?`.
- `SetEntry`: weight, reps, RIR/RPE (optional), setType (warmup/working/drop/failure), completedAt.
- `PersonalRecord` (cached): exerciseId, type (1RM est., rep PR at weight, volume), value, date.
- `Recommendation`: type, payload, createdAt, dismissed/applied.

Exercise library: seed about 150–250 exercises from a bundled JSON asset. Tag each one with movement pattern, muscles and equipment; these tags drive both plan generation and swaps.

## Onboarding questionnaire (about 2 minutes, one question per screen)
1. Sex, age, height, weight. Units (kg/lb, cm/ft) are toggled inline.
2. **Main goal:** strength, hypertrophy (muscle), general fitness, or fat loss + keep muscle.
3. **Experience:** new (<6 months), intermediate (6 months–2 years), advanced (2+ years).
4. **Days per week:** 2–6.
5. **Session length:** 30 / 45 / 60 / 75+ min.
6. **Equipment:** full commercial gym / home gym (barbell + rack / dumbbells only) / bodyweight only. Individual items can be toggled.
7. **Focus areas** (optional, multi-select): e.g. glutes, arms, chest, back, legs, core.
8. **Limitations** (optional): shoulder, knee, lower back, wrist. These exclude or deprioritize the related movement patterns.
9. **Known lifts** (optional, skippable): current bench, squat and deadlift, or "not sure". These seed starting weights.

The wizard ends on a plan summary screen (split, days, sample workout) with "Adjust" and "Start".

## Plan generator (`core/engine`)
**1. Choose the split** from days/week × experience × session length:
- 2–3 days → Full body (A/B or A/B/C)
- 4 days → Upper/Lower
- 5 days → Upper/Lower + Full, or PPL + Upper/Lower
- 6 days → Push/Pull/Legs ×2
- You can always override this ("Full body" / "Half body = Upper/Lower" / "PPL").

**2. Fill each day with movement-pattern slots.** For example, Full body = squat or lunge, hinge, horizontal push, horizontal pull, vertical push or pull, plus 1–2 accessories. Each slot is filled with the best-fitting exercise for your equipment, experience and limitations.

**3. Set the goal parameters:**

| Goal | Main lifts | Accessories | Rest | Weekly sets/muscle |
|---|---|---|---|---|
| Strength | 3–5 × 3–6 @ RIR 1–3 | 3 × 6–10 | 2–4 min | 8–12 |
| Hypertrophy | 3–4 × 6–12 @ RIR 0–2 | 3 × 10–15 | 1.5–2.5 min | 10–20 |
| General | 3 × 8–12 | 2–3 × 10–15 | 1–2 min | 8–12 |
| Fat loss | 3 × 8–12 + optional supersets | 2–3 × 12–15 | 60–90 s | 8–12 |

Experience scales volume: new lifters start at the low end, advanced lifters at the high end.

**4. Apply sex and body metrics.** The evidence shows only modest differences between men and women in how they respond to training, so these inputs **adjust** the plan rather than produce two separate programs:
- **Starting-weight estimates** use bodyweight ratios by sex and experience when you skip "known lifts". The first session is labelled "calibration", and weights auto-adjust from the RIR you log.
- **Volume and rest:** women often recover faster between sets at the same relative intensity, so they get slightly shorter default rest and a slightly higher rep tolerance.
- **Default focus emphasis** (e.g. glutes and legs vs chest and arms) is only a *default selection* in the focus-areas question, and you can change it freely.
- **Age:** over 50, the generator favours more machine and dumbbell variations, longer warm-ups and slightly lower starting intensity.
- **BMI/weight:** above a high threshold, impact-heavy and bodyweight-dependent movements are swapped out (e.g. jumps, pull-ups → lat pulldown).

**5. Progression rules** are attached to each `PlanExercise`:
- **Double progression** (default for hypertrophy and accessories): work within the rep range. Once all sets hit the top of the range, add weight (+2.5 kg compound, +1–2 kg isolation or the smallest increment) and drop back to the bottom.
- **Linear** (strength, beginners): +2.5–5 kg per successful session on main lifts.
- **RIR auto-regulation:** if the logged RIR is far from target, the next session's weight adjusts.
- **Deload:** every 4–8 weeks, or when the plateau detector fires on several lifts, the app suggests a lighter week.

## Exercise swap (busy or missing equipment)
- In the session, each exercise card has a **"Swap"** action that opens a bottom sheet with ranked alternatives.
- **Similarity score:** same movement pattern (heavy weight) + overlap of primary muscles + same mechanics + availability of your equipment − limitation conflicts.
- **Quick filters:** "Same equipment", "Dumbbell only", "Machine", "Bodyweight".
- **Choices:** "Just this session" (default) or "Replace in plan". The session records `substitutedFromExerciseId`, so history still links back.
- **Starting weight** for the substitute comes from its own history, or else is estimated from the original exercise's estimated 1RM with a conversion factor per equipment type (e.g. DB bench ≈ 0.8 × BB bench ÷ 2 per hand).

## Workout logging UX (the most important screen)
- One scrollable list of exercise cards. Each set is a row: `prev | weight | reps | (RIR) | ✓`.
- Weight and reps are **pre-filled** from the progression target, with the previous session shown in grey next to them. Most sets take one tap on ✓.
- Large touch targets, a numeric keypad with ±2.5 / ±1 steppers, and swipe to delete a set.
- Ticking a set **starts the rest timer automatically** with that exercise's default rest.
- Add a set, an exercise, a note, or a superset (group exercises; rest only after the last one in the group).
- A warm-up set generator for main lifts (e.g. bar × 10, 50 % × 5, 70 % × 3, 85 % × 1).
- A plate calculator: tap the weight to see the plates per side.
- The session is saved on every change, so a crash or a phone call never loses data. The screen stays awake during a session.
- Finishing the workout shows a summary: duration, volume, PRs hit and next session's targets.

## Timers
- **Workout stopwatch:** total session time shown in the top bar, based on the session start timestamp (not a ticking counter), so it survives the process being killed.
- **Rest timer:** a foreground service with an ongoing notification (countdown + "+30 s / Skip"), vibration and an optional sound at 0. It works when the screen is off or another app is open. The rest time can be adjusted inline.
- A standalone stopwatch/timer is also reachable from the session overflow menu, for planks, carries and similar.

## History and progress
- **Per workout:** a calendar heat-map and a list. The detail view shows all sets, volume, duration and notes, with "Repeat this workout".
- **Per exercise:** a set log over time, plus charts of best set, estimated 1RM (Epley/Brzycki), volume per session and rep PRs at each weight.
- **PR detection** runs on save, celebrates the PR and stores it in `PersonalRecord`.
- **Muscle balance:** weekly sets per muscle group compared with the target, which flags under-trained muscles.
- **Body weight:** trend chart with a 7-day moving average.

## Plateau detection and improvement recommendations
These rules live in `core/engine` and run after each session:
- **Plateau signal:** no estimated-1RM or rep improvement for an exercise in 3+ exposures over 3+ weeks, or a failed progression target twice in a row.
- **Diagnosis**, then a recommendation built from data tables (not hard-coded per lift), so it applies to every exercise:
  - Each main lift maps to its **weak-point accessories** by movement pattern and muscles involved. For example:
    - Bench → close-grip bench, paused bench, DB press, triceps (dips, skull crushers), front delts, rows for stability.
    - Squat → paused squat, front squat, leg press, Bulgarian split squat, core work.
    - Deadlift → RDL, deficit or block pulls, back extensions, rows.
    - Overhead press → push press, lateral raises, triceps, upper back.
    - Pull-up → negatives, lat pulldown, rows, biceps.
  - **Volume check:** if weekly sets for the muscles involved are below target, add sets.
  - **Recovery check:** if RIR trends to 0 and performance falls across several lifts, suggest a deload week.
  - **Rep-range change:** if you have been stuck in the same range for a long time, suggest switching (e.g. 5×5 → 4×8 for a block).
  - **Frequency:** if a muscle is trained only once a week, suggest twice.
  - **Variation:** swap to a close variant for 4–6 weeks, then return.
- Recommendations appear as cards in Progress and on the related exercise screen, with "Apply to plan" or "Dismiss". Each card explains *why* (e.g. "Bench 80 kg × 5 for 4 sessions").

## Theming
Material 3 theme with light, dark and "follow system" settings, plus dynamic color on Android 12+ and a branded fallback palette. Both themes get contrast checks in the Paparazzi screenshot tests.

## v1 scope (confirmed)
- kg/lb units
- RIR/RPE logging (optional)
- Warm-up sets
- Plate calculator
- Deload weeks
- Injury/limitations question
- Equipment profile
- Notes per set and session
- Custom exercises
- Supersets
- PR celebrations
- Auto-save and crash safety
- Keep screen on

**Out of v1:** data export/import.

**Later ideas:**
- Exercise instructions with images or GIFs
- Body measurements and progress photos
- Health Connect sync
- Home-screen widget
- Training-day reminders
- Wear OS rest timer
- Cardio logging
- Program templates (5/3/1, PHUL)
- Sharing workouts

**Privacy and safety:** no analytics. Health data stays on the device unless the user signs in to cloud backup (milestone 8), and then only in their own row-level-secured Supabase rows. Show a "consult a professional" note for limitations, and make no medical claims.

## Milestones
1. **Foundation (week 1):** project setup, modules, theme and dark mode, navigation shell, Room schema, seeded exercise library.
2. **Logging core (weeks 2–3):** empty workout, exercise picker, set logging, auto-save, rest timer service, stopwatch, finish summary.
3. **Onboarding + plan generator (weeks 3–4):** wizard, engine (split, slots, parameters), plan screens, Today tab.
4. **Swap + progression (week 5):** similarity scoring, swap sheet, progression rules, pre-filled targets.
5. **History + progress (week 6):** per-workout and per-exercise history, charts, PRs, muscle balance.
6. **Recommendations (week 7):** plateau detection, accessory mapping, deload, recommendation cards.
7. **Polish (week 8):** accessibility, plate calculator, warm-ups, PR celebrations, screenshot tests, Play Store internal test track.
8. **Cloud backup + sync (weeks 9–10):** optional Supabase sign-in, Postgres schema mirroring Room with row-level security per user, background push/pull sync (last-write-wins per row, soft deletes), restore on a new phone.

## Verification
- **Engine unit tests:** golden tests for plan generation (e.g. "female, 28, intermediate, hypertrophy, 4 days, full gym" → expected split, slots and parameters), progression math, plateau detection on synthetic histories, swap ranking (bench busy → DB bench / machine press first).
- **Room tests:** migrations, PR computation, history queries.
- **Compose UI tests:** onboarding → plan created; log a full session; swap mid-session; timer notification appears and fires.
- **Manual on a device or emulator:** run a real workout end-to-end with the screen off during rest, kill the app mid-session and confirm recovery, toggle dark and light mode, and confirm logging a set takes one tap in most cases.
