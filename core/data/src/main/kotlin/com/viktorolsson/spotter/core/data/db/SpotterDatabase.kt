package com.viktorolsson.spotter.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
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
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity

/**
 * Schema changes must bump [version] and add a migration (auto or manual); the
 * exported JSON in `core/data/schemas` is what migration tests run against.
 */
@Database(
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // session_exercise.restSeconds
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
    ],
)
@TypeConverters(Converters::class)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "spotter.db"
    }
}
