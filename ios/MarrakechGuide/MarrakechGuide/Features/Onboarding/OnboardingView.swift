import SwiftUI

/// Main onboarding container view.
struct OnboardingView: View {
    @StateObject private var viewModel = OnboardingViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            // Progress indicator
            ProgressView(value: viewModel.currentStep.progress)
                .progressViewStyle(.linear)
                .tint(Theme.Adaptive.primary)
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.top, Theme.Spacing.sm)

            // Step content
            TabView(selection: $viewModel.currentStep) {
                ForEach(OnboardingStep.allCases) { step in
                    stepView(for: step)
                        .tag(step)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: viewModel.currentStep)

            // Navigation buttons
            HStack(spacing: Theme.Spacing.md) {
                if viewModel.canGoBack {
                    Button("Back") {
                        viewModel.goToPreviousStep()
                    }
                    .buttonStyle(.bordered)
                }

                Spacer()

                if viewModel.currentStep != .privacy {
                    Button("Skip") {
                        viewModel.skipToEnd()
                    }
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                }

                Button(viewModel.nextButtonTitle) {
                    viewModel.goToNextStep()
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canGoNext)
            }
            .padding(Theme.Spacing.md)
            .background(Theme.Adaptive.backgroundPrimary)
        }
        .background(Theme.Adaptive.backgroundSecondary)
        .onChange(of: viewModel.isComplete) { _, complete in
            if complete {
                dismiss()
            }
        }
        .onChange(of: viewModel.currentStep) { _, newStep in
            // Trigger readiness check when entering that step (handles swipe navigation)
            if newStep == .readinessCheck && viewModel.readinessItems.isEmpty && !viewModel.isCheckingReadiness {
                Task { await viewModel.performReadinessCheck() }
            }
        }
    }

    @ViewBuilder
    private func stepView(for step: OnboardingStep) -> some View {
        switch step {
        case .welcome:
            WelcomeStepView(viewModel: viewModel)
        case .offlinePromise:
            OfflinePromiseStepView()
        case .downloads:
            DownloadsStepView(viewModel: viewModel)
        case .readinessCheck:
            ReadinessCheckStepView(viewModel: viewModel)
        case .demo:
            DemoStepView(viewModel: viewModel)
        case .privacy:
            PrivacyStepView()
        }
    }
}

// MARK: - Step 1: Welcome

private struct WelcomeStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.xl) {
                Spacer(minLength: Theme.Spacing.xl)

                // Hero
                Image(systemName: "building.columns.fill")
                    .font(.system(size: 80))
                    .foregroundStyle(Theme.Adaptive.primary)
                    .accessibilityHidden(true)

                VStack(spacing: Theme.Spacing.sm) {
                    Text("Welcome to Marrakech")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text("Your offline guide to the Red City")
                        .font(.themeTitle3)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }

                Spacer(minLength: Theme.Spacing.lg)

                // Settings
                VStack(spacing: Theme.Spacing.md) {
                    // Language picker
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("Language")
                            .font(.themeSubheadline)
                            .foregroundStyle(Theme.Adaptive.textSecondary)

                        Picker("Language", selection: $viewModel.selectedLanguage) {
                            ForEach(AppLanguage.allCases) { language in
                                Text(language.displayName).tag(language)
                            }
                        }
                        .pickerStyle(.segmented)
                    }

                    // Currency picker
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("Home Currency")
                            .font(.themeSubheadline)
                            .foregroundStyle(Theme.Adaptive.textSecondary)

                        Picker("Currency", selection: $viewModel.selectedCurrency) {
                            ForEach(HomeCurrency.allCases) { currency in
                                Text(currency.rawValue).tag(currency)
                            }
                        }
                        .pickerStyle(.segmented)
                    }

                    Text("Used for price comparisons. Stored locally on your device.")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .padding(Theme.Spacing.md)
                .background(Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                Spacer()
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
    }
}

// MARK: - Step 2: Offline Promise

private struct OfflinePromiseStepView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.xl) {
                Spacer(minLength: Theme.Spacing.xl)

                // Airplane mode icon
                ZStack {
                    Circle()
                        .fill(Theme.Adaptive.primary.opacity(0.1))
                        .frame(width: 120, height: 120)

                    Image(systemName: "airplane")
                        .font(.system(size: 50))
                        .foregroundStyle(Theme.Adaptive.primary)
                }
                .accessibilityHidden(true)

                VStack(spacing: Theme.Spacing.sm) {
                    Text("Works Without Internet")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text("Navigate the Medina with confidence, even offline")
                        .font(.themeTitle3)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }

                Spacer(minLength: Theme.Spacing.lg)

                // Feature list
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    OfflineFeatureRow(
                        icon: "checkmark.circle.fill",
                        iconColor: Theme.Fairness.fair,
                        title: "Works Offline",
                        items: ["Places & directions", "Price checks", "Phrasebook", "Tips & culture"]
                    )

                    Divider()

                    OfflineFeatureRow(
                        icon: "wifi",
                        iconColor: Theme.Adaptive.textSecondary,
                        title: "Needs Internet",
                        items: ["Content updates (optional)", "Weather (optional)"]
                    )
                }
                .padding(Theme.Spacing.md)
                .background(Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                Spacer()
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
    }
}

private struct OfflineFeatureRow: View {
    let icon: String
    let iconColor: Color
    let title: String
    let items: [String]

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.md) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(iconColor)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(title)
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                ForEach(items, id: \.self) { item in
                    Text(item)
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }
        }
    }
}

// MARK: - Step 3: Downloads

private struct DownloadsStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.xl) {
                Spacer(minLength: Theme.Spacing.xl)

                Image(systemName: "arrow.down.circle.fill")
                    .font(.system(size: 60))
                    .foregroundStyle(Theme.Adaptive.primary)
                    .accessibilityHidden(true)

                VStack(spacing: Theme.Spacing.sm) {
                    Text("Get Ready for Offline")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text("Download essential content for your trip")
                        .font(.themeTitle3)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }

                Spacer(minLength: Theme.Spacing.lg)

                // Download options
                VStack(spacing: Theme.Spacing.md) {
                    DownloadPackCard(
                        title: "Base Pack",
                        subtitle: "Places, prices, tips, phrases",
                        size: "12 MB",
                        isIncluded: true,
                        isDownloaded: viewModel.basePackDownloaded,
                        isDownloading: viewModel.isDownloadingBasePack,
                        progress: viewModel.basePackProgress,
                        onDownload: {
                            Task { await viewModel.downloadBasePack() }
                        }
                    )

                    DownloadPackCard(
                        title: "Medina Map Pack",
                        subtitle: "Offline navigation maps",
                        size: "45 MB",
                        isIncluded: false,
                        isDownloaded: false,
                        isDownloading: false,
                        progress: 0,
                        onDownload: {}
                    )
                    .opacity(0.6)
                    .overlay {
                        Text("Coming Soon")
                            .font(.themeCaption)
                            .padding(.horizontal, Theme.Spacing.sm)
                            .padding(.vertical, Theme.Spacing.xs)
                            .background(Theme.Adaptive.textSecondary)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }
                }

                Text("You can download more content later from Settings.")
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
                    .multilineTextAlignment(.center)

                Spacer()
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
    }
}

private struct DownloadPackCard: View {
    let title: String
    let subtitle: String
    let size: String
    let isIncluded: Bool
    let isDownloaded: Bool
    let isDownloading: Bool
    let progress: Double
    let onDownload: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            HStack {
                VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                    HStack {
                        Text(title)
                            .font(.themeHeadline)
                            .foregroundStyle(Theme.Adaptive.textPrimary)

                        if isIncluded {
                            Text("Included")
                                .font(.themeCaption)
                                .padding(.horizontal, Theme.Spacing.sm)
                                .padding(.vertical, 2)
                                .background(Theme.Fairness.fair)
                                .foregroundStyle(.white)
                                .clipShape(Capsule())
                        }
                    }

                    Text(subtitle)
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }

                Spacer()

                Text(size)
                    .font(.themeSubheadline)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }

            if isDownloading {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(Theme.Adaptive.primary)
            } else if isDownloaded {
                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Theme.Fairness.fair)
                    Text("Downloaded")
                        .font(.themeSubheadline)
                        .foregroundStyle(Theme.Fairness.fair)
                }
            } else if isIncluded {
                Button("Download Now") {
                    onDownload()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
        }
        .padding(Theme.Spacing.md)
        .background(Theme.Adaptive.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }
}

// MARK: - Step 4: Readiness Check

private struct ReadinessCheckStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.xl) {
                Spacer(minLength: Theme.Spacing.xl)

                Image(systemName: "checkmark.shield.fill")
                    .font(.system(size: 60))
                    .foregroundStyle(viewModel.readinessComplete ? Theme.Fairness.fair : Theme.Adaptive.primary)
                    .accessibilityHidden(true)

                VStack(spacing: Theme.Spacing.sm) {
                    Text("Ready Check")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text(viewModel.readinessComplete
                         ? "Everything looks good!"
                         : "Verifying offline readiness...")
                        .font(.themeTitle3)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }

                Spacer(minLength: Theme.Spacing.lg)

                // Checklist
                VStack(spacing: Theme.Spacing.md) {
                    ForEach(viewModel.readinessItems) { item in
                        ReadinessRow(item: item)
                    }

                    if viewModel.isCheckingReadiness && viewModel.readinessItems.isEmpty {
                        ProgressView()
                            .padding()
                    }
                }
                .padding(Theme.Spacing.md)
                .background(Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                if viewModel.readinessComplete {
                    VStack(spacing: Theme.Spacing.sm) {
                        Image(systemName: "airplane")
                            .font(.title2)
                            .foregroundStyle(Theme.Adaptive.textSecondary)

                        Text("Try enabling Airplane Mode to test offline features!")
                            .font(.themeSubheadline)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(Theme.Spacing.md)
                }

                Spacer()
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
    }
}

private struct ReadinessRow: View {
    let item: ReadinessItem

    var body: some View {
        HStack {
            if item.isReady {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Theme.Fairness.fair)
            } else {
                ProgressView()
                    .scaleEffect(0.8)
            }

            Text(item.title)
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Spacer()

            if let count = item.count {
                Text("\(count)")
                    .font(.themeSubheadline.weight(.semibold))
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
    }
}

// MARK: - Step 5: Demo

private struct DemoStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.xl) {
                Spacer(minLength: Theme.Spacing.lg)

                VStack(spacing: Theme.Spacing.sm) {
                    Text("Quick Demo")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text("See how to check if a price is fair")
                        .font(.themeTitle3)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }

                // Demo card
                VStack(spacing: Theme.Spacing.md) {
                    // Scenario
                    HStack {
                        Image(systemName: "car.fill")
                            .font(.title2)
                            .foregroundStyle(Theme.Adaptive.primary)

                        VStack(alignment: .leading) {
                            Text("Taxi from Airport")
                                .font(.themeHeadline)
                            Text("Driver quotes: 200 MAD")
                                .font(.themeBody)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }

                        Spacer()
                    }
                    .padding(Theme.Spacing.md)
                    .background(Theme.Adaptive.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))

                    // Result based on demo step
                    if viewModel.demoStep >= 1 {
                        VStack(spacing: Theme.Spacing.sm) {
                            HStack {
                                Text("Fair Price Range")
                                    .font(.themeSubheadline)
                                    .foregroundStyle(Theme.Adaptive.textSecondary)
                                Spacer()
                                Text("120-180 MAD")
                                    .font(.themeHeadline)
                            }

                            if viewModel.demoStep >= 2 {
                                HStack {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundStyle(Theme.Fairness.high)
                                    Text("200 MAD is above fair price")
                                        .font(.themeBody)
                                        .foregroundStyle(Theme.Fairness.high)
                                    Spacer()
                                }
                            }

                            if viewModel.demoStep >= 3 {
                                Text("Counter: \"150 dirhams?\" and walk away if needed.")
                                    .font(.themeBody)
                                    .foregroundStyle(Theme.Adaptive.textPrimary)
                                    .padding(Theme.Spacing.sm)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Theme.Adaptive.primary.opacity(0.1))
                                    .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.sm))
                            }
                        }
                        .padding(Theme.Spacing.md)
                        .background(Theme.Adaptive.surface)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }

                    if viewModel.demoStep < 3 {
                        Button("Show \(["Price Check", "Verdict", "Counter Tip"][viewModel.demoStep])") {
                            withAnimation {
                                viewModel.advanceDemo()
                            }
                        }
                        .buttonStyle(.bordered)
                    } else {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Theme.Fairness.fair)
                            Text("That's how it works!")
                                .font(.themeHeadline)
                        }
                        .padding(Theme.Spacing.md)
                    }
                }
                .padding(Theme.Spacing.md)

                Spacer()
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
    }
}

// MARK: - Step 6: Privacy

private struct PrivacyStepView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.xl) {
                Spacer(minLength: Theme.Spacing.xl)

                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 60))
                    .foregroundStyle(Theme.Adaptive.primary)
                    .accessibilityHidden(true)

                VStack(spacing: Theme.Spacing.sm) {
                    Text("Your Privacy Matters")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                        .multilineTextAlignment(.center)

                    Text("We built this app with privacy first")
                        .font(.themeTitle3)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }

                Spacer(minLength: Theme.Spacing.lg)

                // Privacy points
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    PrivacyPointRow(
                        icon: "person.crop.circle.badge.xmark",
                        title: "No Accounts",
                        description: "No sign-up, no login, no tracking"
                    )

                    Divider()

                    PrivacyPointRow(
                        icon: "megaphone.fill",
                        title: "No Ads",
                        description: "No advertisements, ever"
                    )

                    Divider()

                    PrivacyPointRow(
                        icon: "location.fill",
                        title: "Location Stays on Device",
                        description: "Used only when you ask, never sent anywhere"
                    )

                    Divider()

                    PrivacyPointRow(
                        icon: "externaldrive.fill",
                        title: "Data Stays Local",
                        description: "All your favorites and settings stored on your phone"
                    )
                }
                .padding(Theme.Spacing.md)
                .background(Theme.Adaptive.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                Spacer()
            }
            .padding(.horizontal, Theme.Spacing.lg)
        }
    }
}

private struct PrivacyPointRow: View {
    let icon: String
    let title: String
    let description: String

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.md) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(Theme.Adaptive.primary)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(title)
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                Text(description)
                    .font(.themeBody)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
    }
}

// MARK: - Preview

#Preview {
    OnboardingView()
}
