package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.model.MovementPattern
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercise")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE movementPattern = :pattern ORDER BY name COLLATE NOCASE")
    suspend fun getByPattern(pattern: MovementPattern): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE")
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)
}
