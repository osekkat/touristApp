import SwiftUI

/// Main compass view for navigating back to home base.
///
/// Shows:
/// - Compass arrow pointing to home
/// - Distance and estimated walk time
/// - Home base name and location
/// - Heading confidence indicator
/// - Actions: Refresh, Show to Taxi Driver
struct GoHomeView: View {

    @StateObject private var viewModel: HomeBaseViewModel
    @State private var showingTaxiCard = false
    @State private var showingSetup = false

    init(settingsRepository: UserSettingsRepository) {
        _viewModel = StateObject(wrappedValue: HomeBaseViewModel(settingsRepository: settingsRepository))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                if viewModel.isLoading {
                    ProgressView()
                } else if viewModel.homeBase == nil {
                    noHomeBaseView
                } else if !viewModel.hasLocationPermission {
                    permissionRequiredView
                } else {
                    compassView
                }
            }
            .navigationTitle("Go Home")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if viewModel.homeBase != nil {
                    ToolbarItem(placement: .primaryAction) {
                        Menu {
                            Button {
                                showingSetup = true
                            } label: {
                                Label("Change Home Base", systemImage: "pencil")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                    }
                }
            }
            .task {
                await viewModel.load()
            }
            .onAppear {
                viewModel.startTracking()
            }
            .onDisappear {
                viewModel.stopTracking()
            }
            .sheet(isPresented: $showingTaxiCard) {
                if let homeBase = viewModel.homeBase {
                    TaxiDriverCardView(homeBase: homeBase)
                }
            }
            .sheet(isPresented: $showingSetup) {
                HomeBaseSetupView(
                    settingsRepository: viewModel.settingsRepository,
                    onComplete: {
                        showingSetup = false
                        Task { await viewModel.load() }
                    }
                )
            }
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                if let error = viewModel.errorMessage {
                    Text(error)
                }
            }
        }
    }

    // MARK: - Compass View

    private var compassView: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Home base name
                if let homeBase = viewModel.homeBase {
                    VStack(spacing: 4) {
                        Text(homeBase.name)
                            .font(.title2)
                            .fontWeight(.semibold)

                        if let address = homeBase.address {
                            Text(address)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.top, 16)
                }

                // Compass
                CompassArrowView(
                    rotationDegrees: viewModel.arrowRotation,
                    confidence: viewModel.headingConfidence,
                    size: 220
                )
                .padding(.vertical, 20)

                // Distance and time
                HStack(spacing: 40) {
                    VStack(spacing: 4) {
                        Text(viewModel.formattedDistance)
                            .font(.system(size: 32, weight: .bold, design: .rounded))

                        Text("Distance")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    VStack(spacing: 4) {
                        Text(viewModel.estimatedWalkTime)
                            .font(.system(size: 32, weight: .bold, design: .rounded))

                        Text("Walk time")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                // Direction
                Text(viewModel.directionDescription)
                    .font(.headline)
                    .foregroundColor(.secondary)

                // Heading confidence
                headingConfidenceView

                Divider()
                    .padding(.horizontal)

                // Actions
                actionsView
            }
            .padding()
        }
    }

    private var headingConfidenceView: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(confidenceColor)
                .frame(width: 8, height: 8)

            Text(confidenceText)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    private var confidenceColor: Color {
        switch viewModel.headingConfidence {
        case .good: return .green
        case .weak: return .orange
        case .unavailable: return .gray
        }
    }

    private var confidenceText: String {
        switch viewModel.headingConfidence {
        case .good: return "Heading: Good"
        case .weak: return "Heading: Weak — move phone for better accuracy"
        case .unavailable: return "Heading: Unavailable"
        }
    }

    private var actionsView: some View {
        VStack(spacing: 12) {
            // Refresh button
            Button {
                Task { await viewModel.refreshLocation() }
            } label: {
                Label("Refresh Location", systemImage: "location.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(.blue)

            // Taxi driver card button
            Button {
                showingTaxiCard = true
            } label: {
                Label("Show to Taxi Driver", systemImage: "car")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.orange)
        }
        .padding(.horizontal)
    }

    // MARK: - No Home Base View

    private var noHomeBaseView: some View {
        VStack(spacing: 20) {
            Image(systemName: "house.circle")
                .font(.system(size: 60))
                .foregroundColor(.orange)

            Text("Set Your Home Base")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Save where you're staying so you can always find your way back.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                showingSetup = true
            } label: {
                Text("Set Home Base")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.orange)
            .padding(.horizontal, 40)
            .padding(.top, 10)
        }
        .padding()
    }

    // MARK: - Permission Required View

    private var permissionRequiredView: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.slash")
                .font(.system(size: 60))
                .foregroundColor(.orange)

            Text("Location Permission Needed")
                .font(.title2)
                .fontWeight(.semibold)

            Text("To show the compass direction to your home base, we need access to your location. Your location is only used on-device and never sent anywhere.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                Task {
                    await viewModel.requestPermission()
                }
            } label: {
                Text("Enable Location")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.blue)
            .padding(.horizontal, 40)

            if let homeBase = viewModel.homeBase {
                Divider()
                    .padding(.horizontal, 40)
                    .padding(.vertical, 10)

                Text("You can still use the taxi driver card")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Button {
                    showingTaxiCard = true
                } label: {
                    Label("Show to Taxi Driver", systemImage: "car")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.orange)
                .padding(.horizontal, 40)
            }
        }
        .padding()
    }
}

// MARK: - Extension for accessing repository

private extension HomeBaseViewModel {
    var settingsRepository: UserSettingsRepository {
        // This would normally come from dependency injection
        // For now, we access through the view model's internal reference
        fatalError("settingsRepository should be accessed through DI container")
    }
}
