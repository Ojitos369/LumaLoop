package com.ojitos369.lumaloop.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Gallery : NavigationDestination(
        route = "gallery",
        title = "Gallery",
        icon = Icons.Default.CollectionsBookmark
    )
    
    object Preview : NavigationDestination(
        route = "preview",
        title = "Preview",
        icon = Icons.Default.Visibility
    )
    
    object Settings : NavigationDestination(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )

    object Tags : NavigationDestination(
        route = "tags",
        title = "Tags",
        icon = Icons.Default.CollectionsBookmark
    )
}
