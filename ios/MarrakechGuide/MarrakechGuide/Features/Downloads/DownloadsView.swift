import SwiftUI

/// View for managing content pack downloads.
struct DownloadsView: View {
    @StateObject private var viewModel = DownloadsViewModel()
    @State private var packToRemove: ContentPack?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                // Storage info card
                StorageInfoCard(
                    availableSpace: viewModel.formattedAvailableSpace,
                    wifiOnly: viewModel.preferences.wifiOnly,
                    onWifiOnlyChange: { viewModel.updateWifiOnly($0) }
                )

                // Pack list
                ForEach(viewModel.packItems) { item in
                    PackCard(
                        pack: item.pack,
                        state: item.state,
                        onDownload: { Task { await viewModel.startDownload(packId: item.pack.id) } },
                        onPause: { Task { await viewModel.pauseDownload(packId: item.pack.id) } },
                        onResume: { Task { await viewModel.resumeDownload(packId: item.pack.id) } },
                        onCancel: { Task { await viewModel.cancelDownload(packId: item.pack.id) } },
                        onRemove: { packToRemove = item.pack }
                    )
                }
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .navigationTitle("Downloads")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if viewModel.isRefreshing {
                    ProgressView()
                } else {
                    Button {
                        Task { await viewModel.checkForUpdates() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .task {
            await viewModel.loadPacks()
        }
        .alert("Remove Pack?", isPresented: Binding(
            get: { packToRemove != nil },
            set: { if !$0 { packToRemove = nil } }
        )) {
            Button("Cancel", role: .cancel) {
                packToRemove = nil
            }
            Button("Remove", role: .destructive) {
                if let pack = packToRemove {
                    Task { await viewModel.removePack(packId: pack.id) }
                }
                packToRemove = nil
            }
        } message: {
            if let pack = packToRemove {
                Text("This will free up \(pack.formattedSize) of storage. You can download it again later.")
            }
        }
    }
}

// MARK: - View Model

@MainActor
final class DownloadsViewModel: ObservableObject {
    @Published var packItems: [PackDisplayItem] = []
    @Published var preferences = DownloadPreferences()
    @Published var isRefreshing = false

    private let downloadService: DownloadService = DownloadServiceImpl.shared

    var formattedAvailableSpace: String {
        ByteCountFormatter.string(fromByteCount: downloadService.getAvailableSpace(), countStyle: .file)
    }

    func loadPacks() async {
        let packs = await downloadService.availablePacks
        let states = await downloadService.packStates
        preferences = await downloadService.preferences

        packItems = packs.map { pack in
            PackDisplayItem(
                pack: pack,
                state: states[pack.id] ?? PackState(packId: pack.id, status: .notDownloaded)
            )
        }
    }

    func startDownload(packId: String) async {
        _ = await downloadService.startDownload(packId: packId)
        await loadPacks()
    }

    func pauseDownload(packId: String) async {
        await downloadService.pauseDownload(packId: packId)
        await loadPacks()
    }

    func resumeDownload(packId: String) async {
        _ = await downloadService.resumeDownload(packId: packId)
        await loadPacks()
    }

    func cancelDownload(packId: String) async {
        await downloadService.cancelDownload(packId: packId)
        await loadPacks()
    }

    func removePack(packId: String) async {
        await downloadService.removePack(packId: packId)
        await loadPacks()
    }

    func checkForUpdates() async {
        isRefreshing = true
        await downloadService.checkForUpdates()
        await loadPacks()
        isRefreshing = false
    }

    func updateWifiOnly(_ enabled: Bool) {
        preferences.wifiOnly = enabled
        downloadService.updatePreferences(preferences)
    }
}

// MARK: - Display Item

struct PackDisplayItem: Identifiable {
    let pack: ContentPack
    let state: PackState

    var id: String { pack.id }
}

// MARK: - Storage Info Card

private struct StorageInfoCard: View {
    let availableSpace: String
    let wifiOnly: Bool
    let onWifiOnlyChange: (Bool) -> Void

    var body: some View {
        ContentCard(title: "Storage", subtitle: nil) {
            VStack(spacing: Theme.Spacing.sm) {
                HStack {
                    Text("Available")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                    Spacer()
                    Text(availableSpace)
                        .font(.themeBody.weight(.semibold))
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                }

                Toggle(isOn: Binding(get: { wifiOnly }, set: onWifiOnlyChange)) {
                    Text("Download on Wi-Fi only")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textPrimary)
                }
            }
        }
    }
}

// MARK: - Pack Card

private struct PackCard: View {
    let pack: ContentPack
    let state: PackState
    let onDownload: () -> Void
    let onPause: () -> Void
    let onResume: () -> Void
    let onCancel: () -> Void
    let onRemove: () -> Void

    var body: some View {
        ContentCard(title: pack.displayName, subtitle: pack.description) {
            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                // Status and icon row
                HStack {
                    packIcon
                        .font(.title2)
                        .foregroundStyle(Theme.Adaptive.primary)

                    Spacer()

                    statusBadge
                }

                // Progress bar (if downloading)
                if state.status == .downloading {
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        ProgressView(value: state.progress)
                            .tint(Theme.Adaptive.primary)

                        HStack {
                            Text("\(state.progressPercent)%")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                            Spacer()
                            Text("\(formatBytes(state.downloadedBytes)) / \(pack.formattedSize)")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                        }
                    }
                }

                // Error message
                if state.status == .failed, let error = state.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.themeCaption)
                        .foregroundStyle(.red)
                }

                // Size and version info
                HStack {
                    Text(pack.formattedSize)
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                    Spacer()
                    if let version = state.installedVersion {
                        Text("v\(version)")
                            .font(.themeCaption)
                            .foregroundStyle(Theme.Adaptive.textSecondary)
                    }
                }

                // Action buttons
                actionButtons
            }
        }
    }

    private var packIcon: some View {
        Image(systemName: pack.type.iconName)
    }

    @ViewBuilder
    private var statusBadge: some View {
        switch state.status {
        case .notDownloaded:
            EmptyView()
        case .queued:
            StatusLabel(text: "Queued", color: .orange)
        case .downloading:
            StatusLabel(text: "Downloading", color: Theme.Adaptive.primary)
        case .paused:
            StatusLabel(text: "Paused", color: .secondary)
        case .verifying:
            StatusLabel(text: "Verifying", color: .orange)
        case .installing:
            StatusLabel(text: "Installing", color: .orange)
        case .installed:
            Label("Installed", systemImage: "checkmark.circle.fill")
                .font(.themeCaption)
                .foregroundStyle(Theme.Fairness.fair)
        case .updateAvailable:
            StatusLabel(text: "Update", color: .orange)
        case .failed:
            StatusLabel(text: "Failed", color: .red)
        }
    }

    @ViewBuilder
    private var actionButtons: some View {
        HStack(spacing: Theme.Spacing.sm) {
            switch state.status {
            case .notDownloaded, .failed:
                Button {
                    onDownload()
                } label: {
                    Label("Download", systemImage: "arrow.down.circle")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.borderedProminent)

            case .queued, .downloading:
                Button {
                    onPause()
                } label: {
                    Label("Pause", systemImage: "pause.circle")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)

                Button {
                    onCancel()
                } label: {
                    Image(systemName: "xmark.circle")
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .tint(.red)

            case .paused:
                Button {
                    onResume()
                } label: {
                    Label("Resume", systemImage: "play.circle")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.borderedProminent)

                Button {
                    onCancel()
                } label: {
                    Image(systemName: "xmark.circle")
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .tint(.red)

            case .verifying, .installing:
                HStack {
                    Spacer()
                    ProgressView()
                    Text(state.status == .verifying ? "Verifying..." : "Installing...")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                    Spacer()
                }
                .frame(minHeight: 44)

            case .installed:
                Button {
                    onRemove()
                } label: {
                    Label("Remove", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .tint(.red)

            case .updateAvailable:
                Button {
                    onDownload()
                } label: {
                    Label("Update", systemImage: "arrow.clockwise")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.borderedProminent)

                Button {
                    onRemove()
                } label: {
                    Image(systemName: "trash")
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .tint(.red)
            }
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}

// MARK: - Status Label

private struct StatusLabel: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.themeCaption)
            .foregroundStyle(color)
    }
}

// MARK: - Pack Type Extension

private extension PackType {
    var iconName: String {
        switch self {
        case .medinaMap, .guelizMap:
            return "map"
        case .audioPhrases:
            return "waveform"
        case .highResImages:
            return "photo"
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        DownloadsView()
    }
}
