package com.ojitos369.lumaloop.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ojitos369.lumaloop.ui.utils.MediaStoreHelper

data class TagInfo(
    val name: String,
    val count: Int,
    val isSystemTag: Boolean = false
)

enum class TagSortOption {
    NAME_ASC, NAME_DESC, COUNT_ASC, COUNT_DESC
}

data class TagCatalogUiState(
    val tags: List<TagInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isAutoTagging: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val currentAutoTagItem: String = "",
    val sortOption: TagSortOption = TagSortOption.NAME_ASC
)

class TagCatalogViewModel(
    private val context: Context,
    private val preferencesManager: SharedPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagCatalogUiState())
    val uiState: StateFlow<TagCatalogUiState> = _uiState.asStateFlow()

    init {
        loadTags()
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val masterTags = preferencesManager.masterTagList
            val allUris = preferencesManager.imageUrisBase
            
            val tagCounts = masterTags.associateWith { tag ->
                allUris.count { uri -> preferencesManager.getTags(uri).contains(tag) }
            }
            
            val tagInfos = tagCounts.map { (name, count) ->
                TagInfo(name, count, isSystemTag = name == "Images" || name == "Videos")
            }
            
            _uiState.value = _uiState.value.copy(
                tags = sortTags(tagInfos, _uiState.value.sortOption),
                isLoading = false
            )
        }
    }

    fun setSortOption(option: TagSortOption) {
        _uiState.value = _uiState.value.copy(
            sortOption = option,
            tags = sortTags(_uiState.value.tags, option)
        )
    }

    private fun sortTags(tags: List<TagInfo>, option: TagSortOption): List<TagInfo> {
        return when (option) {
            TagSortOption.NAME_ASC -> tags.sortedBy { it.name.lowercase() }
            TagSortOption.NAME_DESC -> tags.sortedByDescending { it.name.lowercase() }
            TagSortOption.COUNT_ASC -> tags.sortedBy { it.count }
            TagSortOption.COUNT_DESC -> tags.sortedByDescending { it.count }
        }
    }

    fun addTag(name: String) {
        preferencesManager.addTagToCatalog(name)
        loadTags()
    }

    fun renameTag(oldName: String, newName: String) {
        preferencesManager.renameTag(oldName, newName)
        loadTags()
    }

    fun deleteTag(name: String) {
        preferencesManager.removeTagFromCatalog(name)
        loadTags()
    }

    /**
     * Performs auto-tagging.
     * @param targetTag If provided, only this tag will be checked over all elements.
     * @param targetUri If provided, all tags will be checked over only this element.
     * If both are null, all tags are checked over all elements.
     */
    fun performAutoTag(targetTag: String? = null, targetUri: Uri? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAutoTagging = true, processedCount = 0)
            
            val allUris = if (targetUri != null) listOf(targetUri) else preferencesManager.imageUrisBase
            val tagsToCheck = if (targetTag != null) listOf(targetTag) else preferencesManager.masterTagList.toList()
            
            _uiState.value = _uiState.value.copy(totalCount = allUris.size)
            
            withContext(Dispatchers.IO) {
                allUris.forEachIndexed { index, uri ->
                    val displayName = MediaStoreHelper.getDisplayName(context, uri) ?: uri.lastPathSegment ?: "Unknown"
                    val normalizedName = displayName.lowercase().replace(Regex("[^a-z0-9]"), "")
                    
                    val currentTags = preferencesManager.getTags(uri).toSet()
                    var changed = false
                    
                    tagsToCheck.forEach { tag ->
                        val normalizedTag = tag.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (normalizedTag.isNotEmpty() && normalizedName.contains(normalizedTag)) {
                            if (!currentTags.contains(tag)) {
                                Log.d("TagCatalogVM", "Adding tag '$tag' to '$displayName' (normalized: $normalizedName contains $normalizedTag)")
                                preferencesManager.addTag(uri, tag)
                                changed = true
                            } else {
                                Log.d("TagCatalogVM", "Tag '$tag' already exists on '$displayName'")
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            processedCount = index + 1,
                            currentAutoTagItem = displayName
                        )
                    }
                }
            }
            
            _uiState.value = _uiState.value.copy(isAutoTagging = false)
            loadTags()
        }
    }

    fun exportTags(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = preferencesManager.exportTagData(context)
            com.ojitos369.lumaloop.utilities.TagBackupHelper.exportTags(context, uri, data)
        }
    }

    fun importTags(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = com.ojitos369.lumaloop.utilities.TagBackupHelper.importTags(context, uri)
            if (data != null) {
                withContext(Dispatchers.Main) {
                    preferencesManager.importTagData(context, data)
                    loadTags()
                }
            }
        }
    }
}

class TagCatalogViewModelFactory(
    private val context: Context,
    private val preferencesManager: SharedPreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TagCatalogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TagCatalogViewModel(context, preferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
