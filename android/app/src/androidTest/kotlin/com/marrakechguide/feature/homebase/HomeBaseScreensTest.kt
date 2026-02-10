package com.marrakechguide.feature.homebase

import android.location.Location
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marrakechguide.core.repository.ExchangeRate
import com.marrakechguide.core.repository.HomeBase
import com.marrakechguide.core.repository.SettingKey
import com.marrakechguide.core.repository.UserSettingsRepository
import com.marrakechguide.core.service.HeadingConfidence
import com.marrakechguide.core.service.HeadingService
import com.marrakechguide.core.service.LocationService
import com.marrakechguide.core.service.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeBaseScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun goHomeScreen_authorizedPermission_showsCompassActions() {
        val homeBase = sampleHomeBase()
        val viewModel = buildViewModel(
            homeBase = homeBase,
            permissionStatus = PermissionStatus.AUTHORIZED
        )

        composeTestRule.setContent {
            GoHomeScreen(
                onNavigateToSetup = {},
                onNavigateToTaxiCard = {},
                onRequestLocationPermission = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.isLoading.value
        }

        composeTestRule.onNodeWithText(homeBase.name).assertIsDisplayed()
        composeTestRule.onNodeWithText("Refresh Location").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show to Taxi Driver").assertIsDisplayed()
    }

    @Test
    fun goHomeScreen_permissionDenied_showsTaxiCardFallbackAction() {
        val homeBase = sampleHomeBase()
        val viewModel = buildViewModel(
            homeBase = homeBase,
            permissionStatus = PermissionStatus.DENIED
        )

        var taxiCardHomeBase: HomeBase? = null
        var permissionRequested = false

        composeTestRule.setContent {
            GoHomeScreen(
                onNavigateToSetup = {},
                onNavigateToTaxiCard = { taxiCardHomeBase = it },
                onRequestLocationPermission = { permissionRequested = true },
                viewModel = viewModel
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.isLoading.value
        }

        composeTestRule.onNodeWithText("Location Permission Needed").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertFalse(permissionRequested)
            assertNull(taxiCardHomeBase)
        }

        composeTestRule.onNodeWithText("Enable Location").performClick()
        composeTestRule.runOnIdle {
            assertTrue(permissionRequested)
            assertNull(taxiCardHomeBase)
        }

        composeTestRule.onNodeWithText("Show to Taxi Driver").performClick()
        composeTestRule.runOnIdle {
            assertNotNull(taxiCardHomeBase)
            assertEquals(homeBase.name, taxiCardHomeBase?.name)
        }
    }

    @Test
    fun taxiDriverCardScreen_rendersDriverInstructions() {
        composeTestRule.setContent {
            TaxiDriverCardScreen(
                homeBase = sampleHomeBase(),
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithText("Please take me here").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show this to the taxi driver").assertIsDisplayed()
    }

    private fun buildViewModel(
        homeBase: HomeBase,
        permissionStatus: PermissionStatus
    ): HomeBaseViewModel {
        return HomeBaseViewModel(
            settingsRepository = FakeUserSettingsRepository(homeBase),
            locationService = FakeLocationService(permissionStatus),
            headingService = FakeHeadingService()
        )
    }

    private fun sampleHomeBase(): HomeBase {
        return HomeBase(
            name = "Riad Dar Maya",
            lat = 31.6295,
            lng = -7.9912,
            address = "Medina, Marrakech"
        )
    }

    private class FakeUserSettingsRepository(
        initialHomeBase: HomeBase?
    ) : UserSettingsRepository {
        private val settings = mutableMapOf<SettingKey, String>()
        private var homeBase: HomeBase? = initialHomeBase
        private var exchangeRate: ExchangeRate? = null

        override suspend fun <T> getSetting(key: SettingKey, deserialize: (String) -> T?): T? {
            val rawValue = settings[key] ?: return null
            return deserialize(rawValue)
        }

        override suspend fun <T> setSetting(key: SettingKey, value: T, serialize: (T) -> String) {
            settings[key] = serialize(value)
        }

        override suspend fun deleteSetting(key: SettingKey) {
            settings.remove(key)
        }

        override suspend fun getHomeBase(): HomeBase? = homeBase

        override suspend fun setHomeBase(homeBase: HomeBase) {
            this.homeBase = homeBase
        }

        override suspend fun getExchangeRate(): ExchangeRate? = exchangeRate

        override suspend fun setExchangeRate(rate: ExchangeRate) {
            exchangeRate = rate
        }

        override suspend fun isOnboardingComplete(): Boolean {
            return settings[SettingKey.ONBOARDING_COMPLETE] == "true"
        }

        override suspend fun setOnboardingComplete(complete: Boolean) {
            settings[SettingKey.ONBOARDING_COMPLETE] = complete.toString()
        }

        override suspend fun isArrivalModeActive(): Boolean {
            return settings[SettingKey.ARRIVAL_MODE_ACTIVE] == "true"
        }

        override suspend fun setArrivalModeActive(active: Boolean) {
            settings[SettingKey.ARRIVAL_MODE_ACTIVE] = active.toString()
        }

        override suspend fun getArrivalModeCompletedAt(): String? {
            return settings[SettingKey.ARRIVAL_MODE_COMPLETED_AT]
        }

        override suspend fun markArrivalModeCompleted(iso8601Timestamp: String) {
            settings[SettingKey.ARRIVAL_MODE_COMPLETED_AT] = iso8601Timestamp
            settings[SettingKey.ARRIVAL_MODE_ACTIVE] = false.toString()
        }

        override suspend fun resetArrivalMode() {
            settings.remove(SettingKey.ARRIVAL_MODE_COMPLETED_AT)
            settings[SettingKey.ARRIVAL_MODE_ACTIVE] = true.toString()
        }
    }

    private class FakeLocationService(
        initialPermission: PermissionStatus
    ) : LocationService {
        private val permission = MutableStateFlow(initialPermission)
        private val location = MutableStateFlow<Location?>(null)

        override val currentLocation: StateFlow<Location?> = location
        override val permissionStatus: StateFlow<PermissionStatus> = permission

        override val isAuthorized: Boolean
            get() = permission.value == PermissionStatus.AUTHORIZED

        override fun checkPermission(): PermissionStatus = permission.value

        override fun startUpdates() = Unit

        override fun stopUpdates() = Unit

        override suspend fun refreshLocation(): Location {
            return location.value ?: Location("test").apply {
                latitude = 31.6295
                longitude = -7.9912
                time = System.currentTimeMillis()
            }
        }
    }

    private class FakeHeadingService : HeadingService {
        private val heading = MutableStateFlow<Float?>(0f)
        private val confidence = MutableStateFlow(HeadingConfidence.GOOD)

        override val currentHeading: StateFlow<Float?> = heading
        override val headingConfidence: StateFlow<HeadingConfidence> = confidence
        override val isHeadingAvailable: Boolean = true

        override fun startUpdates() = Unit

        override fun stopUpdates() = Unit

        override fun needsCalibration(): Boolean = false
    }
}
