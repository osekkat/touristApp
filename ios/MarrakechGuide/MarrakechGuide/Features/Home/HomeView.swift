import SwiftUI

struct HomeView: View {
    @State private var showQuoteAction = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    SectionHeader(title: "Quick Actions")

                    ContentCard(title: "Confidence Tools") {
                        VStack(spacing: Theme.Spacing.sm) {
                            Button {
                                showQuoteAction = true
                            } label: {
                                Label("Check a Price", systemImage: "scale.3d")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)

                            Text("Quick fairness check with scripts and counter-offer suggestions.")
                                .font(.themeFootnote)
                                .foregroundStyle(Theme.Adaptive.textSecondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    ContentCard(title: "Offline Ready") {
                        OfflineBanner()
                    }
                }
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.sm)
            }
            .navigationTitle("Home")
            .navigationDestination(isPresented: $showQuoteAction) {
                QuoteActionView()
            }
        }
    }
}

#Preview {
    HomeView()
}
