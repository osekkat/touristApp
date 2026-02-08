package com.marrakechguide.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marrakechguide.feature.eat.EatScreen
import com.marrakechguide.feature.explore.ExploreScreen
import com.marrakechguide.feature.explore.PlaceDetailScreen
import com.marrakechguide.feature.home.HomeScreen
import com.marrakechguide.feature.more.MoreScreen
import com.marrakechguide.feature.prices.PriceCardDetailScreen
import com.marrakechguide.feature.prices.PricesScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            BottomNavBar(
                navController = navController,
                currentRoute = currentRoute,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Explore.route) { ExploreScreen() }
            composable(Screen.Eat.route) { EatScreen() }
            composable(Screen.Prices.route) { PricesScreen() }
            composable(Screen.More.route) { MoreScreen() }

            composable(
                route = Screen.PlaceDetail.route,
                arguments = listOf(navArgument("placeId") { type = NavType.StringType }),
            ) { backStackEntry ->
                PlaceDetailScreen(
                    placeId = backStackEntry.arguments?.getString("placeId"),
                )
            }

            composable(
                route = Screen.PriceCardDetail.route,
                arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
            ) { backStackEntry ->
                PriceCardDetailScreen(
                    cardId = backStackEntry.arguments?.getString("cardId"),
                )
            }
        }
    }
}
