import XCTest
@testable import MarrakechGuide

final class QuoteActionViewLogicTests: XCTestCase {

    func testShouldApplyResult_WhenStateMatchesSnapshot_ReturnsTrue() {
        let snapshot = QuoteActionEvaluationPolicy.makeSnapshot(
            selectedCardId: "price-taxi-medina-short-ride",
            quoteText: "120",
            quantity: 2,
            modifierIds: ["night"]
        )

        let shouldApply = QuoteActionEvaluationPolicy.shouldApplyResult(
            snapshot: snapshot,
            currentSelectedCardId: "price-taxi-medina-short-ride",
            currentQuoteText: "120",
            currentQuantity: 2,
            currentModifierIds: ["night"]
        )

        XCTAssertTrue(shouldApply)
    }

    func testShouldApplyResult_WhenQuoteChanges_ReturnsFalse() {
        let snapshot = QuoteActionEvaluationPolicy.makeSnapshot(
            selectedCardId: "price-taxi-medina-short-ride",
            quoteText: "120",
            quantity: 2,
            modifierIds: ["night"]
        )

        let shouldApply = QuoteActionEvaluationPolicy.shouldApplyResult(
            snapshot: snapshot,
            currentSelectedCardId: "price-taxi-medina-short-ride",
            currentQuoteText: "125",
            currentQuantity: 2,
            currentModifierIds: ["night"]
        )

        XCTAssertFalse(shouldApply)
    }

    func testShouldApplyResult_WhenQuantityChanges_ReturnsFalse() {
        let snapshot = QuoteActionEvaluationPolicy.makeSnapshot(
            selectedCardId: "price-taxi-medina-short-ride",
            quoteText: "120",
            quantity: 2,
            modifierIds: ["night"]
        )

        let shouldApply = QuoteActionEvaluationPolicy.shouldApplyResult(
            snapshot: snapshot,
            currentSelectedCardId: "price-taxi-medina-short-ride",
            currentQuoteText: "120",
            currentQuantity: 3,
            currentModifierIds: ["night"]
        )

        XCTAssertFalse(shouldApply)
    }

    func testShouldApplyResult_WhenCardChanges_ReturnsFalse() {
        let snapshot = QuoteActionEvaluationPolicy.makeSnapshot(
            selectedCardId: "price-taxi-medina-short-ride",
            quoteText: "120",
            quantity: 2,
            modifierIds: ["night"]
        )

        let shouldApply = QuoteActionEvaluationPolicy.shouldApplyResult(
            snapshot: snapshot,
            currentSelectedCardId: "price-taxi-airport-marrakech-center",
            currentQuoteText: "120",
            currentQuantity: 2,
            currentModifierIds: ["night"]
        )

        XCTAssertFalse(shouldApply)
    }

    func testShouldApplyResult_WhenModifiersChange_ReturnsFalse() {
        let snapshot = QuoteActionEvaluationPolicy.makeSnapshot(
            selectedCardId: "price-taxi-medina-short-ride",
            quoteText: "120",
            quantity: 2,
            modifierIds: ["night"]
        )

        let shouldApply = QuoteActionEvaluationPolicy.shouldApplyResult(
            snapshot: snapshot,
            currentSelectedCardId: "price-taxi-medina-short-ride",
            currentQuoteText: "120",
            currentQuantity: 2,
            currentModifierIds: []
        )

        XCTAssertFalse(shouldApply)
    }

    func testMakePricingInput_UsesQuantityAtEvaluationSnapshot() throws {
        guard let card = QuoteActionCatalog.cards.first else {
            XCTFail("QuoteActionCatalog should include at least one card")
            return
        }

        let input = QuoteActionEvaluationPolicy.makePricingInput(
            selectedCard: card,
            quoteAmount: 240,
            modifiers: [],
            quantityAtEvaluation: 4
        )

        XCTAssertEqual(input.quantity, 4)
        XCTAssertEqual(input.expectedCostMinMad, Double(card.expectedCostMinMad))
        XCTAssertEqual(input.expectedCostMaxMad, Double(card.expectedCostMaxMad))
        XCTAssertEqual(input.quotedMad, 240, accuracy: 0.001)
    }

    func testInputMutationPolicy_DidValueChange() {
        XCTAssertTrue(QuoteActionInputMutationPolicy.didValueChange(from: "100", to: "101"))
        XCTAssertFalse(QuoteActionInputMutationPolicy.didValueChange(from: "100", to: "100"))
        XCTAssertTrue(QuoteActionInputMutationPolicy.didValueChange(from: 1, to: 2))
    }

    func testInputMutationPolicy_ShouldInvalidateForInputChange() {
        XCTAssertTrue(
            QuoteActionInputMutationPolicy.shouldInvalidateForInputChange(
                didValueChange: true,
                isSuppressed: false
            )
        )
        XCTAssertFalse(
            QuoteActionInputMutationPolicy.shouldInvalidateForInputChange(
                didValueChange: false,
                isSuppressed: false
            )
        )
        XCTAssertFalse(
            QuoteActionInputMutationPolicy.shouldInvalidateForInputChange(
                didValueChange: true,
                isSuppressed: true
            )
        )
    }

    func testInputMutationPolicy_RecentQuoteRestoreSuppressesInvalidation() {
        let shouldInvalidate = QuoteActionInputMutationPolicy.shouldInvalidateForInputChange(
            didValueChange: QuoteActionInputMutationPolicy.didValueChange(from: "200", to: "220"),
            isSuppressed: true
        )
        XCTAssertFalse(shouldInvalidate)
    }

    func testInputMutationPolicy_ShouldReturnToInputPhase() {
        XCTAssertTrue(
            QuoteActionInputMutationPolicy.shouldReturnToInputPhase(
                isResultPhase: true,
                isCalculatingPhase: false
            )
        )
        XCTAssertTrue(
            QuoteActionInputMutationPolicy.shouldReturnToInputPhase(
                isResultPhase: false,
                isCalculatingPhase: true
            )
        )
        XCTAssertFalse(
            QuoteActionInputMutationPolicy.shouldReturnToInputPhase(
                isResultPhase: false,
                isCalculatingPhase: false
            )
        )
    }
}
