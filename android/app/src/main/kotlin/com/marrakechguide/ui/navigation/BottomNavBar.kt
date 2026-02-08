package com.marrakechguide.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun BottomNavBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        bottomDestinations.forEach { destination ->
            NavigationBarItem(
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                selected = isSelectedRoute(
                    currentRoute = currentRoute,
                    destinationRoute = destination.route,
                ),
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

private fun isSelectedRoute(
    currentRoute: String?,
    destinationRoute: String,
): Boolean {
    if (currentRoute == destinationRoute) return true

    val inExploreDetail = currentRoute == Screen.PlaceDetail.route || currentRoute?.startsWith("place/") == true
    if (inExploreDetail && destinationRoute == Screen.Explore.route) return true

    val inPricesDetail = currentRoute == Screen.PriceCardDetail.route || currentRoute?.startsWith("priceCard/") == true
    if (inPricesDetail && destinationRoute == Screen.Prices.route) return true

    return false
}
