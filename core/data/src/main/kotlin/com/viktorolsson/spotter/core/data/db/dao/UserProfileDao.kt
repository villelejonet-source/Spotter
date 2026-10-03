package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.viktorolsson.spotter.core.data.db.entity.BodyWeightEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    fun observe(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM body_weight_entry ORDER BY date")
    fun observeBodyWeight(): Flow<List<BodyWeightEntryEntity>>

    @Upsert
    suspend fun upsertBodyWeight(entry: BodyWeightEntryEntity)
}
