package com.viktorolsson.spotter.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.PersonalRecordEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanExerciseWithExercise
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.engine.LoggedSet
import com.viktorolsson.spotter.core.engine.PersonalRecords
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.engine.PlanInput
import com.viktorolsson.spotter.core.engine.Progression
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.isTimed
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug builds only: fills the app with about five weeks of realistic training, for
 * Play Store screenshots and manual testing. On a fresh install:
 *
 *     adb shell am broadcast -n com.viktorolsson.spotter.debug/com.viktorolsson.spotter.debug.DemoDataReceiver
 *
 * Does nothing if workouts are already logged.
 */
@AndroidEntryPoint
class DemoDataReceiver : BroadcastReceiver() {
    @Inject lateinit var seeder: DemoDataSeeder

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                Log.i(TAG, seeder.seed())
            } catch (e: Exception) {
                Log.e(TAG, "Seeding failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SpotterDemoData"
    }
}

class DemoDataSeeder @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val planDao: PlanDao,
    private val profiles: UserProfileRepository,
    private val plans: PlanRepository,
    private val exercises: ExerciseRepository,
    private val preferences: UserPreferencesRepository,
    private val bodyWeight: BodyWeightRepository,
    private val recommendations: RecommendationRepository,
    private val clock: Clock,
) {
    private val zone = ZoneId.systemDefault()
    private val random = Random(42)

    suspend fun seed(): String {
        if (workoutDao.getTrainingStarts().isNotEmpty()) return "Workouts already logged; nothing seeded."
        val today = LocalDate.now(clock)
        if (profiles.get() == null) createProfileAndPlan(today)
        val plan = planDao.getActivePlan() ?: return "No active plan."
        val days = plan.days.sortedBy { it.day.position }

        // Monday, Tuesday, Thursday and Friday for the five weeks before this one.
        val firstMonday = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(WEEKS.toLong())
        val dates = (0 until WEEKS).flatMap { week -> listOf(0L, 1L, 3L, 4L).map { firstMonday.plusWeeks(week.toLong()).plusDays(it) } }
            .filter { it.isBefore(today) }

        // The first lift of the first day stalls after its second session (three weeks
        // before the last one), so Progress shows a plateau fix.
        val stalledExerciseId = days.first().exercises.minBy { it.planExercise.position }.exercise.id
        val state = mutableMapOf<String, LiftState>()
        dates.forEachIndexed { i, date ->
            val day = days[i % days.size]
            logSession(date, day.day.id, day.exercises.sortedBy { it.planExercise.position }, state, stalledExerciseId, date.isBefore(firstMonday.plusWeeks(1)))
        }

        var weight = START_BODY_WEIGHT
        generateSequence(today.minusWeeks(WEEKS.toLong())) { it.plusDays(3) }.takeWhile { !it.isAfter(today) }.forEach { date ->
            weight -= 0.12
            bodyWeight.log(date, ((weight + random.nextDouble(-0.3, 0.3)) * 10).roundToInt() / 10.0)
        }

        recommendations.refresh()
        return "Seeded ${dates.size} workouts."
    }

    private suspend fun createProfileAndPlan(today: LocalDate) {
        val profile = UserProfile(
            sex = Sex.MALE,
            heightCm = 181.0,
            birthDate = today.minusYears(31),
            bodyWeightKg = START_BODY_WEIGHT,
            units = WeightUnit.KG,
            experience = ExperienceLevel.INTERMEDIATE,
            goal = Goal.HYPERTROPHY,
            daysPerWeek = 4,
            sessionLengthMinutes = 60,
            equipment = Equipment.entries.toSet(),
            focusAreas = setOf(BodyArea.CHEST, BodyArea.BACK),
            limitations = emptySet(),
        )
        val library = exercises.observeAll().first { it.isNotEmpty() }
        val generated = PlanGenerator().generate(
            PlanInput(profile = profile, library = library, today = today.minusWeeks(WEEKS.toLong()), now = clock.instant()),
        )
        profiles.save(profile)
        plans.saveAsActive(generated.plan)
        preferences.setWeightUnit(WeightUnit.KG)
        preferences.setOnboardingCompleted(true)
    }

    private suspend fun logSession(
        date: LocalDate,
        planDayId: Long,
        planned: List<PlanExerciseWithExercise>,
        state: MutableMap<String, LiftState>,
        stalledExerciseId: String,
        earlyWeeks: Boolean,
    ) {
        val start = date.atTime(LocalTime.of(17, 20).plusMinutes(random.nextLong(0, 40))).atZone(zone).toInstant()
        val sessionId = workoutDao.insertSession(
            WorkoutSessionEntity(startedAt = start, endedAt = null, planDayId = planDayId, notes = null, perceivedDifficulty = null),
        )
        var time = start.plus(Duration.ofMinutes(4))
        planned.forEachIndexed { position, (pe, exercise) ->
            val compound = exercise.mechanics == Mechanics.COMPOUND
            val lift = state.getOrPut(exercise.id) {
                LiftState(weightKg = pe.startingWeightKg ?: startingWeight(exercise.toModel()), reps = pe.repMin)
            }
            val sessionExerciseId = workoutDao.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = exercise.id,
                    position = position,
                    substitutedFromExerciseId = null,
                    supersetGroup = pe.supersetGroup,
                    notes = null,
                    restSeconds = pe.restSeconds,
                    planExerciseId = pe.id,
                ),
            )
            val sets = (0 until pe.sets).map { i ->
                time = time.plusSeconds(pe.restSeconds + 40L)
                // The last set sometimes falls a rep short, as real sets do.
                val reps = if (i == pe.sets - 1 && lift.reps > pe.repMin && random.nextBoolean()) lift.reps - 1 else lift.reps
                SetEntryEntity(
                    sessionExerciseId = sessionExerciseId,
                    position = i,
                    weightKg = lift.weightKg,
                    reps = reps,
                    rir = pe.targetRir ?: 2,
                    rpe = null,
                    setType = SetType.WORKING,
                    completedAt = time,
                    notes = null,
                )
            }
            workoutDao.insertSets(sets)
            recordPersonalRecords(exercise.id, sets, start, sessionId, time)

            // Double progression: a rep per session up to the top of the range, then more weight.
            if (exercise.id != stalledExerciseId || earlyWeeks) {
                state[exercise.id] = when {
                    lift.reps < pe.repMax -> lift.copy(reps = (lift.reps + if (exercise.toModel().isTimed) Progression.TIMED_STEP_SECONDS else 1).coerceAtMost(pe.repMax))
                    lift.weightKg == null -> lift.copy(reps = pe.repMin + 2)
                    else -> LiftState(lift.weightKg + if (compound) 2.5 else 1.0, pe.repMin)
                }
            }
        }
        workoutDao.endSession(sessionId, time.plus(Duration.ofMinutes(3)))
    }

    private suspend fun recordPersonalRecords(
        exerciseId: String,
        sets: List<SetEntryEntity>,
        start: Instant,
        sessionId: Long,
        achievedAt: Instant,
    ) {
        val previous = workoutDao.getWorkingSetsBefore(exerciseId, start).groupBy { it.sessionId }.values
            .map { group -> group.map { LoggedSet(it.set.weightKg, it.set.reps ?: 0, it.set.rir) } }
        val logged = sets.map { LoggedSet(it.weightKg, it.reps ?: 0, it.rir) }
        val records = PersonalRecords.detect(logged, PersonalRecords.bests(previous)).map {
            PersonalRecordEntity(
                exerciseId = exerciseId,
                type = it.type,
                value = it.value,
                weightKg = it.weightKg,
                reps = it.reps,
                achievedAt = achievedAt,
                sessionId = sessionId,
            )
        }
        if (records.isNotEmpty()) workoutDao.insertRecords(records)
    }

    /** Accessories get no plan estimate; these are typical for an intermediate lifter. */
    private fun startingWeight(exercise: Exercise): Double? = when {
        exercise.equipment.all { it in BODYWEIGHT_EQUIPMENT } -> null
        "Calf" in exercise.name -> 60.0
        Equipment.MACHINE in exercise.equipment -> 35.0
        Equipment.CABLE in exercise.equipment -> 15.0
        else -> 12.0
    }

    private data class LiftState(val weightKg: Double?, val reps: Int)

    private companion object {
        const val WEEKS = 5
        const val START_BODY_WEIGHT = 82.6
        val BODYWEIGHT_EQUIPMENT = setOf(Equipment.PULL_UP_BAR, Equipment.DIP_STATION, Equipment.BENCH)
    }
}
