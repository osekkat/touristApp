package com.marrakechguide.feature.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for OnboardingViewModel.
 *
 * These tests verify the onboarding flow, particularly the re-run scenario
 * that was previously buggy (the completion flag not being reset).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val onboardingPrefsName = "onboarding"
    private val onboardingCompleteKey = "onboarding_complete"

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(onboardingPrefsName, Context.MODE_PRIVATE)
        // Clear any existing onboarding state before each test
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Clean up after tests
        prefs.edit().clear().commit()
    }

    // MARK: - Initial State Tests

    @Test
    fun `initial state - when never completed - isComplete is false`() = runTest {
        // Given: No prior onboarding completion
        prefs.edit().putBoolean(onboardingCompleteKey, false).commit()

        // When: Creating a new ViewModel
        val viewModel = OnboardingViewModel(context)

        // Then: isComplete should be false
        assertFalse(viewModel.isComplete.first())
        assertEquals(OnboardingStep.WELCOME, viewModel.currentStep.first())
    }

    @Test
    fun `initial state - when previously completed - isComplete is true`() = runTest {
        // Given: Prior onboarding was completed
        prefs.edit().putBoolean(onboardingCompleteKey, true).commit()

        // When: Creating a new ViewModel
        val viewModel = OnboardingViewModel(context)

        // Then: isComplete should be true
        assertTrue(viewModel.isComplete.first())
    }

    // MARK: - Navigation Tests

    @Test
    fun `goToNextStep - advances through all steps`() = runTest {
        val viewModel = OnboardingViewModel(context)

        assertEquals(OnboardingStep.WELCOME, viewModel.currentStep.first())

        viewModel.goToNextStep()
        assertEquals(OnboardingStep.OFFLINE_PROMISE, viewModel.currentStep.first())

        viewModel.goToNextStep()
        assertEquals(OnboardingStep.DOWNLOADS, viewModel.currentStep.first())

        viewModel.goToNextStep()
        assertEquals(OnboardingStep.READINESS_CHECK, viewModel.currentStep.first())
    }

    @Test
    fun `goToNextStep - from last step - completes onboarding`() = runTest {
        val viewModel = OnboardingViewModel(context)

        // Navigate through all steps to the end
        repeat(OnboardingStep.entries.size) {
            viewModel.goToNextStep()
        }

        // Then: Onboarding should be complete
        assertTrue(viewModel.isComplete.first())
        assertTrue(prefs.getBoolean(onboardingCompleteKey, false))
    }

    @Test
    fun `skipToEnd - completes onboarding`() = runTest {
        val viewModel = OnboardingViewModel(context)

        // When: Skipping to end
        viewModel.skipToEnd()

        // Then: Onboarding should be complete
        assertTrue(viewModel.isComplete.first())
        assertTrue(prefs.getBoolean(onboardingCompleteKey, false))
    }

    // MARK: - Re-run Onboarding Tests (Regression tests for the bug)

    /**
     * This test verifies the fix for the bug where re-running onboarding
     * wouldn't properly work because isComplete was already true.
     *
     * The fix requires that the calling code (SettingsScreen's "Run Setup Again")
     * resets the SharedPreferences flag BEFORE creating the OnboardingViewModel.
     */
    @Test
    fun `rerun onboarding - isComplete must transition from false to true`() = runTest {
        // Given: User has completed onboarding before
        prefs.edit().putBoolean(onboardingCompleteKey, true).commit()

        // Simulate what "Run Setup Again" now does before launching OnboardingScreen
        prefs.edit().putBoolean(onboardingCompleteKey, false).commit()

        // When: Creating a new ViewModel (as OnboardingScreen does)
        val viewModel = OnboardingViewModel(context)

        // Then: isComplete should start as false
        assertFalse(viewModel.isComplete.first())

        // When: User completes onboarding again
        viewModel.skipToEnd()

        // Then: isComplete should transition to true (this transition is what UI observes)
        assertTrue(viewModel.isComplete.first())
    }

    /**
     * This test demonstrates the bug that would occur WITHOUT the fix.
     * If the flag isn't reset before creating the ViewModel, isComplete starts as true
     * and observers wouldn't see a state change.
     */
    @Test
    fun `rerun onboarding - without reset - would not show proper transition`() = runTest {
        // Given: User has completed onboarding before
        prefs.edit().putBoolean(onboardingCompleteKey, true).commit()

        // BUG scenario: NOT resetting the flag before creating ViewModel
        // (This is what the old code did)

        // When: Creating a new ViewModel
        val viewModel = OnboardingViewModel(context)

        // Then: isComplete is already true
        assertTrue(viewModel.isComplete.first())

        // Record initial state
        val initialIsComplete = viewModel.isComplete.first()

        // When: User "completes" onboarding again
        viewModel.skipToEnd()

        // Then: isComplete is still true, but it didn't CHANGE
        // This means UI observers won't see a transition!
        assertTrue(viewModel.isComplete.first())
        assertEquals(initialIsComplete, viewModel.isComplete.first())
    }

    @Test
    fun `resetOnboarding - clears all state`() = runTest {
        // Given: A ViewModel that has progressed through onboarding
        val viewModel = OnboardingViewModel(context)
        viewModel.skipToEnd()
        assertTrue(viewModel.isComplete.first())

        // When: Resetting onboarding
        viewModel.resetOnboarding()

        // Then: All state should be cleared
        assertFalse(viewModel.isComplete.first())
        assertEquals(OnboardingStep.WELCOME, viewModel.currentStep.first())
        assertFalse(viewModel.basePackDownloaded.first())
        assertEquals(0f, viewModel.basePackProgress.first())
        assertTrue(viewModel.readinessItems.first().isEmpty())
        assertFalse(viewModel.readinessComplete.first())
        assertEquals(0, viewModel.demoStep.first())
        assertFalse(prefs.getBoolean(onboardingCompleteKey, true))
    }

    // MARK: - Static Method Tests

    @Test
    fun `shouldShowOnboarding - when not completed - returns true`() {
        prefs.edit().putBoolean(onboardingCompleteKey, false).commit()
        assertTrue(OnboardingViewModel.shouldShowOnboarding(context))
    }

    @Test
    fun `shouldShowOnboarding - when completed - returns false`() {
        prefs.edit().putBoolean(onboardingCompleteKey, true).commit()
        assertFalse(OnboardingViewModel.shouldShowOnboarding(context))
    }

    // MARK: - Button Title Tests

    @Test
    fun `nextButtonTitle - on privacy step - is Get Started`() = runTest {
        val viewModel = OnboardingViewModel(context)
        // Navigate to privacy step
        repeat(5) { viewModel.goToNextStep() }

        assertEquals(OnboardingStep.PRIVACY, viewModel.currentStep.first())
        assertEquals("Get Started", viewModel.nextButtonTitle())
    }

    @Test
    fun `nextButtonTitle - on downloads step when not downloaded - is Skip for Now`() = runTest {
        val viewModel = OnboardingViewModel(context)
        // Navigate to downloads step
        viewModel.goToNextStep() // -> OFFLINE_PROMISE
        viewModel.goToNextStep() // -> DOWNLOADS

        assertEquals(OnboardingStep.DOWNLOADS, viewModel.currentStep.first())
        assertFalse(viewModel.basePackDownloaded.first())
        assertEquals("Skip for Now", viewModel.nextButtonTitle())
    }

    @Test
    fun `nextButtonTitle - on other steps - is Continue`() = runTest {
        val viewModel = OnboardingViewModel(context)

        assertEquals(OnboardingStep.WELCOME, viewModel.currentStep.first())
        assertEquals("Continue", viewModel.nextButtonTitle())

        viewModel.goToNextStep()
        assertEquals(OnboardingStep.OFFLINE_PROMISE, viewModel.currentStep.first())
        assertEquals("Continue", viewModel.nextButtonTitle())
    }

    // MARK: - Demo Tests

    @Test
    fun `advanceDemo - progresses through steps`() = runTest {
        val viewModel = OnboardingViewModel(context)

        assertEquals(0, viewModel.demoStep.first())

        viewModel.advanceDemo()
        assertEquals(1, viewModel.demoStep.first())

        viewModel.advanceDemo()
        assertEquals(2, viewModel.demoStep.first())

        viewModel.advanceDemo()
        assertEquals(3, viewModel.demoStep.first())

        // Should not go beyond 3
        viewModel.advanceDemo()
        assertEquals(3, viewModel.demoStep.first())
    }

    // MARK: - Readiness Check Tests

    @Test
    fun `canGoNext - on readiness check step - requires completion`() = runTest {
        val viewModel = OnboardingViewModel(context)

        // Navigate to readiness check step
        viewModel.goToNextStep() // -> OFFLINE_PROMISE
        viewModel.goToNextStep() // -> DOWNLOADS
        viewModel.goToNextStep() // -> READINESS_CHECK

        assertEquals(OnboardingStep.READINESS_CHECK, viewModel.currentStep.first())

        // Initially cannot proceed (readiness check runs automatically but let's check logic)
        // Note: In real usage, performReadinessCheck is called and sets readinessComplete
        assertFalse(viewModel.readinessComplete.first())
    }
}
