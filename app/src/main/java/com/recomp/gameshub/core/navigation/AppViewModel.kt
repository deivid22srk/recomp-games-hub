package com.recomp.gameshub.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recomp.gameshub.AppContainer
import com.recomp.gameshub.RecompApplication

object Routes {
    const val Splash = "splash"
    const val Catalog = "catalog"
    const val Downloads = "downloads"
    const val Settings = "settings"
}

@Composable
fun <T : ViewModel> appViewModel(
    containerCreator: (AppContainer) -> T,
    key: String? = null,
): T {
    val context = LocalContext.current
    val container = context.applicationContext
        .let { it as RecompApplication }
        .container
    val factory = remember(container, containerCreator) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM =
                containerCreator(container) as VM
        }
    }
    return viewModel(key = key, factory = factory)
}