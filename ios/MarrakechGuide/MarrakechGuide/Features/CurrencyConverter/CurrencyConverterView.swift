import SwiftUI

/// Currency Converter for quick MAD ↔ Home Currency conversion.
struct CurrencyConverterView: View {
    @StateObject private var viewModel = CurrencyConverterViewModel()
    @FocusState private var focusedField: Field?

    enum Field {
        case mad, home
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Theme.Spacing.xl) {
                    // Header with rate info
                    RateInfoCard(
                        currency: viewModel.selectedCurrency,
                        rate: viewModel.exchangeRate,
                        lastUpdated: viewModel.lastUpdated
                    )

                    // Converter inputs
                    VStack(spacing: Theme.Spacing.lg) {
                        // MAD input
                        CurrencyInputField(
                            label: "Moroccan Dirham",
                            currencyCode: "MAD",
                            flag: "🇲🇦",
                            value: $viewModel.madAmount,
                            isFocused: focusedField == .mad
                        )
                        .focused($focusedField, equals: .mad)
                        .onChange(of: viewModel.madAmount) { _, newValue in
                            if focusedField == .mad {
                                viewModel.convertFromMAD()
                            }
                        }

                        // Swap indicator
                        Image(systemName: "arrow.up.arrow.down")
                            .font(.title2)
                            .foregroundStyle(Theme.Adaptive.textSecondary)

                        // Home currency input
                        CurrencyInputField(
                            label: viewModel.selectedCurrency.name,
                            currencyCode: viewModel.selectedCurrency.code,
                            flag: viewModel.selectedCurrency.flag,
                            value: $viewModel.homeAmount,
                            isFocused: focusedField == .home
                        )
                        .focused($focusedField, equals: .home)
                        .onChange(of: viewModel.homeAmount) { _, newValue in
                            if focusedField == .home {
                                viewModel.convertFromHome()
                            }
                        }
                    }
                    .padding(Theme.Spacing.md)
                    .background(Theme.Adaptive.backgroundSecondary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))

                    // Quick amounts
                    QuickAmountsGrid(onAmountSelected: { amount in
                        viewModel.madAmount = String(amount)
                        viewModel.convertFromMAD()
                    })

                    // Currency selector
                    CurrencySelector(
                        currencies: CurrencyConverterViewModel.supportedCurrencies,
                        selectedCurrency: $viewModel.selectedCurrency,
                        onCurrencyChanged: { viewModel.updateRate() }
                    )

                    Spacer()
                }
                .padding(Theme.Spacing.md)
            }
            .navigationTitle("Currency Converter")
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") {
                        focusedField = nil
                    }
                }
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class CurrencyConverterViewModel: ObservableObject {
    @Published var madAmount: String = ""
    @Published var homeAmount: String = ""
    @Published var selectedCurrency: Currency = .usd
    @Published var exchangeRate: Double = 10.0
    @Published var lastUpdated: Date = Date()

    static let supportedCurrencies: [Currency] = [
        .usd, .eur, .gbp, .cad, .aud, .chf
    ]

    init() {
        loadSavedPreferences()
    }

    func convertFromMAD() {
        guard let mad = Double(madAmount.replacingOccurrences(of: ",", with: ".")) else {
            homeAmount = ""
            return
        }
        let home = mad / exchangeRate
        homeAmount = String(format: "%.2f", home)
    }

    func convertFromHome() {
        guard let home = Double(homeAmount.replacingOccurrences(of: ",", with: ".")) else {
            madAmount = ""
            return
        }
        let mad = home * exchangeRate
        madAmount = String(format: "%.0f", mad)
    }

    func updateRate() {
        exchangeRate = selectedCurrency.defaultRate
        convertFromMAD()
        savePreferences()
    }

    private func loadSavedPreferences() {
        if let currencyCode = UserDefaults.standard.string(forKey: "homeCurrency"),
           let currency = Self.supportedCurrencies.first(where: { $0.code == currencyCode }) {
            selectedCurrency = currency
        }
        if let savedRate = UserDefaults.standard.object(forKey: "exchangeRate") as? Double, savedRate > 0 {
            exchangeRate = savedRate
        } else {
            exchangeRate = selectedCurrency.defaultRate
        }
        if let savedDate = UserDefaults.standard.object(forKey: "rateLastUpdated") as? Date {
            lastUpdated = savedDate
        }
    }

    private func savePreferences() {
        UserDefaults.standard.set(selectedCurrency.code, forKey: "homeCurrency")
        UserDefaults.standard.set(exchangeRate, forKey: "exchangeRate")
        UserDefaults.standard.set(lastUpdated, forKey: "rateLastUpdated")
    }
}

// MARK: - Currency Model

struct Currency: Identifiable, Hashable {
    let id: String
    let code: String
    let name: String
    let flag: String
    let defaultRate: Double

    static let usd = Currency(id: "usd", code: "USD", name: "US Dollar", flag: "🇺🇸", defaultRate: 10.0)
    static let eur = Currency(id: "eur", code: "EUR", name: "Euro", flag: "🇪🇺", defaultRate: 11.0)
    static let gbp = Currency(id: "gbp", code: "GBP", name: "British Pound", flag: "🇬🇧", defaultRate: 12.5)
    static let cad = Currency(id: "cad", code: "CAD", name: "Canadian Dollar", flag: "🇨🇦", defaultRate: 7.5)
    static let aud = Currency(id: "aud", code: "AUD", name: "Australian Dollar", flag: "🇦🇺", defaultRate: 6.5)
    static let chf = Currency(id: "chf", code: "CHF", name: "Swiss Franc", flag: "🇨🇭", defaultRate: 11.5)
}

// MARK: - Rate Info Card

private struct RateInfoCard: View {
    let currency: Currency
    let rate: Double
    let lastUpdated: Date

    var body: some View {
        VStack(spacing: Theme.Spacing.xs) {
            Text("Current Rate")
                .font(.themeCaption)
                .foregroundStyle(Theme.Adaptive.textSecondary)

            Text("1 \(currency.code) = \(String(format: "%.1f", rate)) MAD")
                .font(.themeTitle2)
                .foregroundStyle(Theme.Adaptive.textPrimary)

            Text("Default rate \u{2022} Update in Settings")
                .font(.themeCaption)
                .foregroundStyle(Theme.Adaptive.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(Theme.Spacing.md)
        .background(Theme.Adaptive.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
    }
}

// MARK: - Currency Input Field

private struct CurrencyInputField: View {
    let label: String
    let currencyCode: String
    let flag: String
    @Binding var value: String
    let isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            HStack {
                Text(flag)
                    .font(.title2)
                Text(label)
                    .font(.themeSubheadline)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }

            HStack {
                TextField("0", text: $value)
                    .font(.system(size: 36, weight: .semibold, design: .rounded))
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.leading)

                Text(currencyCode)
                    .font(.themeTitle3)
                    .foregroundStyle(Theme.Adaptive.textSecondary)
            }
            .padding(Theme.Spacing.md)
            .background(Theme.Adaptive.backgroundPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.CornerRadius.md)
                    .stroke(isFocused ? Theme.Adaptive.primary : Color.clear, lineWidth: 2)
            )
        }
    }
}

// MARK: - Quick Amounts

private struct QuickAmountsGrid: View {
    let onAmountSelected: (Int) -> Void

    let quickAmounts = [50, 100, 200, 500, 1000, 2000]

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text("Quick Convert")
                .font(.themeSubheadline)
                .foregroundStyle(Theme.Adaptive.textSecondary)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: Theme.Spacing.sm) {
                ForEach(quickAmounts, id: \.self) { amount in
                    Button {
                        onAmountSelected(amount)
                    } label: {
                        Text("\(amount) MAD")
                            .font(.themeBody)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Theme.Spacing.sm)
                            .background(Theme.Adaptive.backgroundSecondary)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.sm))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

// MARK: - Currency Selector

private struct CurrencySelector: View {
    let currencies: [Currency]
    @Binding var selectedCurrency: Currency
    let onCurrencyChanged: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text("Home Currency")
                .font(.themeSubheadline)
                .foregroundStyle(Theme.Adaptive.textSecondary)

            Menu {
                ForEach(currencies) { currency in
                    Button {
                        selectedCurrency = currency
                        onCurrencyChanged()
                    } label: {
                        HStack {
                            Text("\(currency.flag) \(currency.code)")
                            if currency == selectedCurrency {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack {
                    Text(selectedCurrency.flag)
                        .font(.title2)
                    Text(selectedCurrency.name)
                        .font(.themeBody)
                    Spacer()
                    Text(selectedCurrency.code)
                        .font(.themeSubheadline)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                    Image(systemName: "chevron.down")
                        .font(.themeCaption)
                        .foregroundStyle(Theme.Adaptive.textSecondary)
                }
                .padding(Theme.Spacing.md)
                .background(Theme.Adaptive.backgroundSecondary)
                .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Preview

#Preview {
    CurrencyConverterView()
}
