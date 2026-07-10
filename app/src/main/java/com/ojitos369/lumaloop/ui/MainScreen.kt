package com.ojitos369.lumaloop.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ojitos369.lumaloop.ui.navigation.NavigationDestination
import com.ojitos369.lumaloop.ui.theme.neumorphic
import com.ojitos369.lumaloop.ui.screens.GalleryScreen
import com.ojitos369.lumaloop.ui.screens.PreviewScreen
import com.ojitos369.lumaloop.ui.screens.SettingsScreen
import com.ojitos369.lumaloop.ui.screens.TagCatalogScreen
import com.ojitos369.lumaloop.ui.utils.MediaStoreHelper
import com.ojitos369.lumaloop.ui.utils.WatchRepo

@Composable
fun MainScreen(sharedUris: List<Uri>? = null, activity: ComponentActivity) {
    val navController = rememberNavController()
    // Recomputed on every recomposition so toggling the setting updates the bar
    val items = listOfNotNull(
        NavigationDestination.Gallery,
        NavigationDestination.Tags,
        NavigationDestination.Preview,
        NavigationDestination.Watch.takeIf { WatchRepo.isEnabled(activity) },
        NavigationDestination.Settings
    )
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Floating neumorphic pill bar
            NavigationBar(
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                    .neumorphic(cornerRadius = 28.dp)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationDestination.Gallery.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationDestination.Gallery.route) {
                GalleryScreen(sharedUris = sharedUris, activity = activity)
            }
            composable(NavigationDestination.Tags.route) {
                TagCatalogScreen()
            }
            composable(NavigationDestination.Preview.route) {
                PreviewScreen()
            }
            composable(NavigationDestination.Watch.route) {
                GalleryScreen(
                    activity = activity,
                    albumName = MediaStoreHelper.WATCH_ALBUM_NAME,
                    isWatch = true
                )
            }
            composable(NavigationDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
