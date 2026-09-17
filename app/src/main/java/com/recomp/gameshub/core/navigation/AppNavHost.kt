package com.recomp.gameshub.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recomp.gameshub.presentation.catalog.CatalogRoute
import com.recomp.gameshub.presentation.downloads.DownloadsRoute
import com.recomp.gameshub.presentation.repo.RepoEditorRoute
import com.recomp.gameshub.presentation.settings.SettingsRoute
import com.recomp.gameshub.presentation.splash.SplashScreen
import com.recomp.gameshub.presentation.setup.SetupRoute
import kotlinx.coroutines.delay

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        delay(1400)
        // Guarda evita empilhar um segundo Catalog quando a Activity é
        // recriada (rotação/processo) e o Splash não está mais no topo.
        if (navController.currentDestination?.route == Routes.Splash) {
            navController.navigate(Routes.Catalog) {
                popUpTo(Routes.Splash) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun navigateTo(route: String) {
        navController.navigateToTab(route)
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
                onOpenCatalog = { navigateTo(Routes.Catalog) },
                onOpenSettings = { navigateTo(Routes.Settings) },
            )
        }
        composable(Routes.Settings) {
            SettingsRoute(
                onOpenCatalog = { navigateTo(Routes.Catalog) },
                onOpenDownloads = { navigateTo(Routes.Downloads) },
                onOpenRepoEditor = { navigateTo(Routes.RepoEditor) },
            )
        }
        composable(Routes.RepoEditor) {
            RepoEditorRoute(
                onBack = { navController.popBackStack() },
                onOpenSetup = { navigateTo(Routes.Setup) },
            )
        }
        composable(Routes.Setup) {
            SetupRoute(onComplete = { navController.popBackStack() })
        }
    }
}

/**
 * Navegação entre abas com comportamento de bottom bar: estado salvo/restaurado
 * e pilha limitada ao destino inicial (Catalog), em vez de empilhar as abas.
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(Routes.Catalog) {
            saveState = true
        }
    }
}
