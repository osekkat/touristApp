import SwiftUI

struct ContentView: View {
    @AppStorage("selectedTab") private var selectedTabRawValue: String = AppTab.home.rawValue

    private var selectedTab: Binding<AppTab> {
        Binding(
            get: { AppTab(rawValue: selectedTabRawValue) ?? .home },
            set: { selectedTabRawValue = $0.rawValue }
        )
    }

    var body: some View {
        TabView(selection: selectedTab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppTab.home)

            ExploreView()
                .tabItem { Label("Explore", systemImage: "binoculars.fill") }
                .tag(AppTab.explore)

            EatView()
                .tabItem { Label("Eat", systemImage: "fork.knife") }
                .tag(AppTab.eat)

            PricesView()
                .tabItem { Label("Prices", systemImage: "tag.fill") }
                .tag(AppTab.prices)

            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis") }
                .tag(AppTab.more)
        }
        .tint(Theme.Adaptive.primary)
        .primaryBackground()
    }
}

private enum AppTab: String, Hashable {
    case home
    case explore
    case eat
    case prices
    case more
}

#Preview {
    ContentView()
}
