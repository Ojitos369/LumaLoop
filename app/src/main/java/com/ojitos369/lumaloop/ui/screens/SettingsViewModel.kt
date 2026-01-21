package com.ojitos369.lumaloop.ui.screens

import androidx.lifecycle.ViewModel
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
        val interval: Int = 5,
        val transitionDuration: Int = 1000,
        val muteVideos: Boolean = false,
        val playbackOrder: String = "Sequential",
        val displayMode: String = "Fit",
        val swipeToChange: Boolean = false,
        val galleryColumns: Int = 3,
        val thumbnailRatio: String = "3:4",
        val tagFilterMode: SharedPreferencesManager.TagFilterMode = SharedPreferencesManager.TagFilterMode.OR,
        val hiddenTags: Set<String> = emptySet(),
        val autoTagEnabled: Boolean = false,
        val availableTags: Set<String> = emptySet()
)

class SettingsViewModel(private val preferencesManager: SharedPreferencesManager) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val prefs = preferencesManager.preferences

        // Read ordering from preferences (defaults to "selection")
        val orderingValue = prefs.getString("ordering", "selection") ?: "selection"
        val playbackOrderDisplay =
                when (orderingValue) {
                    "random" -> "Random"
                    else -> "Sequential"
                }

        // Read display mode from preferences (uses too_wide_images_rule key)
        val displayModeValue = prefs.getString("too_wide_images_rule", "scale_down") ?: "scale_down"
        val displayModeDisplay =
                when (displayModeValue) {
                    "scroll_forward" -> "Scroll Forward"
                    "scroll_backward" -> "Scroll Backward"
                    "scale_up" -> "Fill"
                    else -> "Fit"
                }

        _uiState.value =
                _uiState.value.copy(
                        interval = preferencesManager.secondsBetweenImages,
                        transitionDuration = preferencesManager.transitionDuration,
                        muteVideos = preferencesManager.muteVideos,
                        playbackOrder = playbackOrderDisplay,
                        displayMode = displayModeDisplay,
                        swipeToChange = preferencesManager.swipeToChange,
                        galleryColumns = prefs.getInt("gallery_columns", 3),
                        thumbnailRatio = prefs.getString("thumbnail_ratio", "3:4") ?: "3:4",
                        tagFilterMode = preferencesManager.getTagFilterMode(),
                        hiddenTags = preferencesManager.getHiddenTags(),
                        autoTagEnabled = preferencesManager.isAutoTagEnabled(),
                        availableTags = preferencesManager.allTags
                )
    }

    fun setInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(interval = interval)
        preferencesManager.secondsBetweenImages = interval
    }

    fun setTransitionDuration(duration: Int) {
        _uiState.value = _uiState.value.copy(transitionDuration = duration)
        preferencesManager.transitionDuration = duration
    }

    fun setMuteVideos(muted: Boolean) {
        _uiState.value = _uiState.value.copy(muteVideos = muted)
        preferencesManager.preferences.edit().putBoolean("mute_videos", muted).apply()
    }

    fun setPlaybackOrder(order: String) {
        _uiState.value = _uiState.value.copy(playbackOrder = order)
        // Map "Random" to "random" preference value
        val value =
                when (order) {
                    "Random" -> "random"
                    else -> "selection"
                }
        preferencesManager.preferences.edit().putString("ordering", value).apply()
    }

    fun setDisplayMode(mode: String) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        val value =
                when (mode) {
                    "Scroll Forward" -> "scroll_forward"
                    "Scroll Backward" -> "scroll_backward"
                    "Fill" -> "scale_up"
                    else -> "scale_down" // Fit
                }
        preferencesManager.preferences.edit().putString("too_wide_images_rule", value).apply()
    }

    fun setSwipeToChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(swipeToChange = enabled)
        preferencesManager.preferences.edit().putBoolean("swipe", enabled).apply()
    }

    fun setGalleryColumns(columns: Int) {
        _uiState.value = _uiState.value.copy(galleryColumns = columns)
        preferencesManager.preferences.edit().putInt("gallery_columns", columns).apply()
    }

    fun setThumbnailRatio(ratio: String) {
        _uiState.value = _uiState.value.copy(thumbnailRatio = ratio)
        preferencesManager.preferences.edit().putString("thumbnail_ratio", ratio).apply()
    }

    fun setTagFilterMode(mode: SharedPreferencesManager.TagFilterMode) {
        _uiState.value = _uiState.value.copy(tagFilterMode = mode)
        preferencesManager.setTagFilterMode(mode)
    }

    fun setHiddenTags(tags: Set<String>) {
        _uiState.value = _uiState.value.copy(hiddenTags = tags)
        preferencesManager.setHiddenTags(tags)
    }

    fun setAutoTagEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoTagEnabled = enabled)
        preferencesManager.setAutoTagEnabled(enabled)
    }
}
