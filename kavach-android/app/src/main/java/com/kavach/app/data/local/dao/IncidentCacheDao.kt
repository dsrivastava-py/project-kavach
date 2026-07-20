package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cached incidents.
 * Queries return [Flow] so the UI auto-updates when the cache is refreshed.
 */
@Dao
interface IncidentCacheDao {

    @Query("SELECT * FROM incidents ORDER BY started_at DESC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE status = 'open' OR status LIKE 'graduated_%' ORDER BY risk_score DESC")
    fun observeActive(): Flow<List<IncidentEntity>>

    @Upsert
    suspend fun upsertAll(incidents: List<IncidentEntity>)

    @Upsert
    suspend fun upsert(incident: IncidentEntity)

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM incidents")
    suspend fun clearAll()

    /** Remove stale cache entries older than [thresholdMs]. */
    @Query("DELETE FROM incidents WHERE cached_at < :thresholdMs")
    suspend fun evictStale(thresholdMs: Long)
}
