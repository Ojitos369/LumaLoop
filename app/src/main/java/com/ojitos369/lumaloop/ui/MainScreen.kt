package com.ojitos369.lumaloop.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ojitos369.lumaloop.ui.navigation.NavigationDestination
import com.ojitos369.lumaloop.ui.screens.GalleryScreen
import com.ojitos369.lumaloop.ui.screens.PreviewScreen
import com.ojitos369.lumaloop.ui.screens.SettingsScreen
import com.ojitos369.lumaloop.ui.screens.TagCatalogScreen

@Composable
fun MainScreen(sharedUris: List<Uri>? = null, activity: ComponentActivity) {
    val navController = rememberNavController()
    val items = listOf(
        NavigationDestination.Gallery,
        NavigationDestination.Tags,
        NavigationDestination.Preview,
        NavigationDestination.Settings
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title) },
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
            composable(NavigationDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
