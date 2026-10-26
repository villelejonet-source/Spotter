package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.toEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single user profile. Its `units` field is the synced source of truth for the unit
 * setting; change it through [UserPreferencesRepository.setWeightUnit], which keeps both in step.
 */
class UserProfileRepository(
    private val dao: UserProfileDao,
) {
    fun observe(): Flow<UserProfile?> = dao.observe().map { it?.toModel() }

    suspend fun get(): UserProfile? = dao.get()?.toModel()

    suspend fun save(profile: UserProfile) = dao.upsert(profile.toEntity())
}
