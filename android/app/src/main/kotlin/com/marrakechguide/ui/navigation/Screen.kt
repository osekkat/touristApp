package com.marrakechguide.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Explore : Screen("explore")
    data object Eat : Screen("eat")
    data object Prices : Screen("prices")
    data object More : Screen("more")

    data object PlaceDetail : Screen("place/{placeId}") {
        fun createRoute(placeId: String): String {
            return "place/${Uri.encode(placeId)}"
        }
    }

    data object PriceCardDetail : Screen("priceCard/{cardId}") {
        fun createRoute(cardId: String): String {
            return "priceCard/${Uri.encode(cardId)}"
        }
    }

    data object QuoteAction : Screen("quoteAction?cardId={cardId}") {
        fun createRoute(cardId: String? = null): String {
            return if (cardId.isNullOrBlank()) {
                "quoteAction"
            } else {
                "quoteAction?cardId=${Uri.encode(cardId)}"
            }
        }
    }
}

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomDestinations = listOf(
    BottomDestination(route = Screen.Home.route, label = "Home", icon = Icons.Filled.Home),
    BottomDestination(route = Screen.Explore.route, label = "Explore", icon = Icons.Filled.Explore),
    BottomDestination(route = Screen.Eat.route, label = "Eat", icon = Icons.Filled.Restaurant),
    BottomDestination(route = Screen.Prices.route, label = "Prices", icon = Icons.Filled.LocalOffer),
    BottomDestination(route = Screen.More.route, label = "More", icon = Icons.Filled.MoreHoriz),
)
