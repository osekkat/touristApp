package com.marrakechguide.di

import android.content.Context
import com.marrakechguide.core.database.ContentDatabase
import com.marrakechguide.core.database.UserDatabase
import com.marrakechguide.core.repository.ActivityRepository
import com.marrakechguide.core.repository.ActivityRepositoryImpl
import com.marrakechguide.core.repository.CultureRepository
import com.marrakechguide.core.repository.CultureRepositoryImpl
import com.marrakechguide.core.repository.EventRepository
import com.marrakechguide.core.repository.EventRepositoryImpl
import com.marrakechguide.core.repository.FavoritesRepository
import com.marrakechguide.core.repository.FavoritesRepositoryImpl
import com.marrakechguide.core.repository.ItineraryRepository
import com.marrakechguide.core.repository.ItineraryRepositoryImpl
import com.marrakechguide.core.repository.PhraseRepository
import com.marrakechguide.core.repository.PhraseRepositoryImpl
import com.marrakechguide.core.repository.PlaceRepository
import com.marrakechguide.core.repository.PlaceRepositoryImpl
import com.marrakechguide.core.repository.PriceCardRepository
import com.marrakechguide.core.repository.PriceCardRepositoryImpl
import com.marrakechguide.core.repository.RecentsRepository
import com.marrakechguide.core.repository.RecentsRepositoryImpl
import com.marrakechguide.core.repository.SavedPlansRepository
import com.marrakechguide.core.repository.SavedPlansRepositoryImpl
import com.marrakechguide.core.repository.TipRepository
import com.marrakechguide.core.repository.TipRepositoryImpl
import com.marrakechguide.core.repository.UserSettingsRepository
import com.marrakechguide.core.repository.UserSettingsRepositoryImpl
import com.marrakechguide.core.service.DownloadService
import com.marrakechguide.core.service.DownloadServiceImpl
import com.marrakechguide.core.service.HeadingService
import com.marrakechguide.core.service.HeadingServiceImpl
import com.marrakechguide.core.service.LocationService
import com.marrakechguide.core.service.LocationServiceImpl
import dagger.Provides
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing service, repository, and database dependencies.
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

    @Binds
    @Singleton
    abstract fun bindDownloadService(impl: DownloadServiceImpl): DownloadService

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindPriceCardRepository(impl: PriceCardRepositoryImpl): PriceCardRepository

    @Binds
    @Singleton
    abstract fun bindPhraseRepository(impl: PhraseRepositoryImpl): PhraseRepository

    @Binds
    @Singleton
    abstract fun bindItineraryRepository(impl: ItineraryRepositoryImpl): ItineraryRepository

    @Binds
    @Singleton
    abstract fun bindTipRepository(impl: TipRepositoryImpl): TipRepository

    @Binds
    @Singleton
    abstract fun bindCultureRepository(impl: CultureRepositoryImpl): CultureRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindRecentsRepository(impl: RecentsRepositoryImpl): RecentsRepository

    @Binds
    @Singleton
    abstract fun bindUserSettingsRepository(impl: UserSettingsRepositoryImpl): UserSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSavedPlansRepository(impl: SavedPlansRepositoryImpl): SavedPlansRepository

    companion object {
        @Provides
        @Singleton
        fun provideContentDatabase(@ApplicationContext context: Context): ContentDatabase {
            return ContentDatabase.getInstance(context)
        }

        @Provides
        @Singleton
        fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase {
            return UserDatabase.getInstance(context)
        }
    }
}
