package com.viktorolsson.spotter.core.data.repository

import androidx.room.withTransaction
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.entity.BodyWeightEntryEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.engine.BodyWeightTrend
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyWeightRepository @Inject constructor(
    private val db: SpotterDatabase,
    private val dao: UserProfileDao,
) {
    fun observeTrend(): Flow<List<BodyWeightPoint>> =
        dao.observeBodyWeight().map { entries -> BodyWeightTrend.points(entries.map { it.toModel() }) }

    /** One entry per day (logging again replaces it); the latest also updates the profile. */
    suspend fun log(date: LocalDate, weightKg: Double) = db.withTransaction {
        val existing = dao.observeBodyWeight().first().firstOrNull { it.date == date }
        dao.upsertBodyWeight(BodyWeightEntryEntity(id = existing?.id ?: 0, date = date, weightKg = weightKg))
        val latest = dao.observeBodyWeight().first().maxByOrNull { it.date }
        val profile = dao.observe().first()
        if (profile != null && latest != null) dao.upsert(profile.copy(bodyWeightKg = latest.weightKg))
    }
}
