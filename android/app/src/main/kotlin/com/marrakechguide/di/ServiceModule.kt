package com.marrakechguide.di

import com.marrakechguide.core.service.HeadingService
import com.marrakechguide.core.service.HeadingServiceImpl
import com.marrakechguide.core.service.LocationService
import com.marrakechguide.core.service.LocationServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing service layer dependencies.
 *
 * Services are application-scoped singletons to maintain consistent state
 * (e.g., location updates, sensor listeners) across the app lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    /**
     * Provides the LocationService implementation.
     *
     * LocationServiceImpl wraps FusedLocationProviderClient with:
     * - Battery-safe balanced accuracy by default
     * - Automatic timeout after 10 minutes
     * - Permission status tracking
     */
    @Binds
    @Singleton
    abstract fun bindLocationService(impl: LocationServiceImpl): LocationService

    /**
     * Provides the HeadingService implementation.
     *
     * HeadingServiceImpl wraps SensorManager with:
     * - Rotation vector sensor (preferred) or accelerometer/magnetometer fallback
     * - UI update throttling (20Hz max)
     * - Heading confidence indicators
     */
    @Binds
    @Singleton
    abstract fun bindHeadingService(impl: HeadingServiceImpl): HeadingService
}
