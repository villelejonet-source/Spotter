package com.viktorolsson.spotter.core.data.repository

import androidx.room.withTransaction
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.entity.BodyWeightEntryEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.engine.BodyWeightTrend
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyWeightRepository @Inject constructor(
    private val db: SpotterDatabase,
    private val dao: UserProfileDao,
) {
    fun observeTrend(): Flow<List<BodyWeightPoint>> =
        dao.observeBodyWeight().map { entries -> BodyWeightTrend.points(entries.map { it.toModel() }) }.flowOn(Dispatchers.Default)

    /** One entry per day (logging again replaces it); the latest also updates the profile. */
    suspend fun log(date: LocalDate, weightKg: Double) = db.withTransaction {
        val existing = dao.getBodyWeight(date)
        dao.upsertBodyWeight(BodyWeightEntryEntity(id = existing?.id ?: 0, date = date, weightKg = weightKg))
        val latest = dao.getLatestBodyWeight()
        val profile = dao.get()
        if (profile != null && latest != null && profile.bodyWeightKg != latest.weightKg) dao.upsert(profile.copy(bodyWeightKg = latest.weightKg))
    }
}
