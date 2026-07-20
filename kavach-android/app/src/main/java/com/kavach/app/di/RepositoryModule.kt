package com.kavach.app.di

import com.kavach.app.data.repository.*
import com.kavach.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds concrete repository implementations to their domain interfaces.
 *
 * ViewModels and UseCases depend on interfaces only — the DI graph
 * wires in the real implementations at runtime.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindIncidentRepository(impl: IncidentRepositoryImpl): IncidentRepository

    @Binds @Singleton
    abstract fun bindSignalRepository(impl: SignalRepositoryImpl): SignalRepository

    @Binds @Singleton
    abstract fun bindDeepCheckRepository(impl: DeepCheckRepositoryImpl): DeepCheckRepository

    @Binds @Singleton
    abstract fun bindBillingRepository(impl: BillingRepositoryImpl): BillingRepository

    @Binds @Singleton
    abstract fun bindGraphRepository(impl: GraphRepositoryImpl): GraphRepository

    @Binds @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository
}
