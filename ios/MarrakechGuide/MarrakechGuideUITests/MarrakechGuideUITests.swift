import XCTest

final class MarrakechGuideUITests: XCTestCase {
    private func launchAppFromHome() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += [
            "-onboardingComplete", "YES",
            "-selectedTab", "home"
        ]
        app.launch()
        return app
    }

    func testPlaceholder() {
        XCTAssertTrue(true)
    }

    // MARK: - Run Setup Again Tests

    /// Regression test for the bug where re-running onboarding wouldn't
    /// properly dismiss because the completion flag wasn't reset.
    ///
    /// This test verifies the "Run Setup Again" flow works correctly:
    /// 1. Navigate to Settings → Run Setup Again
    /// 2. Tap "Start Setup" to begin onboarding
    /// 3. Onboarding should appear (not immediately dismiss)
    func testRunSetupAgain_showsOnboarding() {
        let app = launchAppFromHome()

        // Navigate to More tab
        let moreTab = app.tabBars.buttons["More"]
        XCTAssertTrue(moreTab.waitForExistence(timeout: 5), "More tab should exist")
        moreTab.tap()

        // Navigate to Settings
        let settingsButton = app.buttons["Settings"]
        if settingsButton.waitForExistence(timeout: 5) {
            settingsButton.tap()
        } else {
            // Try finding it as a static text in a list
            let settingsCell = app.staticTexts["Settings"]
            XCTAssertTrue(settingsCell.waitForExistence(timeout: 5), "Settings option should exist")
            settingsCell.tap()
        }

        // Scroll down and tap "Run Setup Again"
        let runSetupButton = app.staticTexts["Run Setup Again"]
        if !runSetupButton.exists {
            app.swipeUp()
        }
        XCTAssertTrue(runSetupButton.waitForExistence(timeout: 5), "Run Setup Again should exist")
        runSetupButton.tap()

        // Tap "Start Setup"
        let startSetupButton = app.buttons["Start Setup"]
        XCTAssertTrue(startSetupButton.waitForExistence(timeout: 5), "Start Setup button should exist")
        startSetupButton.tap()

        // Verify onboarding appears (should show Welcome screen, not immediately dismiss)
        let welcomeText = app.staticTexts["Welcome to Marrakech"]
        XCTAssertTrue(welcomeText.waitForExistence(timeout: 5),
            "Onboarding welcome screen should appear - if this fails, the completion flag wasn't reset")
    }

    func testQuoteActionResultControlsRemainAccessible() {
        let app = launchAppFromHome()

        let openQuoteButton = app.buttons["Check a Price"]
        XCTAssertTrue(openQuoteButton.waitForExistence(timeout: 8))
        openQuoteButton.tap()

        let amountField = app.textFields["Quoted amount in MAD"]
        XCTAssertTrue(amountField.waitForExistence(timeout: 8))
        amountField.tap()
        amountField.typeText("200")

        let checkFairnessButton = app.buttons["Check Fairness"]
        XCTAssertTrue(checkFairnessButton.waitForExistence(timeout: 5))
        checkFairnessButton.tap()

        let saveQuoteButton = app.buttons["Save Quote"]
        XCTAssertTrue(saveQuoteButton.waitForExistence(timeout: 8))
        XCTAssertTrue(saveQuoteButton.isHittable)

        let scriptsButton = app.buttons["Scripts"]
        XCTAssertTrue(scriptsButton.exists)
    }

    func testRecentQuoteSelectionReevaluatesAndShowsResult() {
        let app = launchAppFromHome()

        let openQuoteButton = app.buttons["Check a Price"]
        XCTAssertTrue(openQuoteButton.waitForExistence(timeout: 8))
        openQuoteButton.tap()

        let amountField = app.textFields["Quoted amount in MAD"]
        XCTAssertTrue(amountField.waitForExistence(timeout: 8))
        amountField.tap()
        amountField.typeText("220")

        let checkFairnessButton = app.buttons["Check Fairness"]
        XCTAssertTrue(checkFairnessButton.waitForExistence(timeout: 5))
        checkFairnessButton.tap()

        let saveQuoteButton = app.buttons["Save Quote"]
        XCTAssertTrue(saveQuoteButton.waitForExistence(timeout: 8))
        saveQuoteButton.tap()

        let recentButton = app.buttons["Recent"]
        XCTAssertTrue(recentButton.waitForExistence(timeout: 5))
        recentButton.tap()

        let recentQuoteCell = app.cells.firstMatch
        XCTAssertTrue(recentQuoteCell.waitForExistence(timeout: 5))
        recentQuoteCell.tap()

        XCTAssertTrue(saveQuoteButton.waitForExistence(timeout: 8))
        XCTAssertTrue(saveQuoteButton.isHittable)
    }
}
