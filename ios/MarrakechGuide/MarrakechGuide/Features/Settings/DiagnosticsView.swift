import SwiftUI

/// Diagnostics screen for troubleshooting and status information.
struct DiagnosticsView: View {
    @State private var contentStats = ContentStats()
    @State private var storageStats = StorageStats()
    @State private var isLoading = true
    @State private var showShareSheet = false
    @State private var debugReport: String = ""

    var body: some View {
        List {
            // MARK: - App Info
            Section("App Information") {
                DiagnosticRow(label: "App Version", value: Bundle.main.appVersion)
                DiagnosticRow(label: "iOS Version", value: UIDevice.current.systemVersion)
                DiagnosticRow(label: "Device", value: UIDevice.current.model)
            }

            // MARK: - Content Status
            Section("Content Status") {
                if isLoading {
                    HStack {
                        ProgressView()
                        Text("Loading...")
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                } else {
                    DiagnosticRow(label: "Content Version", value: contentStats.version)
                    DiagnosticRow(label: "Last Updated", value: contentStats.lastUpdated)
                    DiagnosticRow(label: "Places", value: "\(contentStats.placesCount)")
                    DiagnosticRow(label: "Price Cards", value: "\(contentStats.priceCardsCount)")
                    DiagnosticRow(label: "Phrases", value: "\(contentStats.phrasesCount)")
                    DiagnosticRow(label: "Tips", value: "\(contentStats.tipsCount)")
                }
            }

            // MARK: - Pack Status
            Section("Pack Status") {
                PackStatusRow(name: "Base Pack", isInstalled: true, version: "2026.02")
                PackStatusRow(name: "Medina Map", isInstalled: false, version: nil)
                PackStatusRow(name: "Audio Pack", isInstalled: false, version: nil)
            }

            // MARK: - Offline Readiness
            Section("Offline Readiness") {
                ReadinessRow(name: "Core Content", isReady: true)
                ReadinessRow(name: "Search Index", isReady: true)
                ReadinessRow(name: "Home Base", isReady: false)

                Button {
                    showAirplaneModePrompt()
                } label: {
                    Label("Test Offline Mode", systemImage: "airplane")
                }
            }

            // MARK: - Storage
            Section("Storage") {
                DiagnosticRow(label: "Total App Storage", value: storageStats.formattedTotal)
                DiagnosticRow(label: "Content Database", value: storageStats.formattedContent)
                DiagnosticRow(label: "User Data", value: storageStats.formattedUser)
                DiagnosticRow(label: "Cache", value: storageStats.formattedCache)

                Button("Clear Cache") {
                    clearCache()
                }
                .foregroundStyle(Theme.Adaptive.primary)
            }

            // MARK: - Debug Report
            Section {
                Button {
                    generateDebugReport()
                } label: {
                    Label("Export Debug Report", systemImage: "doc.text")
                }
            } footer: {
                Text("The debug report contains app version, device info, and content status. No personal data or location information is included.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Diagnostics")
        .task {
            await loadStats()
        }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(items: [debugReport])
        }
    }

    private func loadStats() async {
        isLoading = true

        // Simulate loading content stats
        try? await Task.sleep(nanoseconds: 500_000_000)

        contentStats = ContentStats(
            version: "2026.02.01",
            lastUpdated: "Feb 8, 2026",
            placesCount: 47,
            priceCardsCount: 23,
            phrasesCount: 85,
            tipsCount: 12
        )

        storageStats = StorageStats(
            totalBytes: 7_340_032,
            contentBytes: 5_242_880,
            userBytes: 204_800,
            cacheBytes: 1_892_352
        )

        isLoading = false
    }

    private func showAirplaneModePrompt() {
        // Would show an alert suggesting to enable airplane mode
    }

    private func clearCache() {
        // TODO: Clear cache files
        storageStats.cacheBytes = 0
    }

    private func generateDebugReport() {
        debugReport = """
        Marrakech Guide Debug Report
        Generated: \(Date().formatted())

        === App Information ===
        Version: \(Bundle.main.appVersion)
        iOS Version: \(UIDevice.current.systemVersion)
        Device: \(UIDevice.current.model)

        === Content Status ===
        Content Version: \(contentStats.version)
        Last Updated: \(contentStats.lastUpdated)
        Places: \(contentStats.placesCount)
        Price Cards: \(contentStats.priceCardsCount)
        Phrases: \(contentStats.phrasesCount)
        Tips: \(contentStats.tipsCount)

        === Pack Status ===
        Base Pack: Installed (v2026.02)
        Medina Map: Not Installed
        Audio Pack: Not Installed

        === Storage ===
        Total: \(storageStats.formattedTotal)
        Content: \(storageStats.formattedContent)
        User: \(storageStats.formattedUser)
        Cache: \(storageStats.formattedCache)

        === Note ===
        No personal data or location information is included in this report.
        """

        showShareSheet = true
    }
}

// MARK: - Data Models

private struct ContentStats {
    var version: String = "-"
    var lastUpdated: String = "-"
    var placesCount: Int = 0
    var priceCardsCount: Int = 0
    var phrasesCount: Int = 0
    var tipsCount: Int = 0
}

private struct StorageStats {
    var totalBytes: Int64 = 0
    var contentBytes: Int64 = 0
    var userBytes: Int64 = 0
    var cacheBytes: Int64 = 0

    var formattedTotal: String {
        ByteCountFormatter.string(fromByteCount: totalBytes, countStyle: .file)
    }

    var formattedContent: String {
        ByteCountFormatter.string(fromByteCount: contentBytes, countStyle: .file)
    }

    var formattedUser: String {
        ByteCountFormatter.string(fromByteCount: userBytes, countStyle: .file)
    }

    var formattedCache: String {
        ByteCountFormatter.string(fromByteCount: cacheBytes, countStyle: .file)
    }
}

// MARK: - Helper Views

private struct DiagnosticRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(Theme.Adaptive.textPrimary)
            Spacer()
            Text(value)
                .foregroundStyle(Theme.Adaptive.textSecondary)
        }
    }
}

private struct PackStatusRow: View {
    let name: String
    let isInstalled: Bool
    let version: String?

    var body: some View {
        HStack {
            Text(name)
                .foregroundStyle(Theme.Adaptive.textPrimary)
            Spacer()
            if isInstalled {
                HStack(spacing: Theme.Spacing.xs) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Theme.Fairness.fair)
                    if let version = version {
                        Text("v\(version)")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                }
            } else {
                Text("Not Installed")
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
        }
    }
}

private struct ReadinessRow: View {
    let name: String
    let isReady: Bool

    var body: some View {
        HStack {
            Text(name)
                .foregroundStyle(Theme.Adaptive.textPrimary)
            Spacer()
            Image(systemName: isReady ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundStyle(isReady ? Theme.Fairness.fair : Theme.Adaptive.textSecondary)
        }
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// MARK: - Preview

#Preview {
    NavigationStack {
        DiagnosticsView()
    }
}
