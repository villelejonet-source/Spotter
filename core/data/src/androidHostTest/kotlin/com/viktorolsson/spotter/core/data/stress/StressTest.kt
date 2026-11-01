package com.viktorolsson.spotter.core.data.stress

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SyncSchema
import com.viktorolsson.spotter.core.data.repository.MutableClock
import com.viktorolsson.spotter.core.data.stress.StressPhone.Companion.USER
import com.viktorolsson.spotter.core.data.sync.FakeRemote
import com.viktorolsson.spotter.core.data.sync.FirstSyncChoice
import com.viktorolsson.spotter.core.data.sync.SyncOutcome
import com.viktorolsson.spotter.core.data.sync.SyncRow
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.engine.PlanInput
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.time.Instant

/**
 * Random use of the app on up to three phones and the website, all on one account, checked
 * after every step. Each seed is one reproducible run: a lifter onboarding, logging and editing
 * workouts, changing plans, killing the app, syncing (or not) and adding phones, while the
 * website deletes and edits. What must hold throughout:
 *
 * - Nothing is lost: a row only ever disappears because someone deleted it (the action that
 *   ran, another phone, the website, or the "use the backup" choice), or because what it
 *   belonged to was deleted.
 * - Every phone's database stays consistent: no broken foreign keys, at most one active plan,
 *   sync triggers in place after a restart.
 * - After everyone syncs, every phone holds exactly what the backup holds.
 *
 * `./gradlew :core:data:testAndroidHostTest --tests '*StressTest*' -Pstress.seeds=500` runs more;
 * `-Pstress.seed=1234 -Pstress.seeds=1` replays one. Each failure is written to
 * `core/data/build/reports/stress/`, with the steps that led to it.
 */
@RunWith(AndroidJUnit4::class)
class StressTest {
    @Test
    fun randomUseAcrossPhonesNeverLosesOrSplitsData() {
        val first = System.getProperty("stress.seed")?.toLongOrNull() ?: 1L
        val count = System.getProperty("stress.seeds")?.toIntOrNull() ?: 8
        val steps = System.getProperty("stress.steps")?.toIntOrNull() ?: 150
        val reports = File("build/reports/stress").apply { deleteRecursively(); mkdirs() }

        val runs = (first until first + count).map { seed -> runBlocking { StressRun(seed, steps).apply { run() } } }
        val failures = runs.mapNotNull { run -> run.failure?.also { File(reports, "seed-${run.seed}.txt").writeText(it) } }
        File(reports, "summary.txt").writeText(
            buildString {
                appendLine("Seeds $first..${first + count - 1}, $steps steps each: ${count - failures.size} passed, ${failures.size} failed.")
                appendLine("Orphan rows left in the backup (a child of a workout or plan deleted while another phone edited it): ${runs.sumOf { it.orphans.size }}")
                runs.filter { it.failure != null }.forEach { appendLine("Seed ${it.seed}: ${it.failure!!.lineSequence().first()}") }
            },
        )
        if (failures.isNotEmpty()) {
            fail("${failures.size} of $count seeds failed (reports in core/data/build/reports/stress/). First:\n\n${failures.first()}")
        }
    }
}

private class Violation(message: String) : AssertionError(message)

private class Action(
    val name: String,
    val weight: Int,
    /** Tables this action may delete rows from on purpose. */
    val deletes: Set<String> = emptySet(),
    /** Runs on [StressPhone]; returns what it did, or null when it doesn't apply right now. */
    val run: suspend StressRun.(StressPhone) -> String?,
)

private class StressRun(val seed: Long, private val steps: Int) {
    val random = Random(seed)
    private val remote = FakeRemote()
    private val clock = MutableClock(Instant.parse("2026-10-01T07:00:00Z"))
    private val phones = mutableListOf<StressPhone>()
    private val log = mutableListOf<String>()

    /** Every synced row any phone has held, and the ones deleted on purpose. */
    private val seen = mutableSetOf<RowKey>()
    private val deliberate = mutableSetOf<RowKey>()

    /** Rows the backup holds whose workout or plan is gone; see [settle]. */
    val orphans = mutableSetOf<RowKey>()

    /** What went wrong, with the steps that led there; null when the run passed. */
    var failure: String? = null
        private set

    /** Set by a sync that answered "use the backup": this phone's own rows go, by choice. */
    var wiped = false

    suspend fun run() {
        failure = attempt()
    }

    private suspend fun attempt(): String? = try {
        newPhone(onboarded = true)
        repeat(steps) { step ->
            clock.advanceSeconds(random.nextLong(20, 900))
            // Sync keeps the strictly newer version; real edits on two phones are never in the same millisecond.
            Thread.sleep(2)
            when {
                phones.size < MAX_PHONES && random.nextInt(50) == 0 -> newPhone(onboarded = random.nextBoolean())
                random.nextInt(25) == 0 -> websiteAction()
                else -> phoneAction(phones.random(random), pick())
            }
            if ((step + 1) % 40 == 0) settle()
        }
        settle()
        null
    } catch (e: Throwable) {
        buildString {
            appendLine("Seed $seed failed after ${log.size} steps: ${e.message}")
            if (e !is Violation) appendLine(e.stackTraceToString().lines().take(12).joinToString("\n"))
            appendLine()
            appendLine("Replay: ./gradlew :core:data:testAndroidHostTest --tests '*StressTest*' -Pstress.seed=$seed -Pstress.seeds=1 -Pstress.steps=$steps")
            appendLine()
            appendLine("Last steps:")
            log.takeLast(40).forEach { appendLine("  $it") }
        }
    } finally {
        phones.forEach { it.close() }
    }

    private fun pick(): Action {
        val total = ACTIONS.sumOf { it.weight }
        var roll = random.nextInt(total)
        return ACTIONS.first { roll -= it.weight; roll < 0 }
    }

    /** Runs [action] (or another, when it doesn't apply) and checks what it left behind. */
    private suspend fun phoneAction(phone: StressPhone, action: Action) {
        var chosen = action
        repeat(20) {
            val before = phone.rows()
            wiped = false
            val did = chosen.run(this, phone)
            if (did != null) {
                log += "${phone.name}: ${chosen.name} $did"
                checkAfter(phone, chosen.name, chosen.deletes, before)
                return
            }
            chosen = pick()
        }
    }

    private suspend fun checkAfter(phone: StressPhone, what: String, deletes: Set<String>, before: Map<RowKey, JsonObject>) {
        val after = phone.rows()
        (before.keys - after.keys).forEach { key ->
            when {
                wiped || key.first in deletes -> deliberate += key
                key in deliberate -> Unit
                belongsToDeleted(key, before) -> deliberate += key
                key.first == "body_weight_entry" && sameDayEntry(before.getValue(key), after) -> deliberate += key
                else -> throw Violation("${phone.name}: $what lost ${key.first} ${key.second}, which nobody deleted")
            }
        }
        seen += after.keys
        val brokenKeys = phone.count("SELECT COUNT(*) FROM pragma_foreign_key_check")
        if (brokenKeys > 0) throw Violation("${phone.name}: $what left $brokenKeys rows pointing at rows that don't exist")
        val activePlans = phone.count("SELECT COUNT(*) FROM plan WHERE isActive = 1")
        if (activePlans > 1) throw Violation("${phone.name}: $what left $activePlans active plans")
    }

    /** True when something [key] belongs to (its exercise, workout, day or plan) was deleted on purpose. */
    private fun belongsToDeleted(key: RowKey, rows: Map<RowKey, JsonObject>) = parentsOf(key, rows).any { it in deliberate }

    /** What [key] belongs to, nearest first, as far as [rows] knows: set → exercise → workout, plan exercise → day → plan. */
    private fun parentsOf(key: RowKey, rows: Map<RowKey, JsonObject>): List<RowKey> = buildList {
        var current = key
        while (true) {
            val (column, parentTable) = CASCADES[current.first] ?: break
            val parentId = rows[current]?.get(column)?.jsonPrimitive?.contentOrNull ?: break
            current = parentTable to parentId
            add(current)
        }
    }

    /** Two phones logged the same day: one entry per day stays, by design. */
    private fun sameDayEntry(gone: JsonObject, after: Map<RowKey, JsonObject>): Boolean =
        after.any { (key, row) -> key.first == "body_weight_entry" && row["date"] == gone["date"] }

    private suspend fun newPhone(onboarded: Boolean) {
        val phone = StressPhone("phone-${'A' + phones.size}", remote, clock)
        phone.open(firstTime = true)
        phones += phone
        if (onboarded) {
            phone.profiles.save(PROFILE)
            savePlan(phone, PROFILE.daysPerWeek)
        }
        log += "${phone.name}: new phone${if (onboarded) " after onboarding" else ""}"
        val before = phone.rows()
        wiped = false
        val did = syncAnswering(phone)
        log += "${phone.name}: first sync $did"
        checkAfter(phone, "first sync", emptySet(), before)
    }

    /** Syncs, answering the first-sync question at random as a lifter would. */
    suspend fun syncAnswering(phone: StressPhone): String {
        val outcome = phone.sync()
        if (outcome !is SyncOutcome.NeedsChoice) return "$outcome"
        val choice = if (random.nextBoolean()) FirstSyncChoice.USE_BACKUP else FirstSyncChoice.MERGE
        wiped = choice == FirstSyncChoice.USE_BACKUP
        return "asked, chose $choice: ${phone.sync(choice)}"
    }

    suspend fun savePlan(phone: StressPhone, daysPerWeek: Int) {
        val profile = phone.profiles.get() ?: PROFILE.also { phone.profiles.save(it) }
        val input = PlanInput(profile.copy(daysPerWeek = daysPerWeek), phone.exercises.observeAll().first(), LocalDate(2026, 10, 1), clock.now())
        phone.plans.saveAsActive(PlanGenerator().generate(input).plan)
    }

    /** The website, signed in to the same account: deletes and edits straight in the backup. */
    private suspend fun websiteAction() {
        val live = remote.live(USER)
        val now = System.currentTimeMillis()
        fun tomb(row: com.viktorolsson.spotter.core.data.sync.RemoteRow) =
            SyncRow(row.tableName, row.syncId, null, maxOf(now, row.updatedAt + 1), maxOf(now, row.updatedAt + 1))
        fun children(table: String, column: String, parents: Set<String>) =
            live.filter { it.tableName == table && it.payload?.get(column)?.jsonPrimitive?.contentOrNull in parents }

        val rows: List<SyncRow>
        val did: String
        when (random.nextInt(4)) {
            0 -> {
                val session = live.filter { it.tableName == "workout_session" && it.payload?.get("endedAt")?.jsonPrimitive?.contentOrNull != null }
                    .randomOrNull(random) ?: return
                val exercises = children("session_exercise", "sessionId", setOf(session.syncId))
                val sets = children("set_entry", "sessionExerciseId", exercises.map { it.syncId }.toSet())
                val records = children("personal_record", "sessionId", setOf(session.syncId))
                rows = (sets + exercises + records + session).map(::tomb)
                did = "deletes workout ${session.syncId}"
            }
            1 -> {
                val plan = live.filter { it.tableName == "plan" }.randomOrNull(random) ?: return
                val days = children("plan_day", "planId", setOf(plan.syncId))
                val exercises = children("plan_exercise", "planDayId", days.map { it.syncId }.toSet())
                rows = (exercises + days + plan).map(::tomb)
                did = "deletes plan ${plan.syncId}"
            }
            2 -> {
                val set = live.filter { it.tableName == "set_entry" }.randomOrNull(random) ?: return
                val weight = WEIGHTS.random(random)
                val payload = JsonObject(set.payload!! + ("weightKg" to JsonPrimitive(weight)))
                rows = listOf(SyncRow(set.tableName, set.syncId, payload, maxOf(now, set.updatedAt + 1)))
                did = "sets weight $weight on set ${set.syncId}"
            }
            else -> {
                val plans = live.filter { it.tableName == "plan" }
                val active = plans.randomOrNull(random) ?: return
                rows = plans.mapNotNull { plan ->
                    val old = plan.payload!!["isActive"]!!.jsonPrimitive
                    val wanted = plan.syncId == active.syncId
                    if ((old.contentOrNull == "1" || old.contentOrNull == "true") == wanted) return@mapNotNull null
                    val value = if (old.isString || old.contentOrNull in listOf("true", "false")) JsonPrimitive(wanted) else JsonPrimitive(if (wanted) 1 else 0)
                    SyncRow(plan.tableName, plan.syncId, JsonObject(plan.payload + ("isActive" to value)), maxOf(now, plan.updatedAt + 1))
                }
                did = "makes plan ${active.syncId} active"
            }
        }
        remote.signedIn = USER
        remote.push(rows)
        rows.filter { it.payload == null }.forEach { deliberate += it.tableName to it.syncId }
        log += "website: $did"
    }

    /** Everyone syncs until nothing moves, then every phone must hold exactly the backup and everything not deleted. */
    private suspend fun settle() {
        repeat(3) { phones.forEach { phoneAction(it, SYNC) } }
        val stored = remote.live(USER).associate { (it.tableName to it.syncId) to it.payload!! }
        // A set edited on one phone after another deleted its workout stays in the backup (the
        // newer edit wins there) with nothing to belong to; phones drop it. Counted, not compared.
        val reachable = stored.filterKeys { key -> parentsOf(key, stored).all { it in stored } }
        orphans += stored.keys - reachable.keys
        // A workout done on a plan day the website deleted keeps the link in the backup; phones
        // unlink it (#52). Compared as unlinked.
        val backup = reachable.mapValues { (key, payload) ->
            val dangling = UNLINKS[key.first].orEmpty().filter { (column, table) ->
                payload[column]?.jsonPrimitive?.contentOrNull?.let { (table to it) !in reachable } == true
            }
            if (dangling.isEmpty()) payload else JsonObject(payload + dangling.keys.associateWith { JsonNull })
        }
        phones.forEach { phone ->
            val rows = phone.rows()
            val missing = backup.keys - rows.keys
            val extra = rows.keys - backup.keys
            val different = (rows.keys intersect backup.keys).filter { rows[it] != backup[it] }
            if (missing.isNotEmpty() || extra.isNotEmpty() || different.isNotEmpty()) {
                throw Violation(
                    "${phone.name} doesn't match the backup after everyone synced: " +
                        "missing ${missing.take(5).map { explain(phone, it, backup, rows) }}, extra ${extra.take(5)}, different " +
                        different.take(3).joinToString { "$it phone ${rows[it]} backup ${backup[it]}" },
                )
            }
            val lost = seen - deliberate - rows.keys
            if (lost.isNotEmpty()) throw Violation("${phone.name} lost rows nobody deleted: ${lost.take(10)}")
            val integrity = phone.strings("PRAGMA integrity_check")
            if (integrity != listOf("ok")) throw Violation("${phone.name}: integrity check says $integrity")
        }
        log += "everyone synced: ${backup.size} rows, all phones match"
    }

    /** Why a row in the backup might not be on [phone]: its parent, and a deletion marker the phone still holds. */
    private fun explain(phone: StressPhone, key: RowKey, backup: Map<RowKey, JsonObject>, rows: Map<RowKey, JsonObject>): String {
        val parent = CASCADES[key.first]?.let { (column, table) -> backup.getValue(key)[column]?.jsonPrimitive?.contentOrNull?.let { table to it } }
        val tombstone = phone.count("SELECT COUNT(*) FROM sync_tombstone WHERE tableName = '${key.first}' AND syncId = '${key.second}'") > 0
        return "$key (parent ${parent?.let { if (it in rows) "on the phone" else if (it in backup) "in the backup only" else "deleted" }}, " +
            "phone has a deletion marker: $tombstone, deleted on purpose: ${key in deliberate})"
    }

    companion object {
        const val MAX_PHONES = 3

        private val CASCADES = mapOf(
            "session_exercise" to ("sessionId" to "workout_session"),
            "set_entry" to ("sessionExerciseId" to "session_exercise"),
            "plan_day" to ("planId" to "plan"),
            "plan_exercise" to ("planDayId" to "plan_day"),
        )

        /** Links that become null when what they point at is deleted (foreign key SET NULL). */
        private val UNLINKS = mapOf(
            "workout_session" to mapOf("planDayId" to "plan_day"),
            "session_exercise" to mapOf("planExerciseId" to "plan_exercise"),
            "personal_record" to mapOf("sessionId" to "workout_session"),
            "scheduled_session" to mapOf("planDayId" to "plan_day", "sessionId" to "workout_session"),
        )

        val PROFILE = UserProfile(
            Sex.FEMALE, 168.0, LocalDate(1992, 3, 14), 64.0, WeightUnit.KG, ExperienceLevel.INTERMEDIATE, Goal.HYPERTROPHY,
            4, 60, Equipment.entries.toSet(), emptySet(), emptySet(),
        )

        val WEIGHTS = listOf(0.0, 2.5, 20.0, 47.5, 62.5, 80.0, 100.0, 142.5, 300.0)
        private val REPS = listOf(null, 0, 1, 5, 8, 12, 30)
        private val NOTES = listOf(null, "", "Felt strong", "Ont i axeln, tog det lugnt", "🔥 PR!", "x".repeat(500))

        private const val ACTIVE_SETS =
            "SELECT s.id FROM set_entry s JOIN session_exercise e ON e.id = s.sessionExerciseId " +
                "JOIN workout_session w ON w.id = e.sessionId WHERE w.endedAt IS NULL ORDER BY s.id"
        private const val ACTIVE_EXERCISES =
            "SELECT e.id FROM session_exercise e JOIN workout_session w ON w.id = e.sessionId WHERE w.endedAt IS NULL ORDER BY e.position"

        private fun StressRun.activeSession(phone: StressPhone) =
            phone.ids("SELECT id FROM workout_session WHERE endedAt IS NULL ORDER BY id").randomOrNull(random)

        private fun StressRun.finishedSession(phone: StressPhone) =
            phone.ids("SELECT id FROM workout_session WHERE endedAt IS NOT NULL ORDER BY id").randomOrNull(random)

        /** A set in the workout in progress, or now and then one in history (edited afterwards). */
        private fun StressRun.someSet(phone: StressPhone) =
            (if (random.nextInt(4) > 0) phone.ids(ACTIVE_SETS) else phone.ids("SELECT id FROM set_entry ORDER BY id")).randomOrNull(random)

        private fun StressRun.someExercise(phone: StressPhone) = phone.ids(ACTIVE_EXERCISES).randomOrNull(random)

        private fun StressRun.exerciseIds(phone: StressPhone, n: Int) =
            phone.strings("SELECT id FROM exercise ORDER BY id").shuffled(random).take(n)

        private fun <T> StressRun.oneOf(values: List<T>) = values.random(random)

        val SYNC = Action("sync", 10, run = { phone -> syncAnswering(phone) })

        val ACTIONS = listOf(
            SYNC,
            Action("start empty workout", 3) { phone -> "#${phone.workouts.startEmptyWorkout()}" },
            Action("start plan day", 4) { phone ->
                val day = phone.ids("SELECT d.id FROM plan_day d JOIN plan p ON p.id = d.planId WHERE p.isActive = 1 ORDER BY d.id")
                    .randomOrNull(random) ?: return@Action null
                "day #$day -> #${phone.workouts.startPlannedWorkout(day)}"
            },
            Action("repeat workout", 1) { phone ->
                val id = finishedSession(phone) ?: return@Action null
                "#$id -> #${phone.workouts.repeatWorkout(id)}"
            },
            Action("add exercises", 4) { phone ->
                val session = activeSession(phone) ?: return@Action null
                val ids = exerciseIds(phone, random.nextInt(1, 3))
                phone.workouts.addExercises(session, ids)
                "$ids to #$session"
            },
            Action("swap exercise", 2) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                val to = exerciseIds(phone, 1).single()
                val inPlan = random.nextBoolean()
                phone.workouts.swapExercise(entry, to, inPlan)
                "#$entry to $to${if (inPlan) " (and in the plan)" else ""}"
            },
            Action("remove exercise", 1, deletes = setOf("session_exercise", "set_entry")) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                phone.workouts.removeExercise(entry)
                "#$entry"
            },
            Action("move exercise", 1) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                val offset = oneOf(listOf(-2, -1, 1, 2))
                phone.workouts.moveExercise(entry, offset)
                "#$entry by $offset"
            },
            Action("superset with next", 1) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                phone.workouts.supersetWithNext(entry)
                "#$entry"
            },
            Action("remove from superset", 1) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                phone.workouts.removeFromSuperset(entry)
                "#$entry"
            },
            Action("exercise note and rest", 1) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                val note = oneOf(NOTES)
                val rest = oneOf(listOf(0, 45, 90, 180, 900))
                phone.workouts.setExerciseNotes(entry, note)
                phone.workouts.setExerciseRest(entry, rest)
                "#$entry note ${note?.take(20)}, rest $rest s"
            },
            Action("add set", 4) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                phone.workouts.addSet(entry)
                "to #$entry"
            },
            Action("add warm-up set", 1) { phone ->
                val entry = someExercise(phone) ?: return@Action null
                "to #$entry: ${phone.workouts.addWarmUpSet(entry, extended = random.nextBoolean())}"
            },
            Action("delete set", 2, deletes = setOf("set_entry")) { phone ->
                val set = someSet(phone) ?: return@Action null
                phone.workouts.deleteSet(set)
                "#$set"
            },
            Action("type weight", 8) { phone ->
                val set = someSet(phone) ?: return@Action null
                val kg = oneOf(WEIGHTS + null)
                phone.workouts.setSetWeight(set, kg)
                "#$set = $kg kg"
            },
            Action("type reps", 8) { phone ->
                val set = someSet(phone) ?: return@Action null
                val reps = oneOf(REPS)
                phone.workouts.setSetReps(set, reps)
                "#$set = $reps reps"
            },
            Action("type effort", 2) { phone ->
                val set = someSet(phone) ?: return@Action null
                val rir = oneOf(listOf(null, 0, 1, 2, 3, 5))
                phone.workouts.setSetRir(set, rir)
                "#$set = RIR $rir"
            },
            Action("set type and note", 1) { phone ->
                val set = someSet(phone) ?: return@Action null
                val type = oneOf(SetType.entries)
                phone.workouts.setSetType(set, type)
                phone.workouts.setSetNotes(set, oneOf(NOTES))
                "#$set $type"
            },
            Action("tick set", 10) { phone ->
                val set = someSet(phone) ?: return@Action null
                "#$set done: ${phone.workouts.toggleSetCompleted(set)}"
            },
            Action("workout note", 1) { phone ->
                val session = activeSession(phone) ?: finishedSession(phone) ?: return@Action null
                phone.workouts.setSessionNotes(session, oneOf(NOTES))
                "#$session"
            },
            Action("finish workout", 4, deletes = setOf("session_exercise", "set_entry")) { phone ->
                val session = activeSession(phone) ?: return@Action null
                clock.advanceSeconds(random.nextLong(600, 5400))
                phone.workouts.finishWorkout(session)
                "#$session"
            },
            Action("discard workout", 1, deletes = setOf("workout_session", "session_exercise", "set_entry")) { phone ->
                val session = activeSession(phone) ?: return@Action null
                phone.workouts.discardWorkout(session)
                "#$session"
            },
            Action("delete finished workout", 1, deletes = setOf("workout_session", "session_exercise", "set_entry", "personal_record")) { phone ->
                val session = finishedSession(phone) ?: return@Action null
                "#$session: ${phone.workouts.deleteFinishedWorkout(session)}"
            },
            Action("create custom exercise", 1) { phone ->
                val id = phone.exercises.createCustom(
                    "Custom ${random.nextInt(1000)}", oneOf(Muscle.entries), oneOf(MovementPattern.entries), listOf(oneOf(Equipment.entries)),
                )
                id
            },
            Action("log body weight", 2, deletes = setOf("body_weight_entry")) { phone ->
                val date = LocalDate(2026, 10, 1).plus(random.nextInt(14), DateTimeUnit.DAY)
                val kg = 55.0 + random.nextInt(400) / 10.0
                phone.bodyWeight.log(date, kg)
                "$date $kg kg"
            },
            Action("change units", 1) { phone ->
                val unit = oneOf(WeightUnit.entries)
                phone.prefs.setWeightUnit(unit)
                "$unit"
            },
            Action("make a new plan", 1) { phone ->
                val days = random.nextInt(2, 6)
                savePlan(phone, days)
                "$days days a week"
            },
            Action("refresh recommendations", 2, deletes = setOf("recommendation")) { phone ->
                phone.recommendations.refresh()
                "${phone.recommendations.observeActive().first().size} cards"
            },
            Action("apply recommendation", 1, deletes = setOf("recommendation")) { phone ->
                val card = phone.recommendations.observeActive().first().randomOrNull(random) ?: return@Action null
                phone.recommendations.apply(card.id)
                "${card.type} #${card.id}"
            },
            Action("dismiss recommendation", 1, deletes = setOf("recommendation")) { phone ->
                val card = phone.recommendations.observeActive().first().randomOrNull(random) ?: return@Action null
                phone.recommendations.dismiss(card.id)
                "${card.type} #${card.id}"
            },
            Action("kill and reopen the app", 1) { phone ->
                phone.restart()
                if (phone.strings("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync%'").size < SyncSchema.tables.size * 3) {
                    throw Violation("${phone.name}: sync triggers missing after a restart")
                }
                "ok"
            },
        )
    }
}
