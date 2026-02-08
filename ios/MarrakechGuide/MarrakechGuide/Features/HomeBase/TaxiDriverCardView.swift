import SwiftUI

/// Full-screen card optimized for showing to taxi drivers.
///
/// Features:
/// - Large Arabic text (RTL)
/// - Latin name below
/// - Address if available
/// - "Take me here" phrase in Darija
/// - High contrast, keeps screen awake
struct TaxiDriverCardView: View {

    let homeBase: HomeBase

    @Environment(\.dismiss) private var dismiss
    @State private var keepAwake = true

    var body: some View {
        NavigationStack {
            ZStack {
                // Background
                Color.white
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    Spacer()

                    // Main content
                    VStack(spacing: 32) {
                        // Arabic name (large, RTL)
                        if let arabicName = arabicTransliteration {
                            Text(arabicName)
                                .font(.system(size: 48, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                                .environment(\.layoutDirection, .rightToLeft)
                        }

                        // Latin name
                        Text(homeBase.name)
                            .font(.system(size: 36, weight: .semibold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)

                        // Address if available
                        if let address = homeBase.address {
                            Text(address)
                                .font(.system(size: 24))
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 20)
                        }

                        Divider()
                            .padding(.horizontal, 40)
                            .padding(.vertical, 20)

                        // Darija phrase
                        VStack(spacing: 12) {
                            // Arabic
                            Text("من فضلك، ديني لهنا")
                                .font(.system(size: 32, weight: .medium))
                                .foregroundColor(.orange)
                                .environment(\.layoutDirection, .rightToLeft)

                            // Latin transliteration
                            Text("Mn fadlik, dini l'hna")
                                .font(.system(size: 20))
                                .foregroundColor(.gray)

                            // English
                            Text("Please take me here")
                                .font(.system(size: 18))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal, 20)

                    Spacer()

                    // Tip
                    Text("Show this to the taxi driver")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.bottom, 40)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundColor(.orange)
                }

                ToolbarItem(placement: .primaryAction) {
                    Button {
                        shareCard()
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .foregroundColor(.orange)
                }
            }
            .onAppear {
                // Keep screen awake
                UIApplication.shared.isIdleTimerDisabled = keepAwake
            }
            .onDisappear {
                // Allow screen to sleep again
                UIApplication.shared.isIdleTimerDisabled = false
            }
        }
        .preferredColorScheme(.light) // Always light mode for high contrast
    }

    // MARK: - Computed Properties

    /// Attempt to provide Arabic transliteration of the name
    /// In a real app, this would come from the database
    private var arabicTransliteration: String? {
        // Common patterns for Marrakech accommodations
        let name = homeBase.name.lowercased()

        if name.contains("riad") {
            // Extract the part after "riad"
            let parts = name.replacingOccurrences(of: "riad ", with: "").capitalized
            return "رياض \(parts)"
        } else if name.contains("hotel") {
            let parts = name.replacingOccurrences(of: "hotel ", with: "").capitalized
            return "فندق \(parts)"
        } else if name.contains("dar") {
            let parts = name.replacingOccurrences(of: "dar ", with: "").capitalized
            return "دار \(parts)"
        }

        // Return name as-is if we can't transliterate
        return nil
    }

    // MARK: - Actions

    private func shareCard() {
        // Create shareable text
        var shareText = homeBase.name

        if let address = homeBase.address {
            shareText += "\n\(address)"
        }

        shareText += "\n\nمن فضلك، ديني لهنا"
        shareText += "\n(Please take me here)"

        // Add coordinates for maps
        let mapsURL = "https://maps.apple.com/?ll=\(homeBase.lat),\(homeBase.lng)"
        shareText += "\n\n\(mapsURL)"

        let activityVC = UIActivityViewController(
            activityItems: [shareText],
            applicationActivities: nil
        )

        // Present the share sheet
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            rootVC.present(activityVC, animated: true)
        }
    }
}

// MARK: - Previews

#Preview {
    TaxiDriverCardView(
        homeBase: HomeBase(
            name: "Riad Dar Maya",
            lat: 31.6295,
            lng: -7.9912,
            address: "12 Derb Sidi Bouloukate, Medina"
        )
    )
}

#Preview("Hotel") {
    TaxiDriverCardView(
        homeBase: HomeBase(
            name: "Hotel La Mamounia",
            lat: 31.6234,
            lng: -7.9956,
            address: "Avenue Bab Jdid"
        )
    )
}

#Preview("No Address") {
    TaxiDriverCardView(
        homeBase: HomeBase(
            name: "Dar Anika",
            lat: 31.6300,
            lng: -7.9900,
            address: nil
        )
    )
}
