import SwiftUI

struct HomeView: View {
    var body: some View {
        NavigationStack {
            PlaceholderStateScreen(title: "Home")
                .navigationTitle("Home")
        }
    }
}

#Preview {
    HomeView()
}
