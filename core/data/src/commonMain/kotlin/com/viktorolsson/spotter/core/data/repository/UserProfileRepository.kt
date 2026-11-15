package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.toEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The single user profile. Its `units` field is the synced source of truth for the unit
 * setting; change it through [UserPreferencesRepository.setWeightUnit], which keeps both in step.
 */
class UserProfileRepository(
    private val dao: UserProfileDao,
) {
    fun observe(): Flow<UserProfile?> = dao.observe().map { it?.toModel() }

    suspend fun get(): UserProfile? = dao.get()?.toModel()

    /**
     * The profile as it was on [date]: with the body weight logged that day or last before it.
     * The profile's own weight when nothing was logged by then.
     */
    suspend fun on(date: LocalDate): UserProfile? {
        val profile = get() ?: return null
        return dao.getBodyWeightOnOrBefore(date)?.let { profile.copy(bodyWeightKg = it.weightKg) } ?: profile
    }

    suspend fun save(profile: UserProfile) = dao.upsert(profile.toEntity())
}
