package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.viktorolsson.spotter.core.data.db.entity.RecommendationEntity
import com.viktorolsson.spotter.core.model.RecommendationStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RecommendationDao {
    @Insert
    suspend fun insert(recommendations: List<RecommendationEntity>)

    @Query("SELECT * FROM recommendation WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendation")
    suspend fun getAll(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendation WHERE id = :id")
    suspend fun get(id: Long): RecommendationEntity?

    @Query("UPDATE recommendation SET status = :status, resolvedAt = :at WHERE id = :id")
    suspend fun resolve(id: Long, status: RecommendationStatus, at: Instant)

    @Query("DELETE FROM recommendation WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
