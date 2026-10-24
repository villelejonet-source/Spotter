package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.toEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single user profile. Its `units` field is the synced source of truth for the unit
 * setting; change it through [UserPreferencesRepository.setWeightUnit], which keeps both in step.
 */
@Singleton
class UserProfileRepository @Inject constructor(
    private val dao: UserProfileDao,
) {
    fun observe(): Flow<UserProfile?> = dao.observe().map { it?.toModel() }

    suspend fun get(): UserProfile? = dao.get()?.toModel()

    suspend fun save(profile: UserProfile) = dao.upsert(profile.toEntity())
}
