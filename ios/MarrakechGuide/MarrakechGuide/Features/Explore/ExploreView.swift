import SwiftUI

struct ExploreView: View {
    var body: some View {
        NavigationStack {
            PlaceholderStateScreen(title: "Explore")
                .navigationTitle("Explore")
        }
    }
}

#Preview {
    ExploreView()
}
