package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.Fixtures.generate
import com.viktorolsson.spotter.core.engine.Fixtures.profile
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGeneratorTest {
    private val GeneratedPlan.allExercises: List<PlanExercise> get() = plan.days.flatMap { it.exercises }

    /** The golden case from PLAN.md: female, 28, intermediate, hypertrophy, 4 days, full gym. */
    @Test
    fun `golden - female intermediate hypertrophy four days full gym`() {
        val generated = generate(profile(focus = setOf(BodyArea.GLUTES, BodyArea.LEGS)))
        val plan = generated.plan

        assertEquals(SplitType.UPPER_LOWER, plan.splitType)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), plan.days.map { it.name })
        assertEquals(
            listOf("barbell-bench-press", "back-squat", "seated-dumbbell-shoulder-press", "deadlift"),
            plan.days.map { it.exercises.first().exercise.id },
        )
        assertTrue(generated.unfilledPatterns.isEmpty())

        val bench = plan.days[0].exercises[0]
        assertEquals(listOf(3, 6, 12, 1, 135), listOf(bench.sets, bench.repMin, bench.repMax, bench.targetRir, bench.restSeconds))
        assertEquals(ProgressionRule.DOUBLE_PROGRESSION, bench.progressionRule)

        // Secondary compounds: main rep range + 2 on the top end, 15 s less rest (sex adjustment).
        val row2 = plan.days[0].exercises[2]
        assertEquals(listOf(8, 14, 105), listOf(row2.repMin, row2.repMax, row2.restSeconds))

        // Glute focus lands a hip thrust on both lower days.
        plan.days.filter { it.name.startsWith("Lower") }.forEach { day ->
            assertTrue(day.name, day.exercises.any { it.exercise.movementPattern == MovementPattern.HIP_THRUST })
        }
        generated.estimatedMinutes.forEach { assertTrue("$it min", it <= 60) }
    }

    @Test
    fun `split follows days per week and experience`() {
        fun split(days: Int, exp: ExperienceLevel = ExperienceLevel.INTERMEDIATE) =
            generate(profile(days = days, experience = exp)).plan
        assertEquals(listOf("Full body A", "Full body B"), split(2).days.map { it.name })
        assertEquals(listOf("Full body A", "Full body B", "Full body C"), split(3).days.map { it.name })
        assertEquals(SplitType.UPPER_LOWER, split(4).splitType)
        assertEquals(SplitType.UPPER_LOWER_FULL, split(5).splitType)
        assertEquals(SplitType.PPL_UPPER_LOWER, split(5, ExperienceLevel.ADVANCED).splitType)
        assertEquals(listOf("Push", "Pull", "Legs", "Push", "Pull", "Legs"), split(6).days.map { it.name })
    }

    @Test
    fun `repeated days in a cycle get identical exercises`() {
        val days = generate(profile(days = 6)).plan.days
        assertEquals(days[0].exercises.map { it.exercise.id }, days[3].exercises.map { it.exercise.id })
    }

    @Test
    fun `split override is honoured, and PPL needs at least three days`() {
        assertEquals(SplitType.FULL_BODY, generate(profile(days = 4), override = SplitType.FULL_BODY).plan.splitType)
        assertEquals(
            listOf("Full body A", "Full body B", "Full body C", "Full body A"),
            generate(profile(days = 4), override = SplitType.FULL_BODY).plan.days.map { it.name },
        )
        assertEquals(SplitType.UPPER_LOWER, generate(profile(days = 2), override = SplitType.PUSH_PULL_LEGS).plan.splitType)
    }

    @Test
    fun `only exercises the user has equipment for`() {
        listOf(Fixtures.dumbbellsOnly, Fixtures.homeBarbell, emptySet(), setOf(Equipment.RESISTANCE_BAND, Equipment.PULL_UP_BAR))
            .forEach { equipment ->
                listOf(2, 3, 4, 5, 6).forEach { days ->
                    generate(profile(days = days, equipment = equipment)).allExercises.forEach {
                        assertTrue("${it.exercise.id} with $equipment", equipment.containsAll(it.exercise.equipment))
                    }
                }
            }
    }

    @Test
    fun `bodyweight only reports that pulling can't be programmed`() {
        val generated = generate(profile(days = 3, equipment = emptySet()))
        assertEquals(setOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL), generated.unfilledPatterns)
        assertTrue(generated.plan.days.all { it.exercises.size >= 3 })
    }

    @Test
    fun `limitations exclude risky exercises and steer away from strained patterns`() {
        Limitation.entries.forEach { limitation ->
            listOf(3, 4, 6).forEach { days ->
                val ids = generate(profile(days = days, limitations = setOf(limitation))).allExercises.map { it.exercise.id }
                val banned = Rules.excludedFor.getValue(limitation)
                assertTrue("$limitation/$days: ${ids.filter { it in banned }}", ids.none { it in banned })
            }
        }
        val knee = generate(profile(limitations = setOf(Limitation.KNEE))).allExercises
        assertTrue(knee.none { it.exercise.movementPattern == MovementPattern.LUNGE })

        val shoulder = generate(profile(limitations = setOf(Limitation.SHOULDER))).allExercises
        assertTrue(
            shoulder.filter { it.exercise.movementPattern == MovementPattern.VERTICAL_PUSH }.all { it.exercise.id == "landmine-press" },
        )
    }

    @Test
    fun `strength beginner repeats barbell mains with linear progression`() {
        val plan = generate(
            profile(sex = Sex.MALE, weightKg = 80.0, heightCm = 180.0, experience = ExperienceLevel.NEW, goal = Goal.STRENGTH, days = 3),
        ).plan
        assertEquals("back-squat", plan.days[0].exercises[0].exercise.id)
        assertEquals("back-squat", plan.days[2].exercises[0].exercise.id)
        plan.days.forEach { day ->
            day.exercises.take(2).forEach { main ->
                assertEquals(ProgressionRule.LINEAR, main.progressionRule)
                assertEquals(listOf(3, 3, 6, 3, 180), listOf(main.sets, main.repMin, main.repMax, main.targetRir, main.restSeconds))
            }
        }
    }

    @Test
    fun `new lifters never get advanced exercises`() {
        listOf(Fixtures.fullGym, Fixtures.dumbbellsOnly, emptySet()).forEach { equipment ->
            generate(profile(experience = ExperienceLevel.NEW, equipment = equipment, days = 3)).allExercises.forEach {
                assertFalse(it.exercise.id, it.exercise.difficulty == Difficulty.ADVANCED)
            }
        }
    }

    @Test
    fun `fat loss pairs accessories into supersets, never the main lifts`() {
        val plan = generate(profile(goal = Goal.FAT_LOSS, days = 4, minutes = 60)).plan
        plan.days.forEach { day ->
            val groups = day.exercises.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
            assertTrue(day.name, groups.isNotEmpty() && groups.values.all { it == 2 })
            assertNull(day.exercises.first().supersetGroup)
        }
    }

    @Test
    fun `starting weights come from known lifts, rounded down to plates`() {
        val known = KnownLifts(bench = LiftResult(weightKg = 100.0, reps = 5))
        val male = profile(sex = Sex.MALE, weightKg = 85.0, heightCm = 182.0)
        // e1RM 116.7 → 6 reps at RIR 1 → 94.6 → 90 % calibration → 85.1 → 85 kg.
        val bench = generate(male, known).plan.days[0].exercises.first { it.exercise.id == "barbell-bench-press" }
        assertEquals(85.0, bench.startingWeightKg!!, 0.001)

        val inLb = generate(male.copy(units = WeightUnit.LB), known).allExercises
        inLb.mapNotNull { it.startingWeightKg }.forEach { kg ->
            val lb = WeightUnit.LB.fromKg(kg)
            assertEquals("$lb lb", 0.0, lb % 5.0, 0.01)
        }
        // Isolation and bodyweight work is left to the user's first pick.
        assertNull(inLb.first { it.exercise.movementPattern == MovementPattern.LATERAL_RAISE }.startingWeightKg)
    }

    @Test
    fun `older lifters get more machines and dumbbells, lighter starts and more reps in reserve`() {
        val young = generate(profile(sex = Sex.MALE, age = 30, weightKg = 85.0, heightCm = 180.0)).allExercises
        val older = generate(profile(sex = Sex.MALE, age = 60, weightKg = 85.0, heightCm = 180.0)).allExercises
        fun barbellShare(list: List<PlanExercise>) = list.count { Equipment.BARBELL in it.exercise.equipment }.toDouble() / list.size
        assertTrue(barbellShare(older) < barbellShare(young))
        assertTrue(older.all { it.targetRir == young.first().targetRir!! + 1 })

        val youngStart = young.filter { it.startingWeightKg != null }.associate { it.exercise.id to it.startingWeightKg!! }
        val shared = older.filter { it.exercise.id in youngStart && it.startingWeightKg != null }
        assertTrue(shared.isNotEmpty())
        shared.forEach { assertTrue(it.exercise.id, it.startingWeightKg!! <= youngStart.getValue(it.exercise.id)) }
    }

    @Test
    fun `high BMI swaps bodyweight-dependent moves for supported ones`() {
        val all = generate(profile(sex = Sex.MALE, weightKg = 125.0, heightCm = 178.0, days = 4)).allExercises
        assertTrue(all.none { Rules.isBodyweightDependent(it.exercise) })
        assertTrue(all.any { it.exercise.id == "lat-pulldown" })
    }

    @Test
    fun `days fit the session length`() {
        listOf(30, 45, 60).forEach { minutes ->
            listOf(Goal.STRENGTH, Goal.HYPERTROPHY, Goal.FAT_LOSS).forEach { goal ->
                val generated = generate(profile(minutes = minutes, goal = goal, days = 3))
                generated.plan.days.forEachIndexed { i, day ->
                    val fits = generated.estimatedMinutes[i] <= minutes
                    assertTrue("$goal $minutes min: ${generated.estimatedMinutes[i]}", fits || day.exercises.size == 3)
                }
            }
        }
        generate(profile(minutes = 75)).estimatedMinutes.forEach { assertTrue(it <= 90) }
    }

    @Test
    fun `library order doesn't change the plan`() {
        val input = profile(focus = setOf(BodyArea.GLUTES, BodyArea.LEGS))
        val shuffled = Fixtures.library.shuffled(kotlin.random.Random(7))
        val byName = Fixtures.library.sortedBy { it.name }
        val expected = generate(input).plan
        listOf(shuffled, byName).forEach { library ->
            assertEquals(expected, PlanGenerator().generate(PlanInput(input, library, Fixtures.today, Fixtures.now)).plan)
        }
    }

    @Test
    fun `plans are deterministic, never repeat an exercise in a day, and skip custom exercises`() {
        val input = profile(days = 5, focus = setOf(BodyArea.ARMS))
        assertEquals(generate(input), generate(input))
        generate(input).plan.days.forEach { day ->
            assertEquals(day.exercises.size, day.exercises.map { it.exercise.id }.toSet().size)
        }
        val withCustom = Fixtures.library + byId.getValue("barbell-bench-press").copy(id = "custom-x", name = "Mine", isCustom = true)
        val plan = PlanGenerator().generate(PlanInput(input, withCustom, Fixtures.today, Fixtures.now)).plan
        assertTrue(plan.days.flatMap { it.exercises }.none { it.exercise.isCustom })
    }
}
