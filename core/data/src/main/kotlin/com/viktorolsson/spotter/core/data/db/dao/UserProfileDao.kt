package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.viktorolsson.spotter.core.data.db.entity.BodyWeightEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM body_weight_entry ORDER BY date")
    fun observeBodyWeight(): Flow<List<BodyWeightEntryEntity>>

    @Query("SELECT * FROM body_weight_entry WHERE date = :date")
    suspend fun getBodyWeight(date: LocalDate): BodyWeightEntryEntity?

    @Query("SELECT * FROM body_weight_entry ORDER BY date DESC LIMIT 1")
    suspend fun getLatestBodyWeight(): BodyWeightEntryEntity?

    @Upsert
    suspend fun upsertBodyWeight(entry: BodyWeightEntryEntity)
}
