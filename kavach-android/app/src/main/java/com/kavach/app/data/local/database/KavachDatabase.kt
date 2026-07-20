package com.kavach.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kavach.app.data.local.dao.IncidentCacheDao
import com.kavach.app.data.local.dao.PlanCacheDao
import com.kavach.app.data.local.dao.SignalEventQueueDao
import com.kavach.app.data.local.entity.IncidentEntity
import com.kavach.app.data.local.entity.PlanEntity
import com.kavach.app.data.local.entity.SignalEventQueueEntity
import com.kavach.app.utils.Constants

/**
 * Room database — single source of truth for cached and queued data.
 *
 * Increment [version] and provide a [Migration] whenever the schema changes.
 * [fallbackToDestructiveMigration] is only acceptable in debug builds —
 * the DatabaseModule enforces this.
 */
@Database(
    entities = [
        IncidentEntity::class,
        PlanEntity::class,
        SignalEventQueueEntity::class,
    ],
    version = Constants.DB_VERSION,
    exportSchema = true,
)
abstract class KavachDatabase : RoomDatabase() {
    abstract fun incidentCacheDao(): IncidentCacheDao
    abstract fun planCacheDao(): PlanCacheDao
    abstract fun signalEventQueueDao(): SignalEventQueueDao
}
