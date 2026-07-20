package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.PlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cached billing plans.
 * Plans rarely change — cache-first strategy suits them well.
 */
@Dao
interface PlanCacheDao {

    @Query("SELECT * FROM plans ORDER BY price_inr ASC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY price_inr ASC")
    suspend fun getAll(): List<PlanEntity>

    @Upsert
    suspend fun upsertAll(plans: List<PlanEntity>)

    @Query("DELETE FROM plans")
    suspend fun clearAll()
}
