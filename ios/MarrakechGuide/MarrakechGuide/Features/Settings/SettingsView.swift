import SwiftUI

/// Main settings screen with user preferences and app information.
struct SettingsView: View {
    @AppStorage("preferredCurrency") private var preferredCurrency = "MAD"
    @AppStorage("preferredLanguage") private var preferredLanguage = "en"
    @AppStorage("wifiOnlyDownloads") private var wifiOnlyDownloads = true
    @State private var showingClearAlert = false
    @State private var storageUsed: String = "Calculating..."

    var body: some View {
        List {
            // MARK: - General
            Section("General") {
                Picker("Language", selection: $preferredLanguage) {
                    Text("English").tag("en")
                    Text("Fran\u{00E7}ais").tag("fr")
                }

                Picker("Home Currency", selection: $preferredCurrency) {
                    Text("MAD (Moroccan Dirham)").tag("MAD")
                    Text("USD (US Dollar)").tag("USD")
                    Text("EUR (Euro)").tag("EUR")
                    Text("GBP (British Pound)").tag("GBP")
                }
            }

            // MARK: - Offline & Downloads
            Section("Offline & Downloads") {
                NavigationLink {
                    DownloadsView()
                } label: {
                    Label("Downloaded Content", systemImage: "arrow.down.circle")
                }

                Toggle("Wi-Fi Only Downloads", isOn: $wifiOnlyDownloads)

                HStack {
                    Text("Storage Used")
                    Spacer()
                    Text(storageUsed)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }

            // MARK: - Home Base
            Section("Home Base") {
                NavigationLink {
                    HomeBaseSettingsView()
                } label: {
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("Your Hotel/Riad")
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                        Text("Not set")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                }
            }

            // MARK: - Privacy
            Section("Privacy") {
                NavigationLink {
                    PrivacyCenterView()
                } label: {
                    Label("Privacy Center", systemImage: "hand.raised")
                }

                HStack {
                    Text("Location Permission")
                    Spacer()
                    Text("When In Use")
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
            }

            // MARK: - Data
            Section("Data") {
                Button("Clear Recent History") {
                    // TODO: Clear recents
                }
                .foregroundStyle(Theme.Adaptive.textPrimary)

                Button("Clear All Saved Items") {
                    showingClearAlert = true
                }
                .foregroundStyle(Theme.Semantic.error)
            }

            // MARK: - About
            Section("About") {
                NavigationLink {
                    DiagnosticsView()
                } label: {
                    HStack {
                        Text("Diagnostics")
                        Spacer()
                        Text(Bundle.main.appVersion)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                }

                NavigationLink {
                    LicensesView()
                } label: {
                    Text("Open Source Licenses")
                }
            }

            // MARK: - Support
            Section("Support") {
                Button {
                    openEmail()
                } label: {
                    Label("Report an Issue", systemImage: "envelope")
                }
                .foregroundStyle(Theme.Adaptive.textPrimary)

                Button {
                    openAppStore()
                } label: {
                    Label("Rate the App", systemImage: "star")
                }
                .foregroundStyle(Theme.Adaptive.textPrimary)

                NavigationLink {
                    RerunOnboardingView()
                } label: {
                    Label("Run Setup Again", systemImage: "arrow.counterclockwise")
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Settings")
        .alert("Clear Saved Items?", isPresented: $showingClearAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Clear All", role: .destructive) {
                // TODO: Clear all saved items
            }
        } message: {
            Text("This will remove all your saved places and price cards. This cannot be undone.")
        }
        .task {
            await calculateStorage()
        }
    }

    private func calculateStorage() async {
        // Simulate storage calculation
        try? await Task.sleep(nanoseconds: 500_000_000)
        let contentSize = 5.2
        let userSize = 0.2
        let cacheSize = 1.5
        let total = contentSize + userSize + cacheSize
        storageUsed = String(format: "%.1f MB", total)
    }

    private func openEmail() {
        let subject = "Marrakech Guide Feedback"
        let body = "App Version: \(Bundle.main.appVersion)\niOS Version: \(UIDevice.current.systemVersion)\n\n"
        let urlString = "mailto:support@marrakechguide.app?subject=\(subject)&body=\(body)"
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""

        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }

    private func openAppStore() {
        // Placeholder - would use actual App Store ID
        if let url = URL(string: "https://apps.apple.com/app/idXXXXXXXXX") {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Downloads View

struct DownloadsView: View {
    var body: some View {
        List {
            Section {
                DownloadPackRow(
                    name: "Base Pack",
                    status: .installed,
                    size: "12 MB",
                    version: "2026.02"
                )

                DownloadPackRow(
                    name: "Medina Map Pack",
                    status: .notInstalled,
                    size: "45 MB",
                    version: nil
                )

                DownloadPackRow(
                    name: "Audio Pack",
                    status: .notInstalled,
                    size: "30 MB",
                    version: nil
                )
            }

            Section {
                Text("Content packs are downloaded over Wi-Fi by default. You can change this in Settings.")
                    .font(.themeCaption)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Downloads")
    }
}

enum PackStatus {
    case installed
    case notInstalled
    case downloading(Double)
}

private struct DownloadPackRow: View {
    let name: String
    let status: PackStatus
    let size: String
    let version: String?

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(name)
                    .font(.themeBody)
                    .foregroundStyle(Theme.Adaptive.textPrimary)

                HStack(spacing: Theme.Spacing.sm) {
                    Text(size)
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)

                    if let version = version {
                        Text("v\(version)")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                }
            }

            Spacer()

            switch status {
            case .installed:
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Theme.Fairness.fair)
            case .notInstalled:
                Button("Download") {
                    // TODO: Start download
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            case .downloading(let progress):
                ProgressView(value: progress)
                    .frame(width: 60)
            }
        }
    }
}

// MARK: - Home Base Settings

struct HomeBaseSettingsView: View {
    @State private var homeBaseName = ""
    @State private var useCurrentLocation = false

    var body: some View {
        List {
            Section {
                TextField("Hotel/Riad Name", text: $homeBaseName)

                Toggle("Use Current Location", isOn: $useCurrentLocation)
            } footer: {
                Text("Your Home Base is used by the compass feature to help you find your way back.")
            }

            Section {
                Button("Save Home Base") {
                    // TODO: Save home base
                }
                .frame(maxWidth: .infinity)

                if !homeBaseName.isEmpty {
                    Button("Remove Home Base", role: .destructive) {
                        homeBaseName = ""
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Home Base")
    }
}

// MARK: - Rerun Onboarding

struct RerunOnboardingView: View {
    @State private var showOnboarding = false

    var body: some View {
        VStack(spacing: Theme.Spacing.lg) {
            Image(systemName: "arrow.counterclockwise.circle")
                .font(.system(size: 60))
                .foregroundStyle(Theme.Adaptive.primary)

            Text("Run Setup Again")
                .font(.themeTitle2)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("This will show you the initial setup screens again, including language selection and the offline features demo.")
                .font(.themeBody)
                .foregroundStyle(Theme.Adaptive.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            Button("Start Setup") {
                showOnboarding = true
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding()
        .navigationTitle("Setup")
        .fullScreenCover(isPresented: $showOnboarding) {
            OnboardingView()
        }
    }
}

// MARK: - Licenses View

struct LicensesView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                LicenseCard(
                    name: "GRDB.swift",
                    license: "MIT License",
                    description: "SQLite toolkit for iOS, providing a Swift interface to SQLite databases."
                )

                LicenseCard(
                    name: "Swift Collections",
                    license: "Apache License 2.0",
                    description: "Data structures package from Apple."
                )
            }
            .padding(Theme.Spacing.md)
        }
        .navigationTitle("Licenses")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LicenseCard: View {
    let name: String
    let license: String
    let description: String

    var body: some View {
        ContentCard(title: name) {
            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                Chip(text: license, style: .outlined, tint: Theme.Adaptive.primary)

                Text(description)
                    .font(.themeBody)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
    }
}

// MARK: - Bundle Extension

extension Bundle {
    var appVersion: String {
        let version = infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        SettingsView()
    }
}
