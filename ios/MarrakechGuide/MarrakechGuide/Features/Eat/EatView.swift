import SwiftUI

struct EatView: View {
    var body: some View {
        NavigationStack {
            PlaceholderStateScreen(title: "Eat")
                .navigationTitle("Eat")
        }
    }
}

#Preview {
    EatView()
}
