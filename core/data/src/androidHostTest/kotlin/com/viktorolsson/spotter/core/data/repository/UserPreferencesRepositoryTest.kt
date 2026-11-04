package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The levels switch moving from this phone's preferences into the synced profile. */
@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var profiles: UserProfileRepository

    private val profile = UserProfile(
        Sex.FEMALE, 165.0, LocalDate(1992, 5, 1), 60.0, WeightUnit.KG, ExperienceLevel.NEW, Goal.STRENGTH,
        3, 60, Equipment.entries.toSet(), emptySet(), emptySet(),
    )

    @Before
    fun setUp() {
        db = testDatabase()
        store = PreferenceDataStoreFactory.create { File.createTempFile("prefs", ".preferences_pb").apply { delete(); deleteOnExit() } }
        prefs = UserPreferencesRepository(store, db.userProfileDao())
        profiles = UserProfileRepository(db.userProfileDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aSwitchTurnedOffBeforeTheUpdateGoesIntoTheProfileOnce() = runTest {
        profiles.save(profile)
        // As 1.3.0 stored it: the preference only, the profile unaware.
        store.edit { it[booleanPreferencesKey("show_levels")] = false }

        prefs.reconcileShowLevels()
        assertEquals(false, profiles.get()!!.showLevels)

        // Turned back on elsewhere and synced: from now on the profile wins.
        profiles.save(profiles.get()!!.copy(showLevels = true))
        prefs.reconcileShowLevels()
        assertEquals(true, prefs.preferences.first().showLevels)
    }

    @Test
    fun aPhoneThatNeverTouchedTheSwitchTakesTheProfiles() = runTest {
        profiles.save(profile.copy(showLevels = false)) // restored from another phone

        prefs.reconcileShowLevels()
        assertEquals(false, profiles.get()!!.showLevels)
        assertEquals(false, prefs.preferences.first().showLevels)
    }

    @Test
    fun withoutAProfileTheSwitchStaysOnThePhone() = runTest {
        prefs.setShowLevels(false)
        prefs.reconcileShowLevels()
        assertEquals(false, prefs.preferences.first().showLevels)

        profiles.save(profile) // onboarding, later, makes a profile that shows them
        prefs.reconcileShowLevels()
        assertEquals(false, profiles.get()!!.showLevels)
        assertEquals(false, prefs.preferences.first().showLevels)
    }
}
