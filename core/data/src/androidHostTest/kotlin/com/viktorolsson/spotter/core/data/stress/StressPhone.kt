package com.viktorolsson.spotter.core.data.stress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.MutableClock
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.data.sql
import com.viktorolsson.spotter.core.data.sync.FakeRemote
import com.viktorolsson.spotter.core.data.sync.FirstSyncChoice
import com.viktorolsson.spotter.core.data.sync.LocalSyncStore
import com.viktorolsson.spotter.core.data.sync.SyncCursorStore
import com.viktorolsson.spotter.core.data.sync.SyncEngine
import com.viktorolsson.spotter.core.data.sync.SyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.time.Clock

/** A row as it travels: its table and global sync id. */
typealias RowKey = Pair<String, String>

/**
 * One phone in the stress test: a database file (so it can be closed and reopened, as when
 * the app is killed), preferences and a sync cursor that outlive a restart, and the same
 * repositories and sync engine the app builds in DataGraph.
 */
class StressPhone(val name: String, private val remote: FakeRemote, val clock: MutableClock) {
    private val dir = File.createTempFile("stress-$name", "").apply { delete(); mkdirs(); deleteOnExit() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefsStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
    private val cursorStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "sync.preferences_pb") }
    private val cursors = SyncCursorStore(cursorStore)

    lateinit var db: SpotterDatabase
        private set
    lateinit var prefs: UserPreferencesRepository
        private set
    lateinit var workouts: WorkoutRepository
        private set
    lateinit var plans: PlanRepository
        private set
    lateinit var profiles: UserProfileRepository
        private set
    lateinit var exercises: ExerciseRepository
        private set
    lateinit var bodyWeight: BodyWeightRepository
        private set
    lateinit var recommendations: RecommendationRepository
        private set
    private lateinit var engine: SyncEngine

    /** Opens the database file, as the app does on start. The catalog is seeded on the first open only. */
    suspend fun open(firstTime: Boolean = false) {
        db = SpotterDatabase.configure(
            Room.databaseBuilder<SpotterDatabase>(ApplicationProvider.getApplicationContext<Context>(), File(dir, "spotter.db").absolutePath),
            AndroidSQLiteDriver(),
        ).build()
        prefs = UserPreferencesRepository(prefsStore, db.userProfileDao())
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), prefs, clock)
        plans = PlanRepository(db, db.planDao())
        profiles = UserProfileRepository(db.userProfileDao())
        exercises = ExerciseRepository(db.exerciseDao())
        bodyWeight = BodyWeightRepository(db, db.userProfileDao())
        recommendations = RecommendationRepository(
            db, db.recommendationDao(), db.workoutDao(), db.planDao(), db.exerciseDao(), profiles,
            HistoryRepository(db.workoutDao(), clock), prefs, clock,
        )
        engine = SyncEngine(LocalSyncStore(db), remote, cursors, Clock.System)
        if (firstTime) db.exerciseDao().upsertAll(catalog)
    }

    /** The app being killed and started again: everything not yet in the database file is gone. */
    suspend fun restart() {
        db.close()
        open()
    }

    fun close() {
        db.close()
        scope.cancel()
        dir.deleteRecursively()
    }

    suspend fun sync(choice: FirstSyncChoice? = null): SyncOutcome {
        remote.signedIn = USER
        return engine.sync(USER, choice, fromLink = false)
    }

    /** Every synced row on this phone, as it would be pushed: foreign keys as sync ids. */
    suspend fun rows(): Map<RowKey, JsonObject> =
        LocalSyncStore(db).changesSince(0).mapNotNull { row -> row.payload?.let { (row.tableName to row.syncId) to it } }.toMap()

    fun ids(sql: String): List<Long> = db.sql.query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    fun strings(sql: String): List<String> = db.sql.query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)!!) } }

    fun count(sql: String): Int = db.sql.query(sql).use { it.moveToFirst(); it.getInt(0) }

    companion object {
        const val USER = "user-1"

        private val catalog by lazy {
            parseExerciseSeed(File("catalog/exercises.json").readText()).exercises.map(SeedExercise::toEntity)
        }
    }
}
