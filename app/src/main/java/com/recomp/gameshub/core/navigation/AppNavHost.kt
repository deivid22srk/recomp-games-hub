package com.recomp.gameshub.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.recomp.gameshub.presentation.catalog.CatalogRoute
import com.recomp.gameshub.presentation.downloads.DownloadsRoute
import com.recomp.gameshub.presentation.settings.SettingsRoute
import com.recomp.gameshub.presentation.splash.SplashScreen
import kotlinx.coroutines.delay

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        delay(1600)
        navController.navigate(Routes.Catalog) {
            popUpTo(Routes.Splash) { inclusive = true }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()

    fun navigateTo(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        enterTransition = { fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 12 } },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { it / 12 } },
    ) {
        composable(Routes.Splash) {
            SplashScreen()
        }
        composable(Routes.Catalog) {
            CatalogRoute(
                onOpenDownloads = { navigateTo(Routes.Downloads) },
                onOpenSettings = { navigateTo(Routes.Settings) },
            )
        }
        composable(Routes.Downloads) {
            DownloadsRoute(
                onBack = { navController.popBackStack() },
                onOpenCatalog = { navigateTo(Routes.Catalog) },
                onOpenSettings = { navigateTo(Routes.Settings) },
            )
        }
        composable(Routes.Settings) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenCatalog = { navigateTo(Routes.Catalog) },
                onOpenDownloads = { navigateTo(Routes.Downloads) },
            )
        }
    }
}