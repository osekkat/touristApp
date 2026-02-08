import SwiftUI

struct MoreView: View {
    var body: some View {
        NavigationStack {
            PlaceholderStateScreen(title: "More")
                .navigationTitle("More")
        }
    }
}

#Preview {
    MoreView()
}
