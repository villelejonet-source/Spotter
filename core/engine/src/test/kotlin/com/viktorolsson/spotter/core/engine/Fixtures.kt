package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** The real bundled library, so tests exercise the same data the app ships. */
object Fixtures {
    @Serializable
    private data class SeedFile(val exercises: List<SeedExercise>)

    @Serializable
    private data class SeedExercise(
        val id: String,
        val name: String,
        val primary: List<Muscle>,
        val secondary: List<Muscle> = emptyList(),
        val pattern: MovementPattern,
        val equipment: List<Equipment> = emptyList(),
        val mechanics: Mechanics,
        val difficulty: Difficulty,
        val unilateral: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }

    val library: List<Exercise> by lazy {
        val text = File("../data/src/main/assets/exercises.json").readText()
        json.decodeFromString<SeedFile>(text).exercises.map {
            Exercise(
                it.id, it.name, it.primary, it.secondary, it.pattern, it.equipment, it.mechanics,
                it.difficulty, it.unilateral, instructions = null, isCustom = false,
            )
        }
    }

    val byId by lazy { library.associateBy { it.id } }

    val today: LocalDate = LocalDate.of(2026, 10, 3)
    val now: Instant = Instant.parse("2026-10-03T10:00:00Z")

    val fullGym = Equipment.entries.toSet()
    val dumbbellsOnly = setOf(Equipment.DUMBBELL, Equipment.BENCH)
    val homeBarbell = setOf(Equipment.BARBELL, Equipment.SQUAT_RACK, Equipment.BENCH, Equipment.DUMBBELL, Equipment.PULL_UP_BAR)

    fun profile(
        sex: Sex = Sex.FEMALE,
        age: Int = 28,
        heightCm: Double = 168.0,
        weightKg: Double = 62.0,
        units: WeightUnit = WeightUnit.KG,
        experience: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
        goal: Goal = Goal.HYPERTROPHY,
        days: Int = 4,
        minutes: Int = 60,
        equipment: Set<Equipment> = fullGym,
        focus: Set<BodyArea> = emptySet(),
        limitations: Set<Limitation> = emptySet(),
    ) = UserProfile(
        sex = sex,
        heightCm = heightCm,
        birthDate = today.minusYears(age.toLong()).minusDays(10),
        bodyWeightKg = weightKg,
        units = units,
        experience = experience,
        goal = goal,
        daysPerWeek = days,
        sessionLengthMinutes = minutes,
        equipment = equipment,
        focusAreas = focus,
        limitations = limitations,
    )

    fun generate(profile: UserProfile, known: KnownLifts = KnownLifts(), override: com.viktorolsson.spotter.core.model.SplitType? = null) =
        PlanGenerator().generate(PlanInput(profile, library, today, now, known, override))

    fun describe(generated: GeneratedPlan): String = buildString {
        appendLine("${generated.plan.name} (${generated.plan.splitType}) unfilled=${generated.unfilledPatterns}")
        generated.plan.days.forEachIndexed { i, day ->
            appendLine("  ${day.name} ~${generated.estimatedMinutes[i]} min")
            day.exercises.forEach {
                appendLine(
                    "    ${it.exercise.id}  ${it.sets}x${it.repMin}-${it.repMax} RIR${it.targetRir} rest${it.restSeconds}" +
                        " ${it.progressionRule} ss=${it.supersetGroup} start=${it.startingWeightKg}",
                )
            }
        }
    }
}
