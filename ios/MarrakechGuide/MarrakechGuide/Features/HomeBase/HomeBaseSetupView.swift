import SwiftUI
import CoreLocation

/// View for setting up or changing the user's home base location.
///
/// Options:
/// - Use current location
/// - Enter manually
struct HomeBaseSetupView: View {

    let settingsRepository: UserSettingsRepository
    let onComplete: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var address = ""
    @State private var latitude = ""
    @State private var longitude = ""

    @State private var isUsingCurrentLocation = false
    @State private var isSaving = false
    @State private var errorMessage: String?

    private let locationService = LocationServiceImpl.shared

    var body: some View {
        NavigationStack {
            Form {
                // Location method section
                Section {
                    Button {
                        useCurrentLocation()
                    } label: {
                        HStack {
                            Image(systemName: "location.fill")
                                .foregroundColor(.blue)

                            Text("Use Current Location")

                            Spacer()

                            if isUsingCurrentLocation {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(isUsingCurrentLocation)
                } header: {
                    Text("Quick Setup")
                } footer: {
                    Text("Stand at your riad or hotel and tap to save this location.")
                }

                // Manual entry section
                Section {
                    TextField("Name (e.g., Riad Dar Maya)", text: $name)
                        .textContentType(.organizationName)
                        .autocorrectionDisabled()

                    TextField("Address (optional)", text: $address)
                        .textContentType(.fullStreetAddress)
                } header: {
                    Text("Details")
                }

                // Coordinates section (for manual entry)
                Section {
                    TextField("Latitude", text: $latitude)
                        .keyboardType(.decimalPad)

                    TextField("Longitude", text: $longitude)
                        .keyboardType(.decimalPad)
                } header: {
                    Text("Coordinates")
                } footer: {
                    Text("You can find coordinates from Google Maps or your booking confirmation.")
                }

                // Tips section
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        tipRow(icon: "building.2", text: "Enter your riad or hotel name")
                        tipRow(icon: "location", text: "Coordinates help the compass point accurately")
                        tipRow(icon: "lock.shield", text: "Location is stored only on your device")
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("Tips")
                }
            }
            .navigationTitle("Set Home Base")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveHomeBase()
                    }
                    .disabled(!canSave || isSaving)
                }
            }
            .alert("Error", isPresented: .constant(errorMessage != nil)) {
                Button("OK") { errorMessage = nil }
            } message: {
                if let error = errorMessage {
                    Text(error)
                }
            }
        }
    }

    // MARK: - Subviews

    private func tipRow(icon: String, text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(.orange)
                .frame(width: 24)

            Text(text)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    // MARK: - Computed Properties

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !latitude.isEmpty &&
        !longitude.isEmpty &&
        Double(latitude) != nil &&
        Double(longitude) != nil
    }

    // MARK: - Actions

    private func useCurrentLocation() {
        Task {
            isUsingCurrentLocation = true
            defer { isUsingCurrentLocation = false }

            // Request permission if needed
            if !locationService.isAuthorized {
                let granted = await locationService.requestPermission()
                if !granted {
                    errorMessage = "Location permission is required to use current location."
                    return
                }
            }

            // Get current location
            do {
                let location = try await locationService.refreshLocation()
                latitude = String(format: "%.6f", location.coordinate.latitude)
                longitude = String(format: "%.6f", location.coordinate.longitude)
            } catch {
                errorMessage = "Couldn't get your location: \(error.localizedDescription)"
            }
        }
    }

    private func saveHomeBase() {
        guard let lat = Double(latitude),
              let lng = Double(longitude) else {
            errorMessage = "Invalid coordinates"
            return
        }

        // Validate coordinates are in reasonable range
        guard lat >= -90 && lat <= 90 else {
            errorMessage = "Latitude must be between -90 and 90"
            return
        }

        guard lng >= -180 && lng <= 180 else {
            errorMessage = "Longitude must be between -180 and 180"
            return
        }

        // Warn if coordinates are far from Marrakech
        let marrakechLat = 31.6295
        let marrakechLng = -7.9891
        let distanceFromMarrakech = sqrt(pow(lat - marrakechLat, 2) + pow(lng - marrakechLng, 2))

        if distanceFromMarrakech > 1.0 {
            // More than ~100km from Marrakech center
            // Could show a warning, but we'll allow it for now
        }

        isSaving = true

        Task {
            defer { isSaving = false }

            let homeBase = HomeBase(
                name: name.trimmingCharacters(in: .whitespaces),
                lat: lat,
                lng: lng,
                address: address.isEmpty ? nil : address.trimmingCharacters(in: .whitespaces)
            )

            do {
                try await settingsRepository.setHomeBase(homeBase)
                dismiss()
                onComplete()
            } catch {
                errorMessage = "Failed to save: \(error.localizedDescription)"
            }
        }
    }
}

// MARK: - Previews

#Preview {
    HomeBaseSetupView(
        settingsRepository: MockUserSettingsRepository(),
        onComplete: {}
    )
}

// Mock for previews
private class MockUserSettingsRepository: UserSettingsRepository {
    func getSetting<T: Decodable>(key: SettingKey) async throws -> T? { nil }
    func setSetting<T: Encodable>(key: SettingKey, value: T) async throws {}
    func deleteSetting(key: SettingKey) async throws {}
    func getHomeBase() async throws -> HomeBase? { nil }
    func setHomeBase(_ homeBase: HomeBase) async throws {}
    func getExchangeRate() async throws -> ExchangeRate? { nil }
    func setExchangeRate(_ rate: ExchangeRate) async throws {}
    func isOnboardingComplete() async throws -> Bool { false }
    func setOnboardingComplete(_ complete: Bool) async throws {}
}
