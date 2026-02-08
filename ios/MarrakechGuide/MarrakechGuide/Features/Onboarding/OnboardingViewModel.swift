import Foundation
import Combine

/// Steps in the onboarding flow.
enum OnboardingStep: Int, CaseIterable, Identifiable {
    case welcome
    case offlinePromise
    case downloads
    case readinessCheck
    case demo
    case privacy

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .welcome: return "Welcome"
        case .offlinePromise: return "Works Offline"
        case .downloads: return "Get Ready"
        case .readinessCheck: return "Ready Check"
        case .demo: return "Quick Demo"
        case .privacy: return "Your Privacy"
        }
    }

    var progress: Double {
        Double(rawValue + 1) / Double(OnboardingStep.allCases.count)
    }
}

/// Supported UI languages.
enum AppLanguage: String, CaseIterable, Identifiable {
    case english = "en"
    case french = "fr"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .english: return "English"
        case .french: return "Fran\u{00E7}ais"
        }
    }
}

/// Supported home currencies.
enum HomeCurrency: String, CaseIterable, Identifiable {
    case usd = "USD"
    case eur = "EUR"
    case gbp = "GBP"
    case cad = "CAD"
    case aud = "AUD"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .usd: return "USD - US Dollar"
        case .eur: return "EUR - Euro"
        case .gbp: return "GBP - British Pound"
        case .cad: return "CAD - Canadian Dollar"
        case .aud: return "AUD - Australian Dollar"
        }
    }

    var symbol: String {
        switch self {
        case .usd: return "$"
        case .eur: return "\u{20AC}"
        case .gbp: return "\u{00A3}"
        case .cad: return "C$"
        case .aud: return "A$"
        }
    }
}

/// Readiness check item.
struct ReadinessItem: Identifiable {
    let id = UUID()
    let title: String
    var isReady: Bool
    var count: Int?
}

/// ViewModel for the onboarding flow.
@MainActor
final class OnboardingViewModel: ObservableObject {

    // MARK: - Published State

    @Published var currentStep: OnboardingStep = .welcome
    @Published var selectedLanguage: AppLanguage = .english
    @Published var selectedCurrency: HomeCurrency = .usd

    // Downloads
    @Published var isDownloadingBasePack = false
    @Published var basePackProgress: Double = 0
    @Published var basePackDownloaded = false

    // Readiness Check
    @Published var readinessItems: [ReadinessItem] = []
    @Published var isCheckingReadiness = false
    @Published var readinessComplete = false

    // Demo
    @Published var demoStep: Int = 0

    // Completion
    @Published var isComplete = false

    // MARK: - Private

    private let userDefaults = UserDefaults.standard
    private static let onboardingCompleteKey = "onboardingComplete"

    // MARK: - Init

    init() {
        // Check if onboarding was previously completed
        isComplete = userDefaults.bool(forKey: Self.onboardingCompleteKey)
    }

    // MARK: - Navigation

    var canGoBack: Bool {
        currentStep.rawValue > 0
    }

    var canGoNext: Bool {
        switch currentStep {
        case .welcome:
            return true
        case .offlinePromise:
            return true
        case .downloads:
            return true // Can always skip downloads
        case .readinessCheck:
            return readinessComplete || readinessItems.allSatisfy { $0.isReady }
        case .demo:
            return true
        case .privacy:
            return true
        }
    }

    var nextButtonTitle: String {
        switch currentStep {
        case .privacy:
            return "Get Started"
        case .downloads where !basePackDownloaded:
            return "Skip for Now"
        default:
            return "Continue"
        }
    }

    func goToNextStep() {
        guard let nextIndex = OnboardingStep.allCases.firstIndex(of: currentStep),
              nextIndex + 1 < OnboardingStep.allCases.count else {
            completeOnboarding()
            return
        }

        withAnimation(.easeInOut(duration: 0.3)) {
            currentStep = OnboardingStep.allCases[nextIndex + 1]
        }

        // Auto-start readiness check when entering that step
        if currentStep == .readinessCheck {
            Task { await performReadinessCheck() }
        }
    }

    func goToPreviousStep() {
        guard let currentIndex = OnboardingStep.allCases.firstIndex(of: currentStep),
              currentIndex > 0 else { return }

        withAnimation(.easeInOut(duration: 0.3)) {
            currentStep = OnboardingStep.allCases[currentIndex - 1]
        }
    }

    func skipToEnd() {
        completeOnboarding()
    }

    // MARK: - Downloads

    func downloadBasePack() async {
        guard !basePackDownloaded else { return }

        isDownloadingBasePack = true
        basePackProgress = 0

        // Simulate download progress
        for i in 1...10 {
            try? await Task.sleep(nanoseconds: 200_000_000) // 0.2s
            basePackProgress = Double(i) / 10.0
        }

        isDownloadingBasePack = false
        basePackDownloaded = true
    }

    // MARK: - Readiness Check

    func performReadinessCheck() async {
        isCheckingReadiness = true
        readinessComplete = false

        // Initialize items
        readinessItems = [
            ReadinessItem(title: "Places loaded", isReady: false, count: nil),
            ReadinessItem(title: "Price cards loaded", isReady: false, count: nil),
            ReadinessItem(title: "Phrases loaded", isReady: false, count: nil),
            ReadinessItem(title: "Search ready", isReady: false, count: nil)
        ]

        // Simulate checking each item
        for i in 0..<readinessItems.count {
            try? await Task.sleep(nanoseconds: 400_000_000) // 0.4s

            readinessItems[i].isReady = true

            // Set counts for relevant items
            switch i {
            case 0: readinessItems[i].count = 47  // Places
            case 1: readinessItems[i].count = 23  // Price cards
            case 2: readinessItems[i].count = 85  // Phrases
            default: break
            }
        }

        isCheckingReadiness = false
        readinessComplete = true
    }

    // MARK: - Demo

    func advanceDemo() {
        if demoStep < 3 {
            demoStep += 1
        }
    }

    func resetDemo() {
        demoStep = 0
    }

    // MARK: - Completion

    private func completeOnboarding() {
        userDefaults.set(true, forKey: Self.onboardingCompleteKey)
        isComplete = true
    }

    /// Reset onboarding for re-running from Settings.
    func resetOnboarding() {
        userDefaults.set(false, forKey: Self.onboardingCompleteKey)
        isComplete = false
        currentStep = .welcome
        basePackDownloaded = false
        basePackProgress = 0
        readinessItems = []
        readinessComplete = false
        demoStep = 0
    }

    /// Check if onboarding should be shown.
    static func shouldShowOnboarding() -> Bool {
        !UserDefaults.standard.bool(forKey: onboardingCompleteKey)
    }
}
