package com.marrakechguide.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marrakechguide.core.database.entity.FavoriteEntity
import com.marrakechguide.core.database.entity.RecentEntity
import com.marrakechguide.core.repository.ContentType
import com.marrakechguide.core.repository.FavoritesRepository
import com.marrakechguide.core.repository.RecentsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SettingsScreen.
 *
 * These tests verify the "Run Setup Again" feature properly resets
 * the onboarding completion flag before navigating.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel
    private val onboardingPrefsName = "onboarding"
    private val onboardingCompleteKey = "onboarding_complete"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        viewModel = SettingsViewModel(
            context = context,
            recentsRepository = FakeRecentsRepository(),
            favoritesRepository = FakeFavoritesRepository()
        )
        // Clear any existing state
        context.getSharedPreferences(onboardingPrefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        // Clean up
        context.getSharedPreferences(onboardingPrefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    /**
     * Regression test for the bug where "Run Setup Again" didn't reset
     * the onboarding completion flag, causing the onboarding flow to
     * not dismiss properly when completed again.
     *
     * The fix ensures SharedPreferences is reset BEFORE the navigation
     * callback is invoked, so the OnboardingViewModel initializes with
     * isComplete = false.
     */
    @Test
    fun runSetupAgain_resetsOnboardingFlagBeforeNavigation() {
        // Given: Onboarding was previously completed
        val prefs = context.getSharedPreferences(onboardingPrefsName, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(onboardingCompleteKey, true).commit()
        assertTrue("Precondition: onboarding should be marked complete",
            prefs.getBoolean(onboardingCompleteKey, false))

        var navigationCalled = false
        var flagValueWhenNavigationCalled: Boolean? = null

        // When: SettingsScreen is displayed and "Run Setup Again" is clicked
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToOnboarding = {
                    // Capture the flag value at the moment navigation is called
                    flagValueWhenNavigationCalled = prefs.getBoolean(onboardingCompleteKey, true)
                    navigationCalled = true
                }
            )
        }

        // Scroll to and click "Run Setup Again"
        composeTestRule.onNodeWithText("Run Setup Again")
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            // Then: Navigation callback was invoked
            assertTrue("Navigation callback should be called", navigationCalled)

            // And: The flag was reset BEFORE the navigation callback was invoked
            assertFalse("Onboarding flag should be false when navigation is called",
                flagValueWhenNavigationCalled ?: true)

            // And: The flag is still false in SharedPreferences
            assertFalse("Onboarding flag should remain false",
                prefs.getBoolean(onboardingCompleteKey, true))
        }
    }

    @Test
    fun runSetupAgain_buttonIsDisplayed() {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Run Setup Again")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun runSetupAgain_doesNotNavigateBeforeClick() {
        val prefs = context.getSharedPreferences(onboardingPrefsName, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(onboardingCompleteKey, true).commit()

        var navigationCalled = false

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToOnboarding = {
                    navigationCalled = true
                }
            )
        }

        composeTestRule.onNodeWithText("Run Setup Again")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertFalse("Navigation should not be called until user taps the row", navigationCalled)
            assertTrue(
                "Onboarding flag should remain unchanged before click",
                prefs.getBoolean(onboardingCompleteKey, false)
            )
        }
    }

    private class FakeRecentsRepository : RecentsRepository {
        override fun getRecents(limit: Int): Flow<List<RecentEntity>> = flowOf(emptyList())

        override suspend fun getRecentsOnce(limit: Int): List<RecentEntity> = emptyList()

        override fun getRecentsByType(type: ContentType, limit: Int): Flow<List<RecentEntity>> {
            return flowOf(emptyList())
        }

        override suspend fun getRecentsByTypeOnce(type: ContentType, limit: Int): List<RecentEntity> {
            return emptyList()
        }

        override suspend fun recordView(contentType: ContentType, contentId: String) = Unit

        override suspend fun clearRecents() = Unit
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        override fun getFavorites(): Flow<List<FavoriteEntity>> = flowOf(emptyList())

        override suspend fun getFavoritesOnce(): List<FavoriteEntity> = emptyList()

        override fun getFavoritesByType(type: ContentType): Flow<List<FavoriteEntity>> {
            return flowOf(emptyList())
        }

        override suspend fun getFavoritesByTypeOnce(type: ContentType): List<FavoriteEntity> {
            return emptyList()
        }

        override suspend fun isFavorite(contentType: ContentType, contentId: String): Boolean = false

        override suspend fun addFavorite(contentType: ContentType, contentId: String) = Unit

        override suspend fun removeFavorite(contentType: ContentType, contentId: String) = Unit

        override suspend fun toggleFavorite(contentType: ContentType, contentId: String): Boolean = false

        override suspend fun clearFavorites() = Unit
    }
}
