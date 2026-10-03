package com.viktorolsson.spotter.core.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled exercise library into Room. Runs on every launch but only
 * writes when the asset's version is newer than the last one applied. Seed rows
 * are upserted by id, so custom exercises and history references are untouched.
 */
@Singleton
class ExerciseSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val seed = context.assets.open(ASSET_NAME).bufferedReader().use { parseExerciseSeed(it.readText()) }
        val applied = dataStore.data.first()[SEED_VERSION_KEY] ?: 0
        if (seed.version <= applied) return@withContext

        exerciseDao.upsertAll(seed.exercises.map(SeedExercise::toEntity))
        dataStore.edit { it[SEED_VERSION_KEY] = seed.version }
    }

    private companion object {
        const val ASSET_NAME = "exercises.json"
        val SEED_VERSION_KEY = intPreferencesKey("exercise_seed_version")
    }
}
