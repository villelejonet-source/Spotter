package com.viktorolsson.spotter.core.data.seed

import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the bundled library: it must parse, and the tags the engine relies on must be sane. */
class ExerciseSeedTest {
    private val seed = parseExerciseSeed(File("src/main/assets/exercises.json").readText())
    private val exercises = seed.exercises

    @Test
    fun `library size is within the planned range`() {
        assertTrue("got ${exercises.size}", exercises.size in 150..250)
    }

    @Test
    fun `ids are unique kebab-case slugs and names are unique`() {
        assertEquals(exercises.size, exercises.map { it.id }.toSet().size)
        assertEquals(exercises.size, exercises.map { it.name.lowercase() }.toSet().size)
        exercises.forEach { assertTrue(it.id, it.id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*"))) }
    }

    @Test
    fun `every exercise has a primary muscle that is not also secondary`() {
        exercises.forEach { ex ->
            assertTrue(ex.id, ex.primary.isNotEmpty())
            assertTrue(ex.id, ex.primary.intersect(ex.secondary.toSet()).isEmpty())
        }
    }

    @Test
    fun `every movement pattern has at least three exercises so swaps always have options`() {
        val byPattern = exercises.groupBy { it.pattern }
        MovementPattern.entries.forEach { pattern ->
            assertTrue("$pattern has ${byPattern[pattern]?.size ?: 0}", (byPattern[pattern]?.size ?: 0) >= 3)
        }
    }

    @Test
    fun `main patterns can be filled with bodyweight only, dumbbells only and a full gym`() {
        val mainPatterns = listOf(
            MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE,
            MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
            MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL,
        )
        val dumbbellGym = setOf(Equipment.DUMBBELL, Equipment.BENCH)
        mainPatterns.forEach { pattern ->
            // No compound vertical pull exists with dumbbells alone; the pullover (isolation) is the fallback.
            val candidates = exercises.filter {
                it.pattern == pattern && (it.mechanics == Mechanics.COMPOUND || pattern == MovementPattern.VERTICAL_PULL)
            }
            assertTrue("$pattern: no dumbbell option", candidates.any { dumbbellGym.containsAll(it.equipment) })
        }
        // Bodyweight-only users still need a pull; a pull-up bar or band is the minimum kit there.
        listOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE, MovementPattern.HORIZONTAL_PUSH)
            .forEach { pattern ->
                val noKit = exercises.filter { it.pattern == pattern && it.equipment.isEmpty() } +
                    exercises.filter { it.pattern == pattern && it.equipment == listOf(Equipment.RESISTANCE_BAND) }
                assertTrue("$pattern: no bodyweight/band option", noKit.isNotEmpty())
            }
    }
}
