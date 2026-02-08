import SwiftUI

/// Phrasebook screen showing Darija phrases organized by category.
struct PhrasebookView: View {
    @StateObject private var viewModel: PhrasebookViewModel
    @State private var searchText = ""
    @State private var selectedPhrase: Phrase?
    @State private var showLargeTextMode = false
    @State private var lastSelectedPhrase: Phrase?

    init(phraseRepository: PhraseRepository) {
        _viewModel = StateObject(wrappedValue: PhrasebookViewModel(phraseRepository: phraseRepository))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Category chips (when not searching)
                if searchText.isEmpty {
                    PhraseCategoryBar(
                        categories: ["All"] + PhraseCategory.allCases.map { $0.displayName },
                        selectedCategory: Binding(
                            get: { viewModel.selectedCategory?.displayName },
                            set: { name in
                                if name == "All" {
                                    viewModel.selectedCategory = nil
                                } else {
                                    viewModel.selectedCategory = PhraseCategory.allCases.first { $0.displayName == name }
                                }
                            }
                        ),
                        onCategoryChanged: { }
                    )
                }

                // Content based on state
                Group {
                    if viewModel.isLoading {
                        ListItemSkeleton(rows: 8)
                    } else if let error = viewModel.errorMessage {
                        ErrorState(title: "Unable to load phrases", message: error) {
                            Task { await viewModel.load() }
                        }
                    } else {
                        let phrases = filteredPhrases
                        if phrases.isEmpty {
                            EmptyPhrasesView(searchText: searchText)
                        } else {
                            PhraseListView(
                                phrases: phrases,
                                onSelect: { phrase in
                                    lastSelectedPhrase = phrase
                                    selectedPhrase = phrase
                                    viewModel.recordView(phrase: phrase)
                                }
                            )
                        }
                    }
                }
            }
            .navigationTitle("Phrasebook")
            .searchable(text: $searchText, prompt: "Search phrases...")
            .onChange(of: searchText) { _, newValue in
                viewModel.searchQuery = newValue
            }
            .refreshable {
                await viewModel.load()
            }
            .task {
                await viewModel.load()
            }
            .sheet(item: $selectedPhrase) { phrase in
                PhraseDetailSheet(
                    phrase: phrase,
                    onShowLargeText: {
                        selectedPhrase = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            showLargeTextMode = true
                        }
                    }
                )
            }
            .fullScreenCover(isPresented: $showLargeTextMode) {
                if let phrase = lastSelectedPhrase {
                    LargeTextModeView(phrase: phrase) {
                        showLargeTextMode = false
                    }
                }
            }
        }
    }

    private var filteredPhrases: [Phrase] {
        if !searchText.isEmpty {
            return viewModel.searchResults
        }
        if let category = viewModel.selectedCategory {
            return viewModel.getPhrases(for: category)
        }
        return viewModel.allPhrases
    }
}

// MARK: - Category Bar

private struct PhraseCategoryBar: View {
    let categories: [String]
    @Binding var selectedCategory: String?
    let onCategoryChanged: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Theme.Spacing.sm) {
                ForEach(categories, id: \.self) { category in
                    let isSelected = selectedCategory == category || (selectedCategory == nil && category == "All")
                    Chip(
                        text: category,
                        style: isSelected ? .filled : .outlined,
                        tint: Theme.Adaptive.primary
                    )
                    .onTapGesture {
                        selectedCategory = category == "All" ? nil : category
                        onCategoryChanged()
                    }
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .background(Theme.Adaptive.backgroundPrimary)
    }
}

// MARK: - Phrase List

private struct PhraseListView: View {
    let phrases: [Phrase]
    let onSelect: (Phrase) -> Void

    var body: some View {
        List(phrases) { phrase in
            PhraseRowView(phrase: phrase)
                .contentShape(Rectangle())
                .onTapGesture {
                    onSelect(phrase)
                }
                .listRowInsets(EdgeInsets(top: Theme.Spacing.sm, leading: Theme.Spacing.md, bottom: Theme.Spacing.sm, trailing: Theme.Spacing.md))
        }
        .listStyle(.plain)
    }
}

private struct PhraseRowView: View {
    let phrase: Phrase

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            // Latin transliteration (primary)
            Text(phrase.latin)
                .font(.themeHeadline)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            // Arabic (secondary, RTL)
            if let arabic = phrase.arabic {
                Text(arabic)
                    .font(.themeBody)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .environment(\.layoutDirection, .rightToLeft)
            }

            // English meaning
            Text(phrase.english)
                .font(.themeSubheadline)
                .foregroundStyle(Theme.Adaptive.textSecondary)

            // Category badge
            if let category = phrase.category {
                Chip(text: category.capitalized, style: .outlined, size: .small)
            }
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

// MARK: - Phrase Detail Sheet

private struct PhraseDetailSheet: View {
    let phrase: Phrase
    let onShowLargeText: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.lg) {
                    // Latin (large, primary)
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("Darija (Latin)")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                        Text(phrase.latin)
                            .font(.system(size: 28, weight: .semibold))
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                    }

                    // Arabic (RTL)
                    if let arabic = phrase.arabic {
                        VStack(alignment: .trailing, spacing: Theme.Spacing.xs) {
                            Text("Arabic")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                                .frame(maxWidth: .infinity, alignment: .trailing)
                            Text(arabic)
                                .font(.system(size: 32, weight: .regular))
                                .foregroundStyle(Theme.Adaptive.textPrimary)
                                .environment(\.layoutDirection, .rightToLeft)
                                .frame(maxWidth: .infinity, alignment: .trailing)
                        }
                    }

                    // English
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("English")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                        Text(phrase.english)
                            .font(.themeTitle2)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                    }

                    Divider()

                    // Actions
                    VStack(spacing: Theme.Spacing.sm) {
                        Button {
                            onShowLargeText()
                        } label: {
                            Label("Show to Driver", systemImage: "person.fill.viewfinder")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)

                        HStack(spacing: Theme.Spacing.sm) {
                            Button {
                                UIPasteboard.general.string = phrase.latin
                            } label: {
                                Label("Copy Latin", systemImage: "doc.on.doc")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)

                            Button {
                                UIPasteboard.general.string = phrase.arabic ?? ""
                            } label: {
                                Label("Copy Arabic", systemImage: "doc.on.doc")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("Phrase")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Large Text Mode (Taxi Driver Card)

private struct LargeTextModeView: View {
    let phrase: Phrase
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: Theme.Spacing.xl) {
                Spacer()

                // Arabic (very large)
                if let arabic = phrase.arabic {
                    Text(arabic)
                        .font(.system(size: 64, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .environment(\.layoutDirection, .rightToLeft)
                }

                // Latin (below)
                Text(phrase.latin)
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)

                // English (small)
                Text(phrase.english)
                    .font(.system(size: 24))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)

                Spacer()

                // Close button
                Button {
                    onDismiss()
                } label: {
                    Text("Tap to Close")
                        .font(.themeHeadline)
                        .foregroundColor(.blue)
                        .padding()
                }
            }
            .padding(Theme.Spacing.lg)
        }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
        }
    }
}

// MARK: - Empty State

private struct EmptyPhrasesView: View {
    let searchText: String

    var body: some View {
        EmptyState(
            icon: "text.bubble",
            title: "No Phrases Found",
            message: searchText.isEmpty
                ? "No phrases available in this category."
                : "No phrases matching \"\(searchText)\""
        )
    }
}

// MARK: - Preview

#Preview {
    PhrasebookView(phraseRepository: MockPhraseRepository())
}

// Mock repository for previews
private class MockPhraseRepository: PhraseRepository {
    func getAllPhrases() async throws -> [Phrase] {
        try await getPhrases(category: "")
    }

    func getPhrase(id: String) async throws -> Phrase? { nil }

    func getPhrases(category: String) async throws -> [Phrase] {
        [
            Phrase(id: "1", category: "greetings", arabic: "السلام عليكم", latin: "Salam alaikum", english: "Hello", audio: nil, verificationStatus: nil),
            Phrase(id: "2", category: "shopping", arabic: "بشحال؟", latin: "Bsh-hal?", english: "How much?", audio: nil, verificationStatus: nil),
        ]
    }

    func searchPhrases(query: String, limit: Int) async throws -> [Phrase] { [] }
}

// Private initializer extension for Phrase (preview only)
private extension Phrase {
    init(id: String, category: String?, arabic: String?, latin: String, english: String, audio: String?, verificationStatus: String?) {
        self.id = id
        self.category = category
        self.arabic = arabic
        self.latin = latin
        self.english = english
        self.audio = audio
        self.verificationStatus = verificationStatus
    }
}
