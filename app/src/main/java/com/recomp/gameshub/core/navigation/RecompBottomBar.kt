package com.recomp.gameshub.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

object BottomNavItems {
    val Catalog = BottomNavItem(Routes.Catalog, "Catálogo", Icons.Rounded.Extension)
    val Downloads = BottomNavItem(Routes.Downloads, "Downloads", Icons.Rounded.Download)
    val Settings = BottomNavItem(Routes.Settings, "Configurações", Icons.Rounded.Settings)

    val all = listOf(Catalog, Downloads, Settings)
}

@Composable
fun RecompBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(tonalElevation = 3.dp) {
        BottomNavItems.all.forEach { item ->
            val selected = item.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}