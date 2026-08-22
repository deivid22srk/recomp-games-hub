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
    const val RepoEditor = "repo_editor"
    const val Setup = "setup"
}

@Composable
inline fun <reified T : ViewModel> appViewModel(
    key: String? = null,
    noinline containerCreator: (AppContainer) -> T,
): T {
    val context = LocalContext.current
    val container = (context.applicationContext as RecompApplication).container
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM =
                containerCreator(container) as VM
        }
    }
    return viewModel<T>(key = key, factory = factory)
}