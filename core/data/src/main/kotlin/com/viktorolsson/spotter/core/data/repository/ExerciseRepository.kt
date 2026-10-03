package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> = exerciseDao.observeAll().map { it.map(ExerciseEntity::toModel) }

    fun observeCount(): Flow<Int> = exerciseDao.observeCount()

    suspend fun getById(id: String): Exercise? = exerciseDao.getById(id)?.toModel()
}
