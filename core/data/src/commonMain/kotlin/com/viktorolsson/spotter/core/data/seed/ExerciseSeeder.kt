package com.viktorolsson.spotter.core.data.seed

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The bundled `exercises.json` (`core/data/catalog`): an asset on Android, a bundle resource on
 * iOS. Only read when the installed catalog is out of date.
 */
fun interface CatalogSource {
    fun read(): String
}

/**
 * Loads the bundled exercise library into Room. Runs on every launch, but on an install
 * that already has [EXERCISE_CATALOG_VERSION] it returns after one preference read: the
 * catalog is neither opened nor parsed. Seed rows are upserted by id, so custom exercises
 * and history references are untouched.
 */
class ExerciseSeeder(
    private val catalog: CatalogSource,
    private val exerciseDao: ExerciseDao,
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val applied = dataStore.data.first()[SEED_VERSION_KEY] ?: 0
        if (applied >= EXERCISE_CATALOG_VERSION) return@withContext

        val seed = parseExerciseSeed(catalog.read())
        if (seed.version <= applied) return@withContext
        exerciseDao.upsertAll(seed.exercises.map(SeedExercise::toEntity))
        dataStore.edit { it[SEED_VERSION_KEY] = seed.version }
    }

    private companion object {
        val SEED_VERSION_KEY = intPreferencesKey("exercise_seed_version")
    }
}
