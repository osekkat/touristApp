import Foundation
import SwiftUI

struct QuoteActionEvaluationSnapshot: Equatable, Sendable {
    let selectedCardId: String
    let quoteText: String
    let quantity: Int
    let modifierIds: Set<String>
}

enum QuoteActionEvaluationPolicy {
    static func makeSnapshot(
        selectedCardId: String,
        quoteText: String,
        quantity: Int,
        modifierIds: Set<String>
    ) -> QuoteActionEvaluationSnapshot {
        QuoteActionEvaluationSnapshot(
            selectedCardId: selectedCardId,
            quoteText: quoteText,
            quantity: quantity,
            modifierIds: modifierIds
        )
    }

    static func shouldApplyResult(
        snapshot: QuoteActionEvaluationSnapshot,
        currentSelectedCardId: String?,
        currentQuoteText: String,
        currentQuantity: Int,
        currentModifierIds: Set<String>
    ) -> Bool {
        currentSelectedCardId == snapshot.selectedCardId &&
            currentQuoteText == snapshot.quoteText &&
            currentQuantity == snapshot.quantity &&
            currentModifierIds == snapshot.modifierIds
    }

    static func makePricingInput(
        selectedCard: PriceCard,
        quoteAmount: Double,
        modifiers: [PricingEngine.ContextModifier],
        quantityAtEvaluation: Int
    ) -> PricingEngine.Input {
        PricingEngine.Input(
            expectedCostMinMad: Double(selectedCard.expectedCostMinMad),
            expectedCostMaxMad: Double(selectedCard.expectedCostMaxMad),
            quotedMad: quoteAmount,
            modifiers: modifiers,
            quantity: quantityAtEvaluation,
            fairnessLowMultiplier: selectedCard.fairnessLowMultiplier ?? 0.75,
            fairnessHighMultiplier: selectedCard.fairnessHighMultiplier ?? 1.25
        )
    }
}

enum QuoteActionInputMutationPolicy {
    static func didValueChange<Value: Equatable>(from oldValue: Value, to newValue: Value) -> Bool {
        oldValue != newValue
    }

    static func shouldInvalidateForInputChange(
        didValueChange: Bool,
        isSuppressed: Bool
    ) -> Bool {
        didValueChange && !isSuppressed
    }

    static func shouldReturnToInputPhase(
        isResultPhase: Bool,
        isCalculatingPhase: Bool
    ) -> Bool {
        isResultPhase || isCalculatingPhase
    }
}

struct QuoteActionView: View {
    let initialCardId: String?

    @State private var cards: [PriceCard] = QuoteActionCatalog.cards
    @State private var selectedCategory: QuoteCategory = .taxi
    @State private var selectedCardId: String?
    @State private var selectedModifierIds: Set<String> = []
    @State private var quoteText = ""
    @State private var quantity = 1
    @State private var phase: QuoteActionPhase = .loading
    @State private var showCategoryPicker = false
    @State private var showRecentQuotes = false
    @State private var showScripts = true
    @State private var recentQuotes: [RecentQuote] = []
    @State private var inlineErrorMessage: String?
    @State private var evaluationTask: Task<Void, Never>?
    @State private var suppressInputInvalidation = false

    init(initialCardId: String? = nil) {
        self.initialCardId = initialCardId
    }

    private var selectedCard: PriceCard? {
        cards.first { $0.id == selectedCardId }
    }

    private var cardsForSelectedCategory: [PriceCard] {
        cards.filter { QuoteCategory.from(rawCategory: $0.category) == selectedCategory }
    }

    private var canCalculate: Bool {
        selectedCard != nil && (Double(quoteText) ?? 0) > 0
    }

    private var activeResult: PricingEngine.Output? {
        if case .result(let output) = phase {
            return output
        }
        return nil
    }

    var body: some View {
        content
            .navigationTitle("Check a Quote")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showRecentQuotes = true
                    } label: {
                        Label("Recent", systemImage: "clock.arrow.trianglehead.counterclockwise.rotate.90")
                    }
                    .disabled(recentQuotes.isEmpty)
                    .accessibilityHint("View recent quote checks")
                }
            }
            .task {
                bootstrap()
            }
            .sheet(isPresented: $showCategoryPicker) {
                CategoryPickerSheet(
                    categories: QuoteCategory.ordered(
                        withRecent: recentQuotes.map(\.category)
                    ),
                    selected: selectedCategory,
                    onSelect: { category in
                        applyCategory(category)
                        showCategoryPicker = false
                    }
                )
                .presentationDetents([.medium])
            }
            .sheet(isPresented: $showRecentQuotes) {
                RecentQuotesSheet(
                    recentQuotes: recentQuotes,
                    onSelect: { recent in
                        applyRecentQuote(recent)
                        showRecentQuotes = false
                    }
                )
                .presentationDetents([.medium, .large])
            }
            .onDisappear {
                evaluationTask?.cancel()
                evaluationTask = nil
            }
            .onChange(of: quoteText) { oldValue, newValue in
                guard QuoteActionInputMutationPolicy.shouldInvalidateForInputChange(
                    didValueChange: QuoteActionInputMutationPolicy.didValueChange(from: oldValue, to: newValue),
                    isSuppressed: suppressInputInvalidation
                ) else { return }
                invalidateEvaluationIfNeeded()
            }
            .onChange(of: quantity) { oldValue, newValue in
                guard QuoteActionInputMutationPolicy.shouldInvalidateForInputChange(
                    didValueChange: QuoteActionInputMutationPolicy.didValueChange(from: oldValue, to: newValue),
                    isSuppressed: suppressInputInvalidation
                ) else { return }
                invalidateEvaluationIfNeeded()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch phase {
        case .loading:
            ListItemSkeleton(rows: 6)

        case .error(let message):
            ErrorState(
                title: "Unable to check quote",
                message: message,
                retryAction: {
                    phase = .input
                    inlineErrorMessage = nil
                }
            )

        case .calculating:
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    formSection(result: nil)

                    ContentCard(title: "Calculating") {
                        HStack(spacing: Theme.Spacing.sm) {
                            ProgressView()
                            Text("Checking fairness with selected modifiers...")
                                .font(.themeSubheadline)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.sm)
            }

        case .input:
            ScrollView {
                formSection(result: nil)
                    .padding(.horizontal, Theme.Spacing.md)
                    .padding(.vertical, Theme.Spacing.sm)
            }

        case .result(let output):
            ScrollView {
                formSection(result: output)
                    .padding(.horizontal, Theme.Spacing.md)
                    .padding(.vertical, Theme.Spacing.sm)
            }
        }
    }

    @ViewBuilder
    private func formSection(result: PricingEngine.Output?) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.md) {
            selectionCard

            if cardsForSelectedCategory.count > 1 {
                PriceCardSelector(
                    cards: cardsForSelectedCategory,
                    selectedCardId: selectedCardId,
                    onSelect: { newId in
                        evaluationTask?.cancel()
                        evaluationTask = nil
                        selectedCardId = newId
                        selectedModifierIds = []
                        phase = .input
                    }
                )
            }

            QuoteInputView(
                quoteText: $quoteText,
                quantity: $quantity,
                unitLabel: selectedCard?.unit ?? "per item",
                onBackspace: backspaceQuote,
                onClear: clearQuote
            )

            ModifierToggleList(
                modifiers: selectedCard?.contextModifiers ?? [],
                selectedModifierIds: selectedModifierIds,
                onToggle: toggleModifier
            )

            if let inlineErrorMessage {
                Text(inlineErrorMessage)
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Semantic.error)
            }

            Button {
                runEvaluation()
            } label: {
                Label("Check Fairness", systemImage: "scale.3d")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!canCalculate)

            if let selectedCard {
                FairnessResultView(
                    selectedCard: selectedCard,
                    quoteAmount: Double(quoteText) ?? 0,
                    result: result,
                    showScripts: $showScripts,
                    onSaveQuote: saveCurrentQuote
                )
            }
        }
    }

    private var selectionCard: some View {
        ContentCard(title: "1) Category") {
            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                Button {
                    showCategoryPicker = true
                } label: {
                    HStack {
                        Chip(text: selectedCategory.title, style: .outlined, tint: Theme.Adaptive.primary)
                        Spacer()
                        Image(systemName: "chevron.up.chevron.down")
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .frame(minHeight: 44)

                if let selectedCard {
                    HStack(spacing: Theme.Spacing.sm) {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            Text(selectedCard.title)
                                .font(.themeHeadline)
                                .foregroundStyle(Theme.Adaptive.textPrimary)
                            Text("Expected \(selectedCard.expectedCostMinMad)-\(selectedCard.expectedCostMaxMad) MAD")
                                .font(.themeFootnote)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }
                        Spacer()
                        PriceTag(minMAD: selectedCard.expectedCostMinMad, maxMAD: selectedCard.expectedCostMaxMad)
                    }
                }
            }
        }
    }

    private func bootstrap() {
        guard case .loading = phase else { return }

        cards = QuoteActionCatalog.cards

        if cards.isEmpty {
            phase = .error("No price cards are available yet.")
            return
        }

        if let initialCardId,
           let seeded = cards.first(where: { $0.id == initialCardId }) {
            selectedCategory = QuoteCategory.from(rawCategory: seeded.category)
            selectedCardId = seeded.id
        } else {
            applyCategory(selectedCategory)
        }

        phase = .input
    }

    private func applyCategory(_ category: QuoteCategory) {
        evaluationTask?.cancel()
        evaluationTask = nil
        selectedCategory = category
        let matches = cards.filter { QuoteCategory.from(rawCategory: $0.category) == category }

        if let existing = selectedCardId,
           matches.contains(where: { $0.id == existing }) {
            // Keep current selection if it still matches.
        } else {
            selectedCardId = matches.first?.id
        }

        selectedModifierIds = []
        quoteText = ""
        quantity = 1
        phase = .input
        inlineErrorMessage = nil
    }

    private func toggleModifier(_ modifierId: String) {
        evaluationTask?.cancel()
        evaluationTask = nil
        if selectedModifierIds.contains(modifierId) {
            selectedModifierIds.remove(modifierId)
        } else {
            selectedModifierIds.insert(modifierId)
        }
        if case .result = phase {
            phase = .input
        }
    }

    private func clearQuote() {
        evaluationTask?.cancel()
        evaluationTask = nil
        quoteText = ""
        if case .result = phase {
            phase = .input
        }
    }

    private func backspaceQuote() {
        evaluationTask?.cancel()
        evaluationTask = nil
        guard !quoteText.isEmpty else { return }
        quoteText.removeLast()
        if case .result = phase {
            phase = .input
        }
    }

    private func runEvaluation() {
        guard let selectedCard else {
            phase = .error("Select a price card before checking fairness.")
            return
        }

        guard let quoteAmount = Double(quoteText), quoteAmount > 0 else {
            inlineErrorMessage = "Enter a valid quote amount in MAD."
            return
        }

        inlineErrorMessage = nil
        phase = .calculating

        let chosenModifiers = (selectedCard.contextModifiers ?? []).filter { selectedModifierIds.contains($0.id) }
        let snapshot = QuoteActionEvaluationPolicy.makeSnapshot(
            selectedCardId: selectedCard.id,
            quoteText: quoteText,
            quantity: quantity,
            modifierIds: selectedModifierIds
        )
        let engineModifiers = chosenModifiers.map {
            PricingEngine.ContextModifier(
                factorMin: effectiveMinFactor(for: $0, baseMin: selectedCard.expectedCostMinMad),
                factorMax: effectiveMaxFactor(for: $0, baseMax: selectedCard.expectedCostMaxMad)
            )
        }

        evaluationTask?.cancel()
        evaluationTask = Task {
            try? await Task.sleep(nanoseconds: 240_000_000)
            guard !Task.isCancelled else { return }

            let output = PricingEngine.evaluate(
                QuoteActionEvaluationPolicy.makePricingInput(
                    selectedCard: selectedCard,
                    quoteAmount: quoteAmount,
                    modifiers: engineModifiers,
                    quantityAtEvaluation: snapshot.quantity
                )
            )

            await MainActor.run {
                defer { evaluationTask = nil }
                guard QuoteActionEvaluationPolicy.shouldApplyResult(
                    snapshot: snapshot,
                    currentSelectedCardId: selectedCardId,
                    currentQuoteText: quoteText,
                    currentQuantity: quantity,
                    currentModifierIds: selectedModifierIds
                ) else {
                    return
                }
                phase = .result(output)
            }
        }
    }

    private func invalidateEvaluationIfNeeded() {
        evaluationTask?.cancel()
        evaluationTask = nil
        inlineErrorMessage = nil

        if QuoteActionInputMutationPolicy.shouldReturnToInputPhase(
            isResultPhase: phase.isResult,
            isCalculatingPhase: phase.isCalculating
        ) {
            phase = .input
        }
    }

    private func effectiveMinFactor(for modifier: ContextModifier, baseMin: Int) -> Double {
        if let factor = modifier.factorMin {
            return factor
        }
        if let additive = modifier.addMin {
            let base = max(Double(baseMin), 1)
            return (base + additive) / base
        }
        return 1
    }

    private func effectiveMaxFactor(for modifier: ContextModifier, baseMax: Int) -> Double {
        if let factor = modifier.factorMax {
            return factor
        }
        if let additive = modifier.addMax {
            let base = max(Double(baseMax), 1)
            return (base + additive) / base
        }
        return 1
    }

    private func saveCurrentQuote() {
        guard let selectedCard,
              let quoteAmount = Double(quoteText),
              let result = activeResult else {
            return
        }

        let recent = RecentQuote(
            cardId: selectedCard.id,
            cardTitle: selectedCard.title,
            category: selectedCategory,
            quotedMad: quoteAmount,
            quantity: quantity,
            fairness: result.fairness,
            modifierIds: Array(selectedModifierIds),
            checkedAt: Date()
        )

        recentQuotes.removeAll { $0.cardId == recent.cardId && $0.quotedMad == recent.quotedMad }
        recentQuotes.insert(recent, at: 0)
        recentQuotes = Array(recentQuotes.prefix(10))
    }

    private func applyRecentQuote(_ recent: RecentQuote) {
        suppressInputInvalidation = true
        selectedCategory = recent.category
        selectedCardId = recent.cardId
        quoteText = String(Int(recent.quotedMad))
        quantity = recent.quantity
        selectedModifierIds = Set(recent.modifierIds)
        phase = .input
        inlineErrorMessage = nil
        runEvaluation()
        DispatchQueue.main.async {
            suppressInputInvalidation = false
        }
    }
}

private extension QuoteActionPhase {
    var isResult: Bool {
        if case .result = self {
            return true
        }
        return false
    }

    var isCalculating: Bool {
        if case .calculating = self {
            return true
        }
        return false
    }
}

private enum QuoteActionPhase {
    case loading
    case input
    case calculating
    case result(PricingEngine.Output)
    case error(String)
}

private enum QuoteCategory: String, CaseIterable, Identifiable {
    case taxi
    case hammam
    case souks
    case food
    case guide
    case other

    var id: String { rawValue }

    var title: String {
        switch self {
        case .taxi: return "Taxi"
        case .hammam: return "Hammam"
        case .souks: return "Souks"
        case .food: return "Food"
        case .guide: return "Guide"
        case .other: return "Other"
        }
    }

    var icon: String {
        switch self {
        case .taxi: return "car.fill"
        case .hammam: return "drop.fill"
        case .souks: return "bag.fill"
        case .food: return "fork.knife"
        case .guide: return "person.fill.questionmark"
        case .other: return "square.grid.2x2.fill"
        }
    }

    static func from(rawCategory: String?) -> QuoteCategory {
        switch rawCategory?.lowercased() {
        case "taxi":
            return .taxi
        case "hammam":
            return .hammam
        case "souks":
            return .souks
        case "food":
            return .food
        case "guides", "guide", "activities":
            return .guide
        default:
            return .other
        }
    }

    static func ordered(withRecent recent: [QuoteCategory]) -> [QuoteCategory] {
        var ordered: [QuoteCategory] = []
        for category in recent where !ordered.contains(category) {
            ordered.append(category)
        }
        for category in QuoteCategory.allCases where !ordered.contains(category) {
            ordered.append(category)
        }
        return ordered
    }
}

private struct RecentQuote: Identifiable {
    let id = UUID()
    let cardId: String
    let cardTitle: String
    let category: QuoteCategory
    let quotedMad: Double
    let quantity: Int
    let fairness: PricingEngine.FairnessLevel
    let modifierIds: [String]
    let checkedAt: Date
}

private struct CategoryPickerSheet: View {
    let categories: [QuoteCategory]
    let selected: QuoteCategory
    let onSelect: (QuoteCategory) -> Void

    var body: some View {
        NavigationStack {
            List(categories) { category in
                Button {
                    onSelect(category)
                } label: {
                    HStack(spacing: Theme.Spacing.sm) {
                        Image(systemName: category.icon)
                            .foregroundStyle(Theme.Adaptive.primary)
                            .frame(width: 24)
                        Text(category.title)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                        Spacer()
                        if category == selected {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Theme.Adaptive.primary)
                        }
                    }
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("Category")
        }
    }
}

private struct PriceCardSelector: View {
    let cards: [PriceCard]
    let selectedCardId: String?
    let onSelect: (String) -> Void

    var body: some View {
        ContentCard(title: "2) Price Card") {
            VStack(spacing: Theme.Spacing.sm) {
                ForEach(cards) { card in
                    Button {
                        onSelect(card.id)
                    } label: {
                        HStack(spacing: Theme.Spacing.sm) {
                            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                                Text(card.title)
                                    .font(.themeSubheadline.weight(.semibold))
                                    .foregroundStyle(Theme.Adaptive.textPrimary)
                                Text("\(card.expectedCostMinMad)-\(card.expectedCostMaxMad) MAD")
                                    .font(.themeFootnote)
                                    .foregroundStyle(Theme.Adaptive.textSecondary)
                            }
                            Spacer()
                            if selectedCardId == card.id {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(Theme.Adaptive.primary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)

                    if card.id != cards.last?.id {
                        Divider()
                    }
                }
            }
        }
    }
}

private struct QuoteInputView: View {
    @Binding var quoteText: String
    @Binding var quantity: Int

    let unitLabel: String
    let onBackspace: () -> Void
    let onClear: () -> Void

    var body: some View {
        ContentCard(title: "3) Quote Input") {
            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                HStack(spacing: Theme.Spacing.sm) {
                    TextField("0", text: $quoteText)
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .keyboardType(.numberPad)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityLabel("Quoted amount in MAD")
                        .frame(minHeight: 44)
                        .onChange(of: quoteText) { _, newValue in
                            let digitsOnly = newValue.filter(\.isNumber)
                            if digitsOnly != newValue {
                                quoteText = digitsOnly
                            }
                        }

                    Text("MAD")
                        .font(.themeHeadline)
                        .foregroundStyle(Theme.Adaptive.textSecondary)

                    Button(action: onBackspace) {
                        Image(systemName: "delete.left")
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Delete last digit")

                    Button(action: onClear) {
                        Image(systemName: "xmark.circle")
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear amount")
                }

                Text(unitLabel)
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)

                HStack {
                    Text("Quantity")
                        .font(.themeSubheadline)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                    Spacer()
                    Stepper(value: $quantity, in: 1...20) {
                        Text("\(quantity)")
                            .font(.themeHeadline)
                            .monospacedDigit()
                    }
                    .frame(minHeight: 44)
                }
            }
        }
    }
}

private struct ModifierToggleList: View {
    let modifiers: [ContextModifier]
    let selectedModifierIds: Set<String>
    let onToggle: (String) -> Void

    var body: some View {
        ContentCard(title: "4) Modifiers") {
            if modifiers.isEmpty {
                Text("No modifiers for this card.")
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            } else {
                VStack(spacing: Theme.Spacing.sm) {
                    ForEach(modifiers) { modifier in
                        Toggle(isOn: Binding(
                            get: { selectedModifierIds.contains(modifier.id) },
                            set: { _ in onToggle(modifier.id) }
                        )) {
                            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                                Text(modifier.label)
                                    .font(.themeSubheadline.weight(.semibold))
                                    .foregroundStyle(Theme.Adaptive.textPrimary)
                                Text(impactText(for: modifier))
                                    .font(.themeFootnote)
                                    .foregroundStyle(Theme.Adaptive.primary)
                                if let notes = modifier.notes {
                                    Text(notes)
                                        .font(.themeFootnote)
                                        .foregroundStyle(Theme.Adaptive.textSecondary)
                                }
                            }
                        }
                        .tint(Theme.Adaptive.primary)

                        if modifier.id != modifiers.last?.id {
                            Divider()
                        }
                    }
                }
            }
        }
    }

    private func impactText(for modifier: ContextModifier) -> String {
        if let min = modifier.factorMin, let max = modifier.factorMax {
            let minPct = Int(((min - 1) * 100).rounded())
            let maxPct = Int(((max - 1) * 100).rounded())
            if minPct == maxPct {
                return minPct >= 0 ? "+\(minPct)%" : "\(minPct)%"
            }
            let minText = minPct >= 0 ? "+\(minPct)%" : "\(minPct)%"
            let maxText = maxPct >= 0 ? "+\(maxPct)%" : "\(maxPct)%"
            return "\(minText) to \(maxText)"
        }

        if let min = modifier.addMin, let max = modifier.addMax {
            return "+\(Int(min.rounded())) to +\(Int(max.rounded())) MAD"
        }

        return "Variable impact"
    }
}

private struct FairnessResultView: View {
    let selectedCard: PriceCard
    let quoteAmount: Double
    let result: PricingEngine.Output?
    @Binding var showScripts: Bool
    let onSaveQuote: () -> Void

    var body: some View {
        ContentCard(title: "5) Result") {
            if let result {
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    FairnessMeter(
                        fairness: result.fairness,
                        quoteAmount: quoteAmount,
                        adjustedMax: result.adjustedMax,
                        highMultiplier: selectedCard.fairnessHighMultiplier ?? 1.25
                    )

                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("Expected: \(selectedCard.expectedCostMinMad)-\(selectedCard.expectedCostMaxMad) MAD")
                            .font(.themeSubheadline)
                        Text("Adjusted: \(formatMAD(result.adjustedMin))-\(formatMAD(result.adjustedMax)) MAD")
                            .font(.themeSubheadline)
                        Text("Your quote: \(formatMAD(quoteAmount)) MAD")
                            .font(.themeSubheadline.weight(.semibold))
                    }
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                    Text(verdictText(for: result.fairness))
                        .font(.themeHeadline)
                        .foregroundStyle(color(for: result.fairness))

                    Text("Try: \(formatMAD(result.counterMin))-\(formatMAD(result.counterMax)) MAD")
                        .font(.themeSubheadline.weight(.semibold))

                    DisclosureGroup(isExpanded: $showScripts) {
                        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                            ForEach(Array((selectedCard.negotiationScripts ?? []).enumerated()), id: \.offset) { _, script in
                                VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                                    Text(script.darijaLatin)
                                        .font(.themeSubheadline.weight(.semibold))
                                    if let darijaArabic = script.darijaArabic, !darijaArabic.isEmpty {
                                        Text(darijaArabic)
                                            .font(.themeBody)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    Text(script.english)
                                        .font(.themeFootnote)
                                        .foregroundStyle(Theme.Adaptive.textSecondary)
                                    if let french = script.french {
                                        Text(french)
                                            .font(.themeFootnote)
                                            .foregroundStyle(Theme.Adaptive.textSecondary)
                                    }
                                }
                            }
                        }
                        .padding(.top, Theme.Spacing.sm)
                    } label: {
                        Text("Scripts")
                            .font(.themeSubheadline.weight(.semibold))
                    }

                    if result.fairness == .low,
                       let checklist = selectedCard.inclusionsChecklist,
                       !checklist.isEmpty {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            Text("Clarify before accepting")
                                .font(.themeSubheadline.weight(.semibold))
                            ForEach(checklist, id: \.self) { item in
                                Label(item, systemImage: "checkmark.circle")
                                    .font(.themeFootnote)
                            }
                        }
                    }

                    if result.fairness == .veryHigh,
                       let alternatives = selectedCard.whatToDoInstead,
                       !alternatives.isEmpty {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            Text("Alternatives")
                                .font(.themeSubheadline.weight(.semibold))
                            ForEach(alternatives, id: \.self) { option in
                                Label(option, systemImage: "arrow.triangle.turn.up.right.diamond")
                                    .font(.themeFootnote)
                            }
                        }
                    }

                    Button {
                        onSaveQuote()
                    } label: {
                        Label("Save Quote", systemImage: "bookmark")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                }
            } else {
                Text("Enter a quote and tap Check Fairness.")
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
    }

    private func verdictText(for fairness: PricingEngine.FairnessLevel) -> String {
        switch fairness {
        case .low:
            return "Low quote. Verify what is included."
        case .fair:
            return "Fair quote."
        case .high:
            return "High quote. Negotiate."
        case .veryHigh:
            return "Very high quote. Walk away if needed."
        }
    }

    private func color(for fairness: PricingEngine.FairnessLevel) -> Color {
        switch fairness {
        case .low:
            return Theme.Fairness.low
        case .fair:
            return Theme.Fairness.fair
        case .high:
            return Theme.Fairness.high
        case .veryHigh:
            return Theme.Fairness.veryHigh
        }
    }

    private func formatMAD(_ value: Double) -> String {
        String(Int(value.rounded()))
    }
}

private struct FairnessMeter: View {
    let fairness: PricingEngine.FairnessLevel
    let quoteAmount: Double
    let adjustedMax: Double
    let highMultiplier: Double

    private var highThreshold: Double {
        adjustedMax * highMultiplier
    }

    private var markerPosition: Double {
        let scaleMax = max(highThreshold * 1.1, quoteAmount * 1.05, adjustedMax)
        guard scaleMax > 0 else { return 0 }
        return min(max(quoteAmount / scaleMax, 0), 1)
    }

    private var accessibilityDescription: String {
        let levelText: String
        switch fairness {
        case .low: levelText = "Suspiciously low"
        case .fair: levelText = "Fair"
        case .high: levelText = "High"
        case .veryHigh: levelText = "Very high"
        }
        return "Fairness meter shows \(levelText) for quote of \(Int(quoteAmount.rounded())) MAD"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            GeometryReader { proxy in
                let markerX = max(0, (proxy.size.width - 2) * markerPosition)

                ZStack(alignment: .leading) {
                    HStack(spacing: 2) {
                        Theme.Fairness.low
                        Theme.Fairness.fair
                        Theme.Fairness.high
                        Theme.Fairness.veryHigh
                    }
                    .frame(height: 14)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(Color.white)
                        .frame(width: 3, height: 24)
                        .overlay {
                            RoundedRectangle(cornerRadius: 2, style: .continuous)
                                .stroke(Theme.Adaptive.textPrimary.opacity(0.25), lineWidth: 1)
                        }
                        .offset(x: markerX, y: -5)
                }
            }
            .frame(height: 24)
            .accessibilityHidden(true)

            HStack {
                Text(label(for: fairness))
                    .font(.themeHeadline)
                    .foregroundStyle(color(for: fairness))
                Spacer()
                Text("Quote \(Int(quoteAmount.rounded())) MAD")
                    .font(.themeFootnote)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityDescription)
    }

    private func label(for fairness: PricingEngine.FairnessLevel) -> String {
        switch fairness {
        case .low: return "Low"
        case .fair: return "Fair"
        case .high: return "High"
        case .veryHigh: return "Very High"
        }
    }

    private func color(for fairness: PricingEngine.FairnessLevel) -> Color {
        switch fairness {
        case .low: return Theme.Fairness.low
        case .fair: return Theme.Fairness.fair
        case .high: return Theme.Fairness.high
        case .veryHigh: return Theme.Fairness.veryHigh
        }
    }
}

private struct RecentQuotesSheet: View {
    let recentQuotes: [RecentQuote]
    let onSelect: (RecentQuote) -> Void

    private let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    var body: some View {
        NavigationStack {
            if recentQuotes.isEmpty {
                EmptyState(
                    icon: "clock",
                    title: "No Recent Quotes",
                    message: "Saved quote checks will appear here."
                )
                .navigationTitle("Recent Quotes")
            } else {
                List(recentQuotes) { recent in
                    Button {
                        onSelect(recent)
                    } label: {
                        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                            HStack {
                                Text(recent.cardTitle)
                                    .font(.themeSubheadline.weight(.semibold))
                                Spacer()
                                Chip(
                                    text: label(for: recent.fairness),
                                    style: .outlined,
                                    tint: color(for: recent.fairness)
                                )
                            }
                            Text("\(Int(recent.quotedMad.rounded())) MAD · \(recent.category.title)")
                                .font(.themeFootnote)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                            Text(formatter.string(from: recent.checkedAt))
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                }
                .navigationTitle("Recent Quotes")
            }
        }
    }

    private func label(for fairness: PricingEngine.FairnessLevel) -> String {
        switch fairness {
        case .low: return "Low"
        case .fair: return "Fair"
        case .high: return "High"
        case .veryHigh: return "Very High"
        }
    }

    private func color(for fairness: PricingEngine.FairnessLevel) -> Color {
        switch fairness {
        case .low: return Theme.Fairness.low
        case .fair: return Theme.Fairness.fair
        case .high: return Theme.Fairness.high
        case .veryHigh: return Theme.Fairness.veryHigh
        }
    }
}

enum QuoteActionCatalog {
    static let cards: [PriceCard] = [
        PriceCard(
            id: "price-taxi-airport-marrakech-center",
            title: "Taxi: airport to Marrakech center",
            category: "taxi",
            unit: "per ride",
            volatility: "medium",
            confidence: "medium",
            expectedCostMinMad: 120,
            expectedCostMaxMad: 220,
            expectedCostNotes: "Night can be higher.",
            expectedCostUpdatedAt: "2026-02-07",
            provenanceNote: "Seed reference",
            whatInfluencesPrice: ["time of day", "luggage"],
            inclusionsChecklist: ["Confirm total fare", "Confirm exact destination"],
            negotiationScripts: [
                NegotiationScript(
                    darijaLatin: "Bsh-hal l-korsa l-center?",
                    english: "How much is the ride to the center?",
                    darijaArabic: nil,
                    french: nil
                ),
                NegotiationScript(
                    darijaLatin: "Afak, bghit taman qbl ma n-rkb.",
                    english: "Please, I want the price before getting in.",
                    darijaArabic: nil,
                    french: nil
                )
            ],
            redFlags: ["No clear fare before departure"],
            whatToDoInstead: ["Use another taxi with clear pricing"],
            contextModifiers: [
                ContextModifier(id: "night", label: "Night", factorMin: 1.1, factorMax: 1.2, addMin: nil, addMax: nil, notes: "Late-night demand premium"),
                ContextModifier(id: "peak", label: "Airport peak arrivals", factorMin: 1.05, factorMax: 1.15, addMin: nil, addMax: nil, notes: "Queues can increase quotes")
            ],
            fairnessLowMultiplier: 0.75,
            fairnessHighMultiplier: 1.25,
            sourceRefs: nil
        ),
        PriceCard(
            id: "price-taxi-medina-short-ride",
            title: "Taxi: short intra-city ride",
            category: "taxi",
            unit: "per ride",
            volatility: "medium",
            confidence: "medium",
            expectedCostMinMad: 20,
            expectedCostMaxMad: 50,
            expectedCostNotes: nil,
            expectedCostUpdatedAt: "2026-02-07",
            provenanceNote: "Seed reference",
            whatInfluencesPrice: ["traffic", "distance"],
            inclusionsChecklist: ["Is this total for the whole car?"],
            negotiationScripts: [
                NegotiationScript(
                    darijaLatin: "Hadchi ghali shwiya, momkin t-nqass?",
                    english: "That is a bit expensive, can you reduce it?",
                    darijaArabic: nil,
                    french: nil
                )
            ],
            redFlags: ["Very high quote for a short distance"],
            whatToDoInstead: ["Check an app quote before agreeing"],
            contextModifiers: [
                ContextModifier(id: "night", label: "Night", factorMin: 1.1, factorMax: 1.25, addMin: nil, addMax: nil, notes: "Night premium can apply")
            ],
            fairnessLowMultiplier: 0.75,
            fairnessHighMultiplier: 1.3,
            sourceRefs: nil
        ),
        PriceCard(
            id: "price-hammam-local-basic",
            title: "Hammam: local basic",
            category: "hammam",
            unit: "per person",
            volatility: "medium",
            confidence: "medium",
            expectedCostMinMad: 120,
            expectedCostMaxMad: 300,
            expectedCostNotes: nil,
            expectedCostUpdatedAt: "2026-02-07",
            provenanceNote: "Seed reference",
            whatInfluencesPrice: ["add-ons", "weekend demand"],
            inclusionsChecklist: ["Soap included?", "Massage included?"],
            negotiationScripts: [
                NegotiationScript(
                    darijaLatin: "Shno kayn f had taman?",
                    english: "What is included in this price?",
                    darijaArabic: nil,
                    french: nil
                )
            ],
            redFlags: ["Unclear add-on fees"],
            whatToDoInstead: ["Ask for itemized total before paying"],
            contextModifiers: [
                ContextModifier(id: "weekend", label: "Weekend peak", factorMin: 1.1, factorMax: 1.25, addMin: nil, addMax: nil, notes: "Popular time slots")
            ],
            fairnessLowMultiplier: 0.75,
            fairnessHighMultiplier: 1.35,
            sourceRefs: nil
        ),
        PriceCard(
            id: "price-food-mint-tea-cafe",
            title: "Mint tea in cafe/terrace",
            category: "food",
            unit: "per cup",
            volatility: "medium",
            confidence: "medium",
            expectedCostMinMad: 10,
            expectedCostMaxMad: 30,
            expectedCostNotes: nil,
            expectedCostUpdatedAt: "2026-02-07",
            provenanceNote: "Seed reference",
            whatInfluencesPrice: ["tourist area", "rooftop location"],
            inclusionsChecklist: ["Service included?", "Bottled water extra?"],
            negotiationScripts: [
                NegotiationScript(
                    darijaLatin: "Had taman kayn fih service?",
                    english: "Does this price include service?",
                    darijaArabic: nil,
                    french: nil
                )
            ],
            redFlags: ["No menu with listed prices"],
            whatToDoInstead: ["Ask for menu prices before ordering"],
            contextModifiers: [
                ContextModifier(id: "tourist-zone", label: "Tourist-heavy zone", factorMin: 1.1, factorMax: 1.25, addMin: nil, addMax: nil, notes: "Prime location markup")
            ],
            fairnessLowMultiplier: 0.75,
            fairnessHighMultiplier: 1.25,
            sourceRefs: nil
        ),
        PriceCard(
            id: "price-souk-lantern",
            title: "Souk: decorative lantern (mid-size)",
            category: "souks",
            unit: "per item",
            volatility: "high",
            confidence: "medium",
            expectedCostMinMad: 120,
            expectedCostMaxMad: 450,
            expectedCostNotes: nil,
            expectedCostUpdatedAt: "2026-02-07",
            provenanceNote: "Seed reference",
            whatInfluencesPrice: ["material", "quality", "seller zone"],
            inclusionsChecklist: ["Material and workmanship confirmed"],
            negotiationScripts: [
                NegotiationScript(
                    darijaLatin: "Bzaf had taman. Momkin taman akhor?",
                    english: "This price is too much. Can you do another price?",
                    darijaArabic: nil,
                    french: nil
                )
            ],
            redFlags: ["Starts at extreme anchor price"],
            whatToDoInstead: ["Walk away and compare two nearby stalls"],
            contextModifiers: [
                ContextModifier(id: "handmade", label: "High craftsmanship", factorMin: 1.2, factorMax: 1.5, addMin: nil, addMax: nil, notes: "Authentic handmade work is pricier")
            ],
            fairnessLowMultiplier: 0.7,
            fairnessHighMultiplier: 1.4,
            sourceRefs: nil
        ),
        PriceCard(
            id: "price-activity-city-half-day",
            title: "Private half-day city tour",
            category: "guides",
            unit: "per group",
            volatility: "medium",
            confidence: "medium",
            expectedCostMinMad: 350,
            expectedCostMaxMad: 900,
            expectedCostNotes: nil,
            expectedCostUpdatedAt: "2026-02-07",
            provenanceNote: "Seed reference",
            whatInfluencesPrice: ["language", "specialized route"],
            inclusionsChecklist: ["Licensed guide ID", "Total hours confirmed"],
            negotiationScripts: [
                NegotiationScript(
                    darijaLatin: "Chhal taman dial nsf nhar kamel?",
                    english: "What is the full price for a half day?",
                    darijaArabic: nil,
                    french: nil
                )
            ],
            redFlags: ["No clear hours or route scope"],
            whatToDoInstead: ["Use a licensed guide office reference"],
            contextModifiers: [
                ContextModifier(id: "specialist", label: "Specialized history route", factorMin: 1.1, factorMax: 1.35, addMin: nil, addMax: nil, notes: "Subject matter premium")
            ],
            fairnessLowMultiplier: 0.75,
            fairnessHighMultiplier: 1.3,
            sourceRefs: nil
        )
    ]
}

#Preview("Quote Action") {
    NavigationStack {
        QuoteActionView()
    }
}
