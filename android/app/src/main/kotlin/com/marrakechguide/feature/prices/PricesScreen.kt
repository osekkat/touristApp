package com.marrakechguide.feature.prices

import androidx.compose.runtime.Composable
import com.marrakechguide.ui.components.PlaceholderStateScreen

@Composable
fun PricesScreen() {
    PlaceholderStateScreen(title = "Prices")
}

@Composable
fun PriceCardDetailScreen(cardId: String?) {
    PlaceholderStateScreen(
        title = "Price Card Detail",
        subtitle = cardId ?: "unknown",
    )
}
