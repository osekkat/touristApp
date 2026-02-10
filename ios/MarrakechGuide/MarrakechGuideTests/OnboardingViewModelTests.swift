import XCTest
@testable import MarrakechGuide

/// Unit tests for OnboardingViewModel.
///
/// These tests verify the onboarding flow, particularly the re-run scenario
/// that was previously buggy (the completion flag not being reset).
@MainActor
final class OnboardingViewModelTests: XCTestCase {

    private let onboardingCompleteKey = "onboardingComplete"

    override func setUp() async throws {
        try await super.setUp()
        // Clear any existing onboarding state before each test
        UserDefaults.standard.removeObject(forKey: onboardingCompleteKey)
    }

    override func tearDown() async throws {
        // Clean up after tests
        UserDefaults.standard.removeObject(forKey: onboardingCompleteKey)
        try await super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_WhenNeverCompleted_IsCompleteIsFalse() {
        // Given: No prior onboarding completion
        UserDefaults.standard.set(false, forKey: onboardingCompleteKey)

        // When: Creating a new ViewModel
        let viewModel = OnboardingViewModel()

        // Then: isComplete should be false
        XCTAssertFalse(viewModel.isComplete)
        XCTAssertEqual(viewModel.currentStep, .welcome)
    }

    func testInitialState_WhenPreviouslyCompleted_IsCompleteIsTrue() {
        // Given: Prior onboarding was completed
        UserDefaults.standard.set(true, forKey: onboardingCompleteKey)

        // When: Creating a new ViewModel
        let viewModel = OnboardingViewModel()

        // Then: isComplete should be true
        XCTAssertTrue(viewModel.isComplete)
    }

    // MARK: - Navigation Tests

    func testGoToNextStep_AdvancesThroughAllSteps() {
        let viewModel = OnboardingViewModel()

        XCTAssertEqual(viewModel.currentStep, .welcome)

        viewModel.goToNextStep()
        XCTAssertEqual(viewModel.currentStep, .offlinePromise)

        viewModel.goToNextStep()
        XCTAssertEqual(viewModel.currentStep, .downloads)

        viewModel.goToNextStep()
        XCTAssertEqual(viewModel.currentStep, .readinessCheck)

        // Mark readiness as complete to allow progression
        viewModel.readinessComplete = true

        viewModel.goToNextStep()
        XCTAssertEqual(viewModel.currentStep, .demo)

        viewModel.goToNextStep()
        XCTAssertEqual(viewModel.currentStep, .privacy)
    }

    func testGoToNextStep_FromLastStep_CompletesOnboarding() {
        let viewModel = OnboardingViewModel()

        // Navigate to final step
        viewModel.currentStep = .privacy

        // When: Going next from the last step
        viewModel.goToNextStep()

        // Then: Onboarding should be complete
        XCTAssertTrue(viewModel.isComplete)
        XCTAssertTrue(UserDefaults.standard.bool(forKey: onboardingCompleteKey))
    }

    func testSkipToEnd_CompletesOnboarding() {
        let viewModel = OnboardingViewModel()

        // When: Skipping to end
        viewModel.skipToEnd()

        // Then: Onboarding should be complete
        XCTAssertTrue(viewModel.isComplete)
        XCTAssertTrue(UserDefaults.standard.bool(forKey: onboardingCompleteKey))
    }

    // MARK: - Re-run Onboarding Tests (Regression tests for the bug)

    /// This test verifies the fix for the bug where re-running onboarding
    /// wouldn't properly dismiss because isComplete was already true.
    func testRerunOnboarding_IsCompleteMustTransitionFromFalseToTrue() {
        // Given: User has completed onboarding before
        UserDefaults.standard.set(true, forKey: onboardingCompleteKey)

        // Simulate what RerunOnboardingView now does before presenting OnboardingView
        UserDefaults.standard.set(false, forKey: onboardingCompleteKey)

        // When: Creating a new ViewModel (as OnboardingView does)
        let viewModel = OnboardingViewModel()

        // Then: isComplete should start as false
        XCTAssertFalse(viewModel.isComplete, "ViewModel should start with isComplete=false after reset")

        // When: User completes onboarding again
        viewModel.currentStep = .privacy
        viewModel.goToNextStep()

        // Then: isComplete should transition to true (this transition is what triggers .onChange)
        XCTAssertTrue(viewModel.isComplete, "isComplete should become true after completing onboarding")
    }

    /// This test demonstrates the bug that would occur WITHOUT the fix.
    /// If the flag isn't reset before creating the ViewModel, isComplete starts as true
    /// and the .onChange observer won't fire because the value doesn't change.
    func testRerunOnboarding_WithoutReset_WouldNotTransition() {
        // Given: User has completed onboarding before
        UserDefaults.standard.set(true, forKey: onboardingCompleteKey)

        // BUG scenario: NOT resetting the flag before creating ViewModel
        // (This is what the old code did)

        // When: Creating a new ViewModel
        let viewModel = OnboardingViewModel()

        // Then: isComplete is already true
        XCTAssertTrue(viewModel.isComplete, "Without reset, isComplete starts as true")

        // Record initial state
        let initialIsComplete = viewModel.isComplete

        // When: User "completes" onboarding again
        viewModel.currentStep = .privacy
        viewModel.goToNextStep()

        // Then: isComplete is still true, but it didn't CHANGE
        // This means .onChange(of: viewModel.isComplete) won't fire!
        XCTAssertTrue(viewModel.isComplete)
        XCTAssertEqual(initialIsComplete, viewModel.isComplete,
                       "Without reset, isComplete doesn't transition - this is the bug!")
    }

    func testResetOnboarding_ClearsAllState() {
        // Given: A ViewModel that has progressed through onboarding
        let viewModel = OnboardingViewModel()
        viewModel.currentStep = .demo
        viewModel.demoStep = 2
        viewModel.basePackDownloaded = true
        viewModel.readinessComplete = true
        viewModel.skipToEnd() // Complete it
        XCTAssertTrue(viewModel.isComplete)

        // When: Resetting onboarding
        viewModel.resetOnboarding()

        // Then: All state should be cleared
        XCTAssertFalse(viewModel.isComplete)
        XCTAssertEqual(viewModel.currentStep, .welcome)
        XCTAssertFalse(viewModel.basePackDownloaded)
        XCTAssertEqual(viewModel.basePackProgress, 0)
        XCTAssertTrue(viewModel.readinessItems.isEmpty)
        XCTAssertFalse(viewModel.readinessComplete)
        XCTAssertEqual(viewModel.demoStep, 0)
        XCTAssertFalse(UserDefaults.standard.bool(forKey: onboardingCompleteKey))
    }

    // MARK: - Static Method Tests

    func testShouldShowOnboarding_WhenNotCompleted_ReturnsTrue() {
        UserDefaults.standard.set(false, forKey: onboardingCompleteKey)
        XCTAssertTrue(OnboardingViewModel.shouldShowOnboarding())
    }

    func testShouldShowOnboarding_WhenCompleted_ReturnsFalse() {
        UserDefaults.standard.set(true, forKey: onboardingCompleteKey)
        XCTAssertFalse(OnboardingViewModel.shouldShowOnboarding())
    }

    // MARK: - Readiness Check Tests

    func testCanGoNext_OnReadinessCheckStep_RequiresCompletion() {
        let viewModel = OnboardingViewModel()
        viewModel.currentStep = .readinessCheck

        // Initially cannot proceed
        XCTAssertFalse(viewModel.canGoNext)

        // After readiness check completes
        viewModel.readinessComplete = true
        XCTAssertTrue(viewModel.canGoNext)
    }

    // MARK: - Demo Tests

    func testAdvanceDemo_ProgressesThroughSteps() {
        let viewModel = OnboardingViewModel()

        XCTAssertEqual(viewModel.demoStep, 0)

        viewModel.advanceDemo()
        XCTAssertEqual(viewModel.demoStep, 1)

        viewModel.advanceDemo()
        XCTAssertEqual(viewModel.demoStep, 2)

        viewModel.advanceDemo()
        XCTAssertEqual(viewModel.demoStep, 3)

        // Should not go beyond 3
        viewModel.advanceDemo()
        XCTAssertEqual(viewModel.demoStep, 3)
    }

    // MARK: - Button Title Tests

    func testNextButtonTitle_OnPrivacyStep_IsGetStarted() {
        let viewModel = OnboardingViewModel()
        viewModel.currentStep = .privacy
        XCTAssertEqual(viewModel.nextButtonTitle, "Get Started")
    }

    func testNextButtonTitle_OnDownloadsStep_WhenNotDownloaded_IsSkipForNow() {
        let viewModel = OnboardingViewModel()
        viewModel.currentStep = .downloads
        viewModel.basePackDownloaded = false
        XCTAssertEqual(viewModel.nextButtonTitle, "Skip for Now")
    }

    func testNextButtonTitle_OnDownloadsStep_WhenDownloaded_IsContinue() {
        let viewModel = OnboardingViewModel()
        viewModel.currentStep = .downloads
        viewModel.basePackDownloaded = true
        XCTAssertEqual(viewModel.nextButtonTitle, "Continue")
    }

    func testNextButtonTitle_OnOtherSteps_IsContinue() {
        let viewModel = OnboardingViewModel()

        viewModel.currentStep = .welcome
        XCTAssertEqual(viewModel.nextButtonTitle, "Continue")

        viewModel.currentStep = .offlinePromise
        XCTAssertEqual(viewModel.nextButtonTitle, "Continue")

        viewModel.currentStep = .demo
        XCTAssertEqual(viewModel.nextButtonTitle, "Continue")
    }
}
