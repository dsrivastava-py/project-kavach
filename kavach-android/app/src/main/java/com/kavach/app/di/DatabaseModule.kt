package com.kavach.app.di

import android.content.Context
import androidx.room.Room
import com.kavach.app.data.local.database.KavachDatabase
import com.kavach.app.data.local.dao.IncidentCacheDao
import com.kavach.app.data.local.dao.PlanCacheDao
import com.kavach.app.data.local.dao.SignalEventQueueDao
import com.kavach.app.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room database and all DAO instances.
 *
 * The database uses fallbackToDestructiveMigration only in debug builds —
 * production should use explicit Alembic-style migrations.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KavachDatabase =
        Room.databaseBuilder(
            context,
            KavachDatabase::class.java,
            Constants.DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideIncidentCacheDao(db: KavachDatabase): IncidentCacheDao =
        db.incidentCacheDao()

    @Provides
    fun providePlanCacheDao(db: KavachDatabase): PlanCacheDao =
        db.planCacheDao()

    @Provides
    fun provideSignalEventQueueDao(db: KavachDatabase): SignalEventQueueDao =
        db.signalEventQueueDao()
}
