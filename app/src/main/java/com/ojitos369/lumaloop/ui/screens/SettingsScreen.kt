package com.ojitos369.lumaloop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager
import com.ojitos369.lumaloop.ui.components.DisplayModeBottomSheet
import com.ojitos369.lumaloop.ui.components.IntervalBottomSheet
import com.ojitos369.lumaloop.ui.components.PlaybackOrderBottomSheet
import com.ojitos369.lumaloop.ui.components.ThumbnailRatioBottomSheet
import com.ojitos369.lumaloop.ui.components.TagFilterModeBottomSheet
import com.ojitos369.lumaloop.ui.components.HiddenTagsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        viewModel: SettingsViewModel =
                viewModel(
                        factory =
                                SettingsViewModelFactory(
                                        SharedPreferencesManager(
                                                // Use default SharedPreferences to match
                                                // WallpaperPreferencesFragment
                                                PreferenceManager.getDefaultSharedPreferences(
                                                        LocalContext.current
                                                )
                                        )
                                )
                )
) {
    
    val uiState by viewModel.uiState.collectAsState()

    var showIntervalSheet by remember { mutableStateOf(false) }
    var showPlaybackOrderSheet by remember { mutableStateOf(false) }
    var showDisplayModeSheet by remember { mutableStateOf(false) }
    var showThumbnailRatioSheet by remember { mutableStateOf(false) }
    var showTagFilterModeSheet by remember { mutableStateOf(false) }
    var showHiddenTagsSheet by remember { mutableStateOf(false) }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
    ) {
        // Header
        Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        Divider()

        // Interval Setting
        ListItem(
                headlineContent = { Text("Slideshow Interval") },
                supportingContent = { Text("${uiState.interval} seconds between images") },
                leadingContent = {
                    Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showIntervalSheet = true }
        )

        Divider()

        // Transition Duration Setting
        ListItem(
                headlineContent = { Text("Cross-fade Transition Speed") },
                supportingContent = { Text("${uiState.transitionDuration} ms") },
                leadingContent = {
                    Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                }
        )
        Slider(
                value = uiState.transitionDuration.toFloat(),
                onValueChange = { viewModel.setTransitionDuration(it.toInt()) },
                valueRange = 0f..3000f,
                steps = 5,
                modifier = Modifier.padding(horizontal = 16.dp)
        )

        Divider()

        // Mute Setting
        ListItem(
                headlineContent = { Text("Mute Videos") },
                supportingContent = { Text("Play videos without sound") },
                leadingContent = {
                    Icon(
                            if (uiState.muteVideos) Icons.Default.VolumeOff
                            else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Switch(
                            checked = uiState.muteVideos,
                            onCheckedChange = { viewModel.setMuteVideos(it) }
                    )
                }
        )

        Divider()

        // Order Setting
        ListItem(
                headlineContent = { Text("Playback Order") },
                supportingContent = { Text(uiState.playbackOrder) },
                leadingContent = {
                    Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showPlaybackOrderSheet = true }
        )

        Divider()

        // Display Mode Setting
        ListItem(
                headlineContent = { Text("Display Mode") },
                supportingContent = { Text(uiState.displayMode) },
                leadingContent = {
                    Icon(
                            Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showDisplayModeSheet = true }
        )

        HorizontalDivider()

        // Swipe to Change Setting
        ListItem(
                headlineContent = { Text("Swipe to Change") },
                supportingContent = { Text("Swipe screen to change wallpaper") },
                leadingContent = {
                    Icon(
                            Icons.Default.Swipe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Switch(
                            checked = uiState.swipeToChange,
                            onCheckedChange = { viewModel.setSwipeToChange(it) }
                    )
                }
        )

        HorizontalDivider()

        // Gallery Section Header
        Text(
                text = "Gallery",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Thumbnail Ratio Setting
        ListItem(
                headlineContent = { Text("Thumbnail Aspect Ratio") },
                supportingContent = { Text(uiState.thumbnailRatio) },
                leadingContent = {
                    Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null)
                },
                modifier = Modifier.clickable { showThumbnailRatioSheet = true }
        )

        HorizontalDivider()

        // Tag Filter Mode Setting
        ListItem(
                headlineContent = { Text("Tag Filter Mode") },
                supportingContent = { Text("Mode: ${uiState.tagFilterMode}") },
                leadingContent = {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                },
                modifier = Modifier.clickable { showTagFilterModeSheet = true }
        )

        ListItem(
                headlineContent = { Text("Hidden Tags") },
                supportingContent = { Text("${uiState.hiddenTags.size} tags hidden") },
                leadingContent = {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                },
                modifier = Modifier.clickable { showHiddenTagsSheet = true }
        )

        HorizontalDivider()

        // Auto-tag Setting
        ListItem(
                headlineContent = { Text("Auto-tag") },
                supportingContent = { Text("Automatically assign tags based on file names") },
                leadingContent = {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                            checked = uiState.autoTagEnabled,
                            onCheckedChange = { viewModel.setAutoTagEnabled(it) }
                    )
                }
        )

        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        // App Info
        Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = "LumaLoop",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                        text = "Version 1.4.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                        text = "Dynamic wallpaper slideshow app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Show interval bottom sheet
    if (showIntervalSheet) {
        IntervalBottomSheet(
                currentInterval = uiState.interval,
                onDismiss = { showIntervalSheet = false },
                onSave = { newInterval -> viewModel.setInterval(newInterval) }
        )
    }

    // Show playback order bottom sheet
    if (showPlaybackOrderSheet) {
        PlaybackOrderBottomSheet(
                currentOrder = uiState.playbackOrder,
                onDismiss = { showPlaybackOrderSheet = false },
                onSave = { newOrder ->
                    viewModel.setPlaybackOrder(newOrder)
                    showPlaybackOrderSheet = false
                }
        )
    }

    // Show display mode bottom sheet
    if (showDisplayModeSheet) {
        DisplayModeBottomSheet(
                currentMode = uiState.displayMode,
                onDismiss = { showDisplayModeSheet = false },
                onSave = { newMode ->
                    viewModel.setDisplayMode(newMode)
                    showDisplayModeSheet = false
                }
        )
    }

    if (showThumbnailRatioSheet) {
        ThumbnailRatioBottomSheet(
                selectedRatio = uiState.thumbnailRatio,
                onRatioSelected = { viewModel.setThumbnailRatio(it) },
                onDismiss = { showThumbnailRatioSheet = false }
        )
    }

    if (showTagFilterModeSheet) {
        TagFilterModeBottomSheet(
                currentMode = uiState.tagFilterMode,
                onDismiss = { showTagFilterModeSheet = false },
                onSave = { viewModel.setTagFilterMode(it) }
        )
    }

    if (showHiddenTagsSheet) {
        HiddenTagsBottomSheet(
                availableTags = uiState.availableTags,
                hiddenTags = uiState.hiddenTags,
                onDismiss = { showHiddenTagsSheet = false },
                onSave = { viewModel.setHiddenTags(it) }
        )
    }
}
