package com.marrakechguide.feature.explore

import androidx.compose.runtime.Composable
import com.marrakechguide.ui.components.PlaceholderStateScreen

@Composable
fun ExploreScreen() {
    PlaceholderStateScreen(title = "Explore")
}

@Composable
fun PlaceDetailScreen(placeId: String?) {
    PlaceholderStateScreen(
        title = "Place Detail",
        subtitle = placeId ?: "unknown",
    )
}
