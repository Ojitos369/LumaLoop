package com.ojitos369.lumaloop.ui.screens

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.IntentSender
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager
import com.ojitos369.lumaloop.ui.utils.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

data class MediaItem(
        val uri: Uri,
        val isVideo: Boolean = false,
        val name: String = "",
        val lastModified: Long = 0,
        val tags: List<String> = emptyList()
)

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    TAG_COUNT_DESC,
    TAG_COUNT_ASC
}

enum class MediaFilter {
    ALL,
    IMAGES_ONLY,
    VIDEOS_ONLY
}

data class GalleryUiState(
        val mediaItems: List<MediaItem> = emptyList(),
        val selectedItems: Set<Uri> = emptySet(),
        val currentFilter: MediaFilter = MediaFilter.ALL,
        val activeTags: Set<String> = emptySet(),
        val availableTags: Set<String> = emptySet(),
        val hiddenTags: Set<String> = emptySet(),
        val tagFilterMode: SharedPreferencesManager.TagFilterMode = SharedPreferencesManager.TagFilterMode.OR,
        val autoTagEnabled: Boolean = false,
        val isLoading: Boolean = false,
        val sortOption: SortOption = SortOption.DATE_DESC,
        // Enhanced loading progress tracking
        val loadingProgress: Float = 0f,
        val loadingMessage: String = "",
        val currentFileName: String = "",
        val totalFiles: Int = 0,
        val processedFiles: Int = 0,
        val loadingTitle: String? = null,
        // Autotag confirmation
        val showAutotagPrompt: Boolean = false,
        val urisForAutotag: List<Uri> = emptyList(),
        val isAutoTagging: Boolean = false,
        val pendingDeleteIntent: IntentSender? = null
)

class GalleryViewModel(
        private val context: Context,
        private val preferencesManager: SharedPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Track processed shared URIs to prevent re-adding on navigation
    private var processedSharedUris: Set<Uri> = emptySet()

    // ContentObserver for external changes to LumaLoop album
    private val albumContentObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.d("GalleryViewModel", "MediaStore changed, refreshing gallery")
                    loadMediaItems()
                }
            }

    init {
        // Register ContentObserver for MediaStore changes
        context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                albumContentObserver
        )
        context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                albumContentObserver
        )

        loadMediaItems()
        // Migrate from private storage to public album if needed
        migrateToPublicAlbum()
        // Clean up duplicates and invalid URIs
        cleanupDuplicates()
    }

    override fun onCleared() {
        super.onCleared()
        context.contentResolver.unregisterContentObserver(albumContentObserver)
    }

    fun loadMediaItems(preserveSelection: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            // First sync with folder
            syncWithAlbum()

            val uris =
                    preferencesManager.getImageUris(
                            com.ojitos369.lumaloop.preferences
                                    .SharedPreferencesManager.Ordering.SELECTION
                    )

            if (uris.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(mediaItems = emptyList(), selectedItems = emptySet())
                }
                return@launch
            }

            // Batch validate and fetch metadata
            val mediaItemsMap = mutableMapOf<Uri, MediaItem>()
            val idsToQuery = mutableListOf<String>()
            val uriToIdMap = mutableMapOf<String, Uri>()

            uris.forEach { uri ->
                val id = uri.lastPathSegment
                if (id != null) {
                    idsToQuery.add(id)
                    uriToIdMap[id] = uri
                }
            }

            // Query MediaStore in batches if necessary
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.MIME_TYPE
                )

                // Query Images
                val selection = "${MediaStore.MediaColumns._ID} IN (${idsToQuery.joinToString(",") { "?" }})"
                val selectionArgs = idsToQuery.toTypedArray()

                val contentUris = listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                )

                contentUris.forEach { contentUri ->
                    context.contentResolver.query(
                        contentUri,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idColumn)
                            val name = cursor.getString(nameColumn) ?: "Unknown"
                            val lastModified = cursor.getLong(dateColumn)
                            val mimeType = cursor.getString(mimeColumn)
                            val uri = uriToIdMap[id]
                            
                            if (uri != null && !mediaItemsMap.containsKey(uri)) {
                                mediaItemsMap[uri] = MediaItem(
                                    uri = uri,
                                    isVideo = mimeType?.startsWith("video/") == true,
                                    name = name,
                                    lastModified = lastModified
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error querying MediaStore", e)
            }

            // For any URIs not found in MediaStore, fallback to individual validation
            val finalItems = uris.mapNotNull { uri ->
                val item = mediaItemsMap[uri] ?: run {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { 
                            MediaItem(uri = uri, isVideo = isVideoUri(uri), name = uri.lastPathSegment ?: "Unknown", lastModified = 0L)
                        }
                    } catch (e: Exception) {
                        Log.w("GalleryViewModel", "Invalid URI, removing: $uri")
                        preferencesManager.removeUri(uri)
                        null
                    }
                }
                
                item?.let {
                    val isVideo = it.isVideo
                    val typeTag = if (isVideo) "Videos" else "Images"
                    var tags = preferencesManager.getTags(uri).toSet()
                    
                    if (!tags.contains(typeTag)) {
                        preferencesManager.addTag(uri, typeTag)
                        tags = tags + typeTag
                    }

                    it.copy(tags = tags.toList())
                }
            }

            val sortedItems = sortMediaItems(finalItems, _uiState.value.sortOption)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    mediaItems = sortedItems, 
                    selectedItems = if (preserveSelection) _uiState.value.selectedItems else emptySet(),
                    availableTags = preferencesManager.masterTagList,
                    activeTags = preferencesManager.getActiveTags(),
                    hiddenTags = preferencesManager.getHiddenTags(),
                    tagFilterMode = preferencesManager.getTagFilterMode(),
                    autoTagEnabled = preferencesManager.isAutoTagEnabled()
                )
            }
        }
    }

    fun setSortOption(option: SortOption) {
        val sortedItems = sortMediaItems(_uiState.value.mediaItems, option)
        _uiState.value = _uiState.value.copy(sortOption = option, mediaItems = sortedItems)
    }

    private fun sortMediaItems(items: List<MediaItem>, option: SortOption): List<MediaItem> {
        return when (option) {
            SortOption.DATE_DESC -> items.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> items.sortedBy { it.lastModified }
            SortOption.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            SortOption.TAG_COUNT_DESC -> items.sortedByDescending { it.tags.size }
            SortOption.TAG_COUNT_ASC -> items.sortedBy { it.tags.size }
        }
    }

    fun toggleSelection(uri: Uri) {
        val currentSelected = _uiState.value.selectedItems
        _uiState.value =
                _uiState.value.copy(
                        selectedItems =
                                if (uri in currentSelected) {
                                    currentSelected - uri
                                } else {
                                    currentSelected + uri
                                }
                )
    }

    fun removeItemByUri(uri: Uri) {
        viewModelScope.launch {
            preferencesManager.removeUri(uri)
            loadMediaItems()
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            val visibleItems = getFilteredMediaItems()
            _uiState.value =
                    _uiState.value.copy(
                            selectedItems = visibleItems.map { it.uri }.toSet()
                    )
        }
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.value =
                _uiState.value.copy(
                        currentFilter = filter,
                        selectedItems = emptySet() // Clear selection when filter changes
                )
    }

    fun addMediaItems(uris: List<Uri>) {
        viewModelScope.launch {
            val totalCount = uris.size
            var processedCount = 0

            // Initialize loading state with progress info
            _uiState.value =
                    _uiState.value.copy(
                            isLoading = true,
                            totalFiles = totalCount,
                            processedFiles = 0,
                            loadingProgress = 0f,
                            currentFileName = ""
                    )

            try {
                // Get current URIs to prevent duplicates
                val currentUris =
                        preferencesManager
                                .getImageUris(
                                        com.ojitos369.lumaloop.preferences
                                                .SharedPreferencesManager.Ordering.SELECTION
                                )
                                .toSet()

                // Filter out duplicates first
                val urisToProcess = uris.filterNot { it in currentUris }
                val actualTotal = urisToProcess.size

                if (actualTotal == 0) {
                    Log.d("GalleryViewModel", "All URIs already exist, skipping")
                    return@launch
                }

                // Update total with actual count (excluding duplicates)
                _uiState.value = _uiState.value.copy(totalFiles = actualTotal)

                // Use Semaphore for parallel processing with max 3 concurrent
                val semaphore = Semaphore(3)
                val addedUris = java.util.concurrent.ConcurrentLinkedQueue<Uri>()

                // Process files in parallel using coroutineScope
                kotlinx.coroutines.coroutineScope {
                    val jobs =
                            urisToProcess.mapIndexed { index, originalUri ->
                                async(Dispatchers.IO) {
                                    semaphore.acquire()
                                    try {
                                        // Get file name for display
                                        var originalName: String? = null
                                        var displayName = "archivo ${index + 1}"
                                        try {
                                            context.contentResolver.query(
                                                            originalUri,
                                                            arrayOf(
                                                                    MediaStore.MediaColumns
                                                                            .DISPLAY_NAME
                                                            ),
                                                            null,
                                                            null,
                                                            null
                                                    )
                                                    ?.use { cursor ->
                                                        if (cursor.moveToFirst()) {
                                                            val nameIndex =
                                                                    cursor.getColumnIndex(
                                                                            MediaStore.MediaColumns
                                                                                    .DISPLAY_NAME
                                                                    )
                                                            if (nameIndex != -1) {
                                                                val fullName =
                                                                        cursor.getString(nameIndex)
                                                                displayName =
                                                                        fullName ?: displayName
                                                                originalName =
                                                                        fullName?.substringBeforeLast(
                                                                                "."
                                                                        )
                                                            }
                                                        }
                                                    }
                                        } catch (e: Exception) {
                                            Log.w(
                                                    "GalleryViewModel",
                                                    "Could not get original name",
                                                    e
                                            )
                                        }

                                        // Update UI with current file
                                        withContext(Dispatchers.Main) {
                                            _uiState.value =
                                                    _uiState.value.copy(
                                                            currentFileName = displayName
                                                    )
                                        }

                                        // Copy to public MediaStore album
                                        val albumUri =
                                                MediaStoreHelper.copyToPublicAlbum(
                                                        context,
                                                        originalUri,
                                                        originalName
                                                )
                                        val uriToSave = albumUri ?: originalUri

                                        if (uriToSave !in currentUris) {
                                            addedUris.add(uriToSave)
                                        }

                                        if (albumUri == null) {
                                            Log.w(
                                                    "GalleryViewModel",
                                                    "Failed to copy to album, using original: $originalUri"
                                            )
                                        } else {
                                            Log.d("GalleryViewModel", "Added URI: $uriToSave")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                                "GalleryViewModel",
                                                "Error adding media: $originalUri",
                                                e
                                        )
                                        if (originalUri !in currentUris) {
                                            addedUris.add(originalUri)
                                        }
                                        Unit
                                    } finally {
                                        // Update progress on main thread
                                        withContext(Dispatchers.Main) {
                                            processedCount++
                                            val progress = processedCount.toFloat() / actualTotal
                                            _uiState.value =
                                                    _uiState.value.copy(
                                                            processedFiles = processedCount,
                                                            loadingProgress = progress
                                                    )
                                        }
                                        semaphore.release()
                                    }
                                }
                            }
                    // Wait for all to complete
                    jobs.forEach { it.await() }
                }

                // Add all URIs to preferences in batch
                preferencesManager.addUris(addedUris.toList())

                if (preferencesManager.isAutoTagEnabled()) {
                    _uiState.value = _uiState.value.copy(
                        showAutotagPrompt = true,
                        urisForAutotag = addedUris.toList()
                    )
                }

                loadMediaItems()
            } finally {
                _uiState.value =
                        _uiState.value.copy(
                                isLoading = false,
                                totalFiles = 0,
                                processedFiles = 0,
                                loadingProgress = 0f,
                                currentFileName = "",
                                loadingTitle = null
                        )
            }
        }
    }

    // Process shared media with duplication prevention
    fun processSharedMedia(sharedUris: List<Uri>) {
        val newUris = sharedUris.filterNot { it in processedSharedUris }
        if (newUris.isNotEmpty()) {
            processedSharedUris = processedSharedUris + newUris.toSet()
            Log.d("GalleryViewModel", "Processing ${newUris.size} new shared URIs")
            addMediaItems(newUris)
        } else {
            Log.d("GalleryViewModel", "All ${sharedUris.size} URIs already processed, skipping")
        }
    }

    private fun migrateToPublicAlbum() {
        viewModelScope.launch {
            try {
                val privateDir = java.io.File(context.filesDir, "slideshow_media")
                if (!privateDir.exists() || privateDir.listFiles()?.isEmpty() == true) {
                    Log.d("GalleryViewModel", "No private files to migrate")
                    return@launch
                }

                Log.d("GalleryViewModel", "Starting migration from private storage...")
                val migratedUris = mutableListOf<Uri>()

                privateDir.listFiles()?.forEach { file ->
                    try {
                        val fileUri = Uri.fromFile(file)
                        val albumUri = MediaStoreHelper.copyToPublicAlbum(context, fileUri)

                        if (albumUri != null) {
                            migratedUris.add(albumUri)
                            file.delete() // Remove from private storage
                            Log.d("GalleryViewModel", "Migrated: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e("GalleryViewModel", "Failed to migrate: ${file.name}", e)
                    }
                }

                // Update SharedPreferences with new URIs
                if (migratedUris.isNotEmpty()) {
                    // Remove old private file:// URIs
                    val oldUris =
                            preferencesManager.getImageUris(
                                    com.ojitos369.lumaloop.preferences
                                            .SharedPreferencesManager.Ordering.SELECTION
                            )
                    oldUris.forEach { preferencesManager.removeUri(it) }

                    // Add new album URIs in batch
                    preferencesManager.addUris(migratedUris)
                    loadMediaItems()
                    Log.d("GalleryViewModel", "Migration complete: ${migratedUris.size} files")
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Migration error", e)
            }
        }
    }

    private suspend fun syncWithAlbum(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get all files currently in LumaLoop album
            val albumUris = MediaStoreHelper.getAlbumContent(context).toSet()
            val savedUris =
                    preferencesManager.getImageUris(
                            com.ojitos369.lumaloop.preferences
                                    .SharedPreferencesManager.Ordering.SELECTION
                    )

            val toRemove = savedUris.filter { it !in albumUris && it.toString().contains("LumaLoop") }
            val toAdd = albumUris.filter { uri -> 
                !preferencesManager.hasUriWithSameId(savedUris, uri)
            }

            if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
                Log.d("GalleryViewModel", "Syncing with folder: adding ${toAdd.size}, removing ${toRemove.size}")
                if (toRemove.isNotEmpty()) {
                    preferencesManager.removeUris(toRemove)
                }
                if (toAdd.isNotEmpty()) {
                    preferencesManager.addUris(toAdd)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Sync error", e)
        }
        false
    }

    private fun cleanupDuplicates() {
        viewModelScope.launch {
            try {
                val savedUris =
                        preferencesManager
                                .getImageUris(
                                        com.ojitos369.lumaloop.preferences
                                                .SharedPreferencesManager.Ordering.SELECTION
                                )
                                .toSet() // Convert to Set to find duplicates

                val originalSize =
                        preferencesManager.getImageUris(
                                        com.ojitos369.lumaloop.preferences
                                                .SharedPreferencesManager.Ordering.SELECTION
                                )
                                .size

                if (savedUris.size < originalSize) {
                    Log.d("GalleryViewModel", "Found duplicates, cleaning up...")
                    // Remove all and re-add unique ones in batch
                    val allUris =
                            preferencesManager.getImageUris(
                                    com.ojitos369.lumaloop.preferences
                                            .SharedPreferencesManager.Ordering.SELECTION
                            )
                    allUris.forEach { preferencesManager.removeUri(it) }
                    preferencesManager.addUris(savedUris.toList())
                    loadMediaItems()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Cleanup error", e)
            }
        }
    }

    fun removeSelectedItems() {
        viewModelScope.launch {
            val selectedUris = _uiState.value.selectedItems.toList()
            if (selectedUris.isEmpty()) return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: Use createDeleteRequest for bulk deletion
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, selectedUris)
                    _uiState.value = _uiState.value.copy(pendingDeleteIntent = pendingIntent.intentSender)
                } catch (e: Exception) {
                    Log.e("GalleryViewModel", "Failed to create delete request", e)
                    // Fallback to manual loop if request creation fails
                    performManualDelete(selectedUris)
                }
            } else {
                // Android 10 and below or Fallback
                performManualDelete(selectedUris)
            }
        }
    }

    private suspend fun performManualDelete(uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
                Log.d("GalleryViewModel", "Deleted file from storage: $uri")
                preferencesManager.removeUri(uri)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    Log.d("GalleryViewModel", "Caught RecoverableSecurityException for $uri")
                    _uiState.value = _uiState.value.copy(pendingDeleteIntent = e.userAction.actionIntent.intentSender)
                    return // Stop and wait for user permission
                }
                Log.e("GalleryViewModel", "Failed to delete file: $uri", e)
                // Even if physical delete fails (and it's not Recoverable), 
                // we might want to keep it in preferences if it still exists physically 
                // to avoid the sync loop adding it back immediately.
                // But if it's invalid, remove it.
            }
        }
        loadMediaItems()
        deselectAll()
    }

    fun onPendingDeleteResult(success: Boolean) {
        _uiState.value = _uiState.value.copy(pendingDeleteIntent = null)
        if (success) {
            // If the bulk delete was successful, we need to clean up preferences
            // because MediaStore.createDeleteRequest deletes the files but we 
            // still have the URIs in our preferences.
            viewModelScope.launch {
                val selectedUris = _uiState.value.selectedItems
                selectedUris.forEach { preferencesManager.removeUri(it) }
                loadMediaItems()
                deselectAll()
            }
        }
    }

    fun replaceMedia(oldUri: Uri, newUri: Uri) {
        viewModelScope.launch {
            preferencesManager.replaceUri(oldUri, newUri)
            loadMediaItems()
            deselectAll()
        }
    }

    fun refreshMediaItem(uri: Uri) {
        viewModelScope.launch {
            val currentItems = _uiState.value.mediaItems.toMutableList()
            val index = currentItems.indexOfFirst { it.uri == uri }

            if (index != -1) {
                // Refresh specific item
                var name = currentItems[index].name
                var lastModified =
                        System.currentTimeMillis() // Assume modified now if we can't get it

                try {
                    context.contentResolver.query(
                                    uri,
                                    arrayOf(
                                            MediaStore.MediaColumns.DISPLAY_NAME,
                                            MediaStore.MediaColumns.DATE_MODIFIED
                                    ),
                                    null,
                                    null,
                                    null
                            )
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex =
                                            cursor.getColumnIndex(
                                                    MediaStore.MediaColumns.DISPLAY_NAME
                                            )
                                    val dateIndex =
                                            cursor.getColumnIndex(
                                                    MediaStore.MediaColumns.DATE_MODIFIED
                                            )
                                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                                    if (dateIndex != -1) lastModified = cursor.getLong(dateIndex)
                                }
                            }
                } catch (e: Exception) {
                    Log.e("GalleryViewModel", "Error refreshing file info for $uri", e)
                }

                val updatedItem = currentItems[index].copy(name = name, lastModified = lastModified)
                currentItems[index] = updatedItem

                // Re-sort if necessary (optional, but good for consistency)
                val sortedItems = sortMediaItems(currentItems, _uiState.value.sortOption)

                _uiState.value = _uiState.value.copy(mediaItems = sortedItems)
                Log.d("GalleryViewModel", "Refreshed single item: $uri")
            } else {
                Log.w("GalleryViewModel", "Item to refresh not found: $uri, reloading all")
                loadMediaItems()
            }
        }
    }

    private fun getFilteredMediaItems(): List<MediaItem> {
        val baseItems = _uiState.value.mediaItems
        val activeTags = _uiState.value.activeTags
        val hiddenTags = _uiState.value.hiddenTags
        val mode = _uiState.value.tagFilterMode
        
        // First filter out items with ANY hidden tag
        val visibleItems = baseItems.filter { item ->
            item.tags.none { it in hiddenTags }
        }

        val filtered = if (activeTags.isEmpty()) {
            visibleItems
        } else {
            visibleItems.filter { item ->
                when (mode) {
                    SharedPreferencesManager.TagFilterMode.AND -> item.tags.containsAll(activeTags)
                    SharedPreferencesManager.TagFilterMode.OR -> item.tags.any { it in activeTags }
                    SharedPreferencesManager.TagFilterMode.XAND -> !item.tags.containsAll(activeTags)
                    SharedPreferencesManager.TagFilterMode.XOR -> item.tags.count { it in activeTags } == 1
                    else -> item.tags.any { it in activeTags }
                }
            }
        }
        Log.d("GalleryViewModel", "Filtering: mode=$mode, active=${activeTags.joinToString()}, hiddenCount=${hiddenTags.size}, total=${baseItems.size}, filtered=${filtered.size}")
        return filtered
    }

    fun toggleTagFilter(tag: String) {
        val currentActive = _uiState.value.activeTags.toMutableSet()
        if (tag in currentActive) {
            currentActive.remove(tag)
        } else {
            currentActive.add(tag)
        }
        preferencesManager.setActiveTags(currentActive)
        _uiState.value = _uiState.value.copy(activeTags = currentActive, selectedItems = emptySet())
    }

    fun clearTagFilters() {
        preferencesManager.setActiveTags(emptySet())
        _uiState.value = _uiState.value.copy(activeTags = emptySet(), selectedItems = emptySet())
    }

    fun setTagFilterMode(mode: SharedPreferencesManager.TagFilterMode) {
        preferencesManager.setTagFilterMode(mode)
        _uiState.value = _uiState.value.copy(tagFilterMode = mode, selectedItems = emptySet())
    }

    fun setHiddenTags(tags: Set<String>) {
        preferencesManager.setHiddenTags(tags)
        _uiState.value = _uiState.value.copy(hiddenTags = tags)
        loadMediaItems(preserveSelection = true)
    }

    fun setAutoTagEnabled(enabled: Boolean) {
        preferencesManager.setAutoTagEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoTagEnabled = enabled)
        loadMediaItems(preserveSelection = true)
    }

    fun dismissAutotagPrompt() {
        _uiState.value = _uiState.value.copy(showAutotagPrompt = false, urisForAutotag = emptyList())
    }

    fun performAutoTag(targetUris: List<Uri>? = null) {
        val urisToProcess = targetUris ?: _uiState.value.urisForAutotag
        if (urisToProcess.isEmpty()) return
        
        _uiState.value = _uiState.value.copy(
            showAutotagPrompt = false,
            isLoading = true,
            isAutoTagging = true,
            loadingTitle = "Autotagging...",
            totalFiles = urisToProcess.size,
            processedFiles = 0,
            loadingProgress = 0f
        )
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val masterTags = preferencesManager.masterTagList
                urisToProcess.forEachIndexed { index, uri ->
                    val displayName = com.ojitos369.lumaloop.ui.utils.MediaStoreHelper.getDisplayName(context, uri) ?: uri.lastPathSegment ?: "Unknown"
                    val normalizedName = displayName.lowercase().replace(Regex("[^a-z0-9]"), "")
                    
                    val currentTags = preferencesManager.getTags(uri).toSet()
                    
                    masterTags.forEach { tag ->
                        val normalizedTag = tag.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (normalizedTag.isNotEmpty() && normalizedName.contains(normalizedTag)) {
                            if (!currentTags.contains(tag)) {
                                preferencesManager.addTag(uri, tag)
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            processedFiles = index + 1,
                            loadingProgress = (index + 1).toFloat() / urisToProcess.size,
                            currentFileName = displayName
                        )
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAutoTagging = false,
                    loadingTitle = null,
                    urisForAutotag = emptyList()
                )
                loadMediaItems(preserveSelection = true)
            }
        }
    }

    fun addTagToSelected(tag: String) {
        viewModelScope.launch {
            val currentSelected = _uiState.value.selectedItems
            currentSelected.forEach { uri ->
                preferencesManager.addTag(uri, tag)
            }
            loadMediaItems(preserveSelection = true)
        }
    }

    fun removeTagFromSelected(tag: String) {
        viewModelScope.launch {
            val currentSelected = _uiState.value.selectedItems
            currentSelected.forEach { uri ->
                preferencesManager.removeTag(uri, tag)
            }
            loadMediaItems(preserveSelection = true)
        }
    }

    private fun isVideoUri(uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.startsWith("video/") == true
        } catch (e: Exception) {
            false
        }
    }
}

class GalleryViewModelFactory(
        private val context: Context,
        private val preferencesManager: SharedPreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return GalleryViewModel(context, preferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
