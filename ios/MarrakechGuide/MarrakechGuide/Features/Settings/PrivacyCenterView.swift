import SwiftUI

/// Privacy Center explaining data practices in plain language.
struct PrivacyCenterView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.lg) {

                // MARK: - Hero
                VStack(spacing: Theme.Spacing.md) {
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 50))
                        .foregroundStyle(Theme.Adaptive.primary)

                    Text("Your Privacy Matters")
                        .font(.themeTitle2)
                        .foregroundStyle(Theme.Adaptive.textPrimary)

                    Text("Here's exactly what happens with your data")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, Theme.Spacing.lg)

                // MARK: - What's Stored on Device
                PrivacySection(
                    icon: "iphone",
                    title: "What's Stored on Your Device",
                    items: [
                        "Your saved places and recent views",
                        "Your Home Base location (if you set one)",
                        "App settings and preferences",
                        "Downloaded content packs"
                    ]
                )

                // MARK: - What Leaves Device
                PrivacySection(
                    icon: "arrow.up.circle",
                    title: "What Leaves Your Device",
                    items: [
                        "Nothing in version 1.0",
                        "Future versions may offer opt-in crash reports"
                    ],
                    highlight: "Nothing is shared without your explicit consent."
                )

                // MARK: - Location Data
                PrivacySection(
                    icon: "location.fill",
                    title: "Location Data",
                    items: [
                        "Used only when compass/navigation screens are open",
                        "Never sent to any server",
                        "Never stored beyond your current session",
                        "You can use the app without location access"
                    ]
                )

                // MARK: - No Accounts
                PrivacySection(
                    icon: "person.crop.circle.badge.xmark",
                    title: "No Accounts Required",
                    items: [
                        "No sign-up or login needed",
                        "No email collection",
                        "No data synced to any cloud",
                        "Your data stays on your device forever"
                    ]
                )

                // MARK: - No Tracking
                PrivacySection(
                    icon: "eye.slash.fill",
                    title: "No Tracking",
                    items: [
                        "No analytics SDKs",
                        "No advertising identifiers",
                        "No third-party trackers",
                        "No behavioral profiling"
                    ]
                )

                // MARK: - Summary Card
                ContentCard {
                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        Label("In Summary", systemImage: "checkmark.seal.fill")
                            .font(.themeHeadline)
                            .foregroundStyle(Theme.Fairness.fair)

                        Text("This app works entirely offline and keeps all your data on your device. We don't collect, store, or share any personal information. Period.")
                            .font(.themeBody)
                            .foregroundStyle(Theme.Adaptive.textPrimary)
                    }
                }

                // MARK: - Contact
                VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                    Text("Questions?")
                        .font(.themeHeadline)
                        .foregroundStyle(Theme.Adaptive.textPrimary)

                    Text("If you have questions about our privacy practices, contact us at privacy@marrakechguide.app")
                        .font(.themeBody)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
                .padding(.vertical, Theme.Spacing.md)
            }
            .padding(.horizontal, Theme.Spacing.md)
        }
        .navigationTitle("Privacy Center")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Privacy Section

private struct PrivacySection: View {
    let icon: String
    let title: String
    let items: [String]
    var highlight: String? = nil

    var body: some View {
        ContentCard {
            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                Label(title, systemImage: icon)
                    .font(.themeHeadline)
                    .foregroundStyle(Theme.Adaptive.primary)

                VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                    ForEach(items, id: \.self) { item in
                        HStack(alignment: .top, spacing: Theme.Spacing.sm) {
                            Image(systemName: "checkmark")
                                .font(.themeCaption)
                                .foregroundStyle(Theme.Fairness.fair)
                                .frame(width: 16)

                            Text(item)
                                .font(.themeBody)
                                .foregroundStyle(Theme.Adaptive.textPrimary)
                        }
                    }
                }

                if let highlight = highlight {
                    Text(highlight)
                        .font(.themeSubheadline)
                        .foregroundStyle(Theme.Fairness.fair)
                        .padding(Theme.Spacing.sm)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.Fairness.fair.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.sm))
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        PrivacyCenterView()
    }
}
