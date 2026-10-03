package com.viktorolsson.spotter.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.dao.RecommendationDao
import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.BodyWeightEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.ExerciseSimilarityEntity
import com.viktorolsson.spotter.core.data.db.entity.PersonalRecordEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanDayEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.RecommendationEntity
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.SyncStateEntity
import com.viktorolsson.spotter.core.data.db.entity.SyncTombstoneEntity
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity

/**
 * Schema changes must bump [version] and add a migration (auto or manual); the
 * exported JSON in `core/data/schemas` is what migration tests run against.
 */
@Database(
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // session_exercise.restSeconds
        AutoMigration(from = 2, to = 3), // plan_exercise.startingWeightKg, session_exercise.planExerciseId
        AutoMigration(from = 3, to = 4), // session_exercise.progressionReason
        AutoMigration(from = 4, to = 5), // workout_session.isDeload, recommendation.resolvedAt/dedupKey
    ],
    entities = [
        UserProfileEntity::class,
        BodyWeightEntryEntity::class,
        ExerciseEntity::class,
        ExerciseSimilarityEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        PlanExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
        PersonalRecordEntity::class,
        RecommendationEntity::class,
        SyncTombstoneEntity::class,
        SyncStateEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun planDao(): PlanDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        const val NAME = "spotter.db"

        /** Migrations and sync triggers; used for the app database and in tests. */
        fun <T : SpotterDatabase> configure(builder: Builder<T>): Builder<T> = builder
            .addMigrations(SyncSchema.MIGRATION_5_6)
            .addCallback(
                object : Callback() {
                    // Idempotent: also restores triggers if they're ever missing.
                    override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.createTriggers(db)
                },
            )
    }
}
