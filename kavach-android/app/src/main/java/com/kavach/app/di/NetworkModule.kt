package com.kavach.app.di

import com.kavach.app.BuildConfig
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.api.SignalApiService
import com.kavach.app.data.remote.interceptor.AuthInterceptor
import com.kavach.app.data.remote.interceptor.DeviceKeyInterceptor
import com.kavach.app.data.remote.interceptor.TokenExpiryInterceptor
import com.kavach.app.utils.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceKeyOkHttpClient

/**
 * Provides Retrofit, OkHttp, and Moshi instances.
 *
 * Two separate OkHttp clients:
 * 1. [AuthOkHttpClient]      → Bearer JWT interceptor (KavachApiService)
 * 2. [DeviceKeyOkHttpClient] → X-API-Key interceptor   (SignalApiService)
 *
 * Logging interceptor is only added in debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun buildLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    @AuthOkHttpClient
    fun provideAuthOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenExpiryInterceptor: TokenExpiryInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(tokenExpiryInterceptor)
        .addInterceptor(buildLoggingInterceptor())
        .build()

    @Provides
    @Singleton
    @DeviceKeyOkHttpClient
    fun provideDeviceKeyOkHttpClient(
        deviceKeyInterceptor: DeviceKeyInterceptor,
        tokenExpiryInterceptor: TokenExpiryInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(deviceKeyInterceptor)
        .addInterceptor(buildLoggingInterceptor())
        .build()

    @Provides
    @Singleton
    fun provideKavachApiService(
        @AuthOkHttpClient okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): KavachApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(KavachApiService::class.java)

    @Provides
    @Singleton
    fun provideSignalApiService(
        @DeviceKeyOkHttpClient okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): SignalApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SignalApiService::class.java)
}
