package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** A preferences repository backed by a fresh temp file, so tests never share state. */
fun testPreferences(): UserPreferencesRepository {
    val file = File.createTempFile("prefs", ".preferences_pb").apply { delete(); deleteOnExit() }
    val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
    return UserPreferencesRepository(store)
}
