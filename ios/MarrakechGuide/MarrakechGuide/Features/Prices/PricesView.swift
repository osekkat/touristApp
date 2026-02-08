import SwiftUI

struct PricesView: View {
    var body: some View {
        NavigationStack {
            PlaceholderStateScreen(title: "Prices")
                .navigationTitle("Prices")
        }
    }
}

#Preview {
    PricesView()
}
