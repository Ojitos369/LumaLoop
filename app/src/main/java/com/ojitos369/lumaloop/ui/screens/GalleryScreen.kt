package com.ojitos369.lumaloop.ui.screens

import androidx.activity.ComponentActivity
import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yalantis.ucrop.UCrop
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager
import com.ojitos369.lumaloop.ui.components.CropHelper
import com.ojitos369.lumaloop.ui.components.FullscreenImageDialog
import com.ojitos369.lumaloop.ui.components.HiddenTagsBottomSheet
import com.ojitos369.lumaloop.ui.components.LoadingOverlay
import com.ojitos369.lumaloop.ui.components.MediaCard
import com.ojitos369.lumaloop.ui.components.TagFilterBottomSheet
import com.ojitos369.lumaloop.ui.components.TagFilterModeBottomSheet
import com.ojitos369.lumaloop.ui.components.VideoPlayerDialog
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun GalleryScreen(
        sharedUris: List<Uri>? = null,
        activity: ComponentActivity,
        viewModel: GalleryViewModel =
                viewModel(
                        factory =
                                GalleryViewModelFactory(
                                        activity,
                                        SharedPreferencesManager(
                                                activity.getSharedPreferences(
                                                        "${activity.packageName}_preferences",
                                                        Activity.MODE_PRIVATE
                                                )
                                        )
                                )
                )
) {
    val uiState by viewModel.uiState.collectAsState()

    // Media viewer state
    var fullscreenImageUri by remember { mutableStateOf<Uri?>(null) }
    var videoPlayerUri by remember { mutableStateOf<Uri?>(null) }

    // Crop state
    var uriToCrop by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Sorting menu state
    var showSortMenu by remember { mutableStateOf(false) }

    // Tag management state
    var showTagDialog by remember { mutableStateOf(false) }
    
    // Tag filter bottom sheet state
    var showTagFilterSheet by remember { mutableStateOf(false) }

    
    val prefs = remember {
        activity.getSharedPreferences("${activity.packageName}_preferences", Activity.MODE_PRIVATE)
    }

    // Zoom Progression Logic
    // Sequence: 1Col(9:16) -> 1Col(3:4) -> 1Col(1:1) -> 2Col(9:16) ...
    val ratioList = remember { listOf("9:16", "3:4", "1:1") }
    
    // Calculate initial step
    var currentStep by remember { 
        val savedCol = prefs.getInt("gallery_columns", 3).coerceIn(1, 6)
        val savedRatio = prefs.getString("thumbnail_ratio", "3:4") ?: "3:4"
        val ratioIndex = ratioList.indexOf(savedRatio).takeIf { it != -1 } ?: 1
        mutableStateOf((savedCol - 1) * 3 + ratioIndex)
    }

    // Derived states
    val columnCount = (currentStep / 3) + 1
    val activeRatio = ratioList[currentStep % 3]

    var gridScale by remember { mutableStateOf(1f) }

    // Save settings when step changes
    LaunchedEffect(currentStep) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putInt("gallery_columns", columnCount)
                .putString("thumbnail_ratio", activeRatio)
                .apply()
        }
    }

    // Media picker launcher
    val pickMediaLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(),
                    onResult = { uris ->
                        if (uris.isNotEmpty()) {
                            viewModel.addMediaItems(uris)
                        }
                    }
            )

    // Permission launcher
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    Log.d("GalleryScreen", "Storage permissions granted")
                } else {
                    Log.w("GalleryScreen", "Storage permissions denied")
                    // TODO: Show explanation to user
                }
            }

    // Request permissions on first launch
    val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

    LaunchedEffect(Unit) {
        // Check if permissions already granted
        val needsPermission =
                permissions.any {
                    activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }

        if (needsPermission) {
            permissionLauncher.launch(permissions)
        }
    }

    // Handle shared media from intent - delegate to ViewModel
    LaunchedEffect(sharedUris) {
        if (!sharedUris.isNullOrEmpty()) {
            viewModel.processSharedMedia(sharedUris)
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Pending overwrite state for permission requests
    var pendingOverwrite by remember { mutableStateOf<Pair<Uri, Uri>?>(null) }

    // Intent sender launcher for RecoverableSecurityException
    val intentSenderLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    pendingOverwrite?.let { (sourceUri, targetUri) ->
                        coroutineScope.launch {
                            try {
                                // Retry overwrite
                                withContext(Dispatchers.IO) {
                                    activity.contentResolver.openInputStream(sourceUri)?.use { input
                                        ->
                                        activity.contentResolver.openOutputStream(targetUri, "w")
                                                ?.use { output -> input.copyTo(output) }
                                    }
                                    // Delete temp file
                                    try {
                                        File(sourceUri.path!!).delete()
                                    } catch (e: Exception) {}
                                }

                                // Force update
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val values =
                                                ContentValues().apply {
                                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                                }
                                        activity.contentResolver.update(
                                                targetUri,
                                                values,
                                                null,
                                                null
                                        )
                                        values.clear()
                                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                        activity.contentResolver.update(
                                                targetUri,
                                                values,
                                                null,
                                                null
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("GalleryScreen", "Failed to update metadata", e)
                                }

                                delay(500)
                                viewModel.loadMediaItems()
                                Log.d(
                                        "GalleryScreen",
                                        "Overwrite successful after permission grant"
                                )
                            } catch (e: Exception) {
                                Log.e(
                                        "GalleryScreen",
                                        "Failed to overwrite after permission grant",
                                        e
                                )
                            }
                            pendingOverwrite = null
                        }
                    }
                } else {
                    pendingOverwrite = null
                }
            }

    // MediaStore Delete Request Launcher
    val deleteRequestLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                viewModel.onPendingDeleteResult(result.resultCode == Activity.RESULT_OK)
            }

    // Launch delete request when it appears in state
    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { intentSender ->
            val intentRequest = IntentSenderRequest.Builder(intentSender).build()
            deleteRequestLauncher.launch(intentRequest)
        }
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE intent
    val manageStorageLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) {
                // Check if permission is granted after returning from settings
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (android.os.Environment.isExternalStorageManager()) {
                        Log.d("GalleryScreen", "MANAGE_EXTERNAL_STORAGE granted")
                    } else {
                        Log.d("GalleryScreen", "MANAGE_EXTERNAL_STORAGE denied")
                    }
                }
            }

    // Crop launcher
    val cropLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d("GalleryScreen", "Crop result code: ${result.resultCode}")
                if (result.resultCode == Activity.RESULT_OK) {
                    val resultUri = UCrop.getOutput(result.data!!)
                    Log.d("GalleryScreen", "Crop result URI: $resultUri, Target URI: $uriToCrop")

                    if (resultUri != null && uriToCrop != null) {
                        val targetUri = uriToCrop!!
                        coroutineScope.launch {
                            try {
                                Log.d("GalleryScreen", "Starting overwrite operation...")
                                // Overwrite original file
                                withContext(Dispatchers.IO) {
                                    try {
                                        val inputStream =
                                                activity.contentResolver.openInputStream(resultUri)
                                        var outputStream: java.io.OutputStream? = null
                                        try {
                                            outputStream =
                                                    activity.contentResolver.openOutputStream(
                                                            targetUri,
                                                            "w"
                                                    )
                                        } catch (e: Exception) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                                            e is
                                                                    android.app.RecoverableSecurityException
                                            ) {
                                                // If we have MANAGE_EXTERNAL_STORAGE, this
                                                // shouldn't happen, but just in case
                                                if (Build.VERSION.SDK_INT >=
                                                                Build.VERSION_CODES.R &&
                                                                !android.os.Environment
                                                                        .isExternalStorageManager()
                                                ) {
                                                    Log.d(
                                                            "GalleryScreen",
                                                            "Caught RecoverableSecurityException, requesting MANAGE_EXTERNAL_STORAGE"
                                                    )
                                                    val intent =
                                                            Intent(
                                                                    android.provider.Settings
                                                                            .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                            )
                                                    intent.data =
                                                            Uri.parse(
                                                                    "package:${activity.packageName}"
                                                            )
                                                    manageStorageLauncher.launch(intent)
                                                    return@withContext
                                                }

                                                Log.d(
                                                        "GalleryScreen",
                                                        "Caught RecoverableSecurityException, requesting permission"
                                                )
                                                pendingOverwrite = resultUri to targetUri
                                                val intentSender =
                                                        e.userAction.actionIntent.intentSender
                                                val intent =
                                                        androidx.activity.result.IntentSenderRequest
                                                                .Builder(intentSender)
                                                                .build()
                                                intentSenderLauncher.launch(intent)
                                                return@withContext
                                            } else {
                                                Log.w(
                                                        "GalleryScreen",
                                                        "Failed to open output stream with 'w', trying 'wt'",
                                                        e
                                                )
                                                try {
                                                    outputStream =
                                                            activity.contentResolver
                                                                    .openOutputStream(
                                                                            targetUri,
                                                                            "wt"
                                                                    )
                                                } catch (e2: Exception) {
                                                    Log.e(
                                                            "GalleryScreen",
                                                            "Failed to open output stream with 'wt'",
                                                            e2
                                                    )
                                                }
                                            }
                                        }

                                        if (inputStream != null && outputStream != null) {
                                            inputStream.use { input ->
                                                outputStream.use { output -> input.copyTo(output) }
                                            }
                                            Log.d("GalleryScreen", "File overwritten successfully")

                                            // Delete temp file
                                            try {
                                                File(resultUri.path!!).delete()
                                            } catch (e: Exception) {}

                                            // Force update
                                            withContext(Dispatchers.Main) {
                                                // Force MediaStore update for the original URI
                                                // Use MediaScannerConnection to force a re-scan of
                                                // the file
                                                val path =
                                                        com.ojitos369.lumaloop.ui
                                                                .utils.MediaStoreHelper
                                                                .getRealPathFromUri(
                                                                        activity,
                                                                        targetUri
                                                                )
                                                if (path != null) {
                                                    android.media.MediaScannerConnection.scanFile(
                                                            activity,
                                                            arrayOf(path),
                                                            arrayOf(
                                                                    "image/jpeg"
                                                            ), // Assuming JPEG for now, ideally get
                                                            // from MIME type
                                                            null
                                                    )
                                                    Log.d(
                                                            "GalleryScreen",
                                                            "Triggered MediaScannerConnection for $path"
                                                    )
                                                } else {
                                                    Log.w(
                                                            "GalleryScreen",
                                                            "Could not get real path for $targetUri, fallback to reload"
                                                    )
                                                }

                                                // Clear Coil cache to force immediate update
                                                val imageLoader = coil.Coil.imageLoader(activity)
                                                imageLoader.memoryCache?.remove(
                                                        coil.memory.MemoryCache.Key(
                                                                targetUri.toString()
                                                        )
                                                )
                                                imageLoader.diskCache?.remove(targetUri.toString())
                                                Log.d(
                                                        "GalleryScreen",
                                                        "Cleared Coil cache for $targetUri"
                                                )

                                                delay(1000) // Wait a bit longer for the scanner
                                                viewModel.loadMediaItems()
                                            }
                                        } else {
                                            Log.e(
                                                    "GalleryScreen",
                                                    "Failed to open streams. Input: $inputStream, Output: $outputStream"
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e("GalleryScreen", "Error in crop flow", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("GalleryScreen", "Error in crop flow", e)
                            }
                        }
                    } else {
                        Log.e(
                                "GalleryScreen",
                                "Missing URIs. Result: $resultUri, Target: $uriToCrop"
                        )
                    }
                    uriToCrop = null
                } else if (result.resultCode == Activity.RESULT_CANCELED) {
                    Log.d("GalleryScreen", "Crop cancelled")
                    val resultUri = UCrop.getOutput(result.data ?: Intent())
                    if (resultUri != null) {
                        try {
                            File(resultUri.path!!).delete()
                        } catch (e: Exception) {}
                    }
                    uriToCrop = null
                } else {
                    Log.e("GalleryScreen", "Crop failed with result code: ${result.resultCode}")
                    uriToCrop = null
                }
            }
    // Loading overlay with progress
    LoadingOverlay(
            isVisible = uiState.isLoading,
            title = uiState.loadingTitle,
            currentFileName = uiState.currentFileName,
            processedFiles = uiState.processedFiles,
            totalFiles = uiState.totalFiles,
            progress = uiState.loadingProgress
    )

    val filteredItems =
                remember(uiState.mediaItems, uiState.activeTags, uiState.hiddenTags, uiState.tagFilterMode) {
                    val visibleItems = uiState.mediaItems.filter { item ->
                        item.tags.none { it in uiState.hiddenTags }
                    }
                    if (uiState.activeTags.isEmpty()) {
                        visibleItems
                    } else {
                        visibleItems.filter { item ->
                            when (uiState.tagFilterMode) {
                                SharedPreferencesManager.TagFilterMode.AND -> item.tags.containsAll(uiState.activeTags)
                                SharedPreferencesManager.TagFilterMode.OR -> item.tags.any { it in uiState.activeTags }
                                SharedPreferencesManager.TagFilterMode.XAND -> !item.tags.containsAll(uiState.activeTags)
                                SharedPreferencesManager.TagFilterMode.XOR -> item.tags.count { it in uiState.activeTags } == 1
                            }
                        }
                    }
                }
    val gridState = rememberLazyStaggeredGridState()

    Scaffold(
            topBar = {
                // Filter chips
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Filter by Tags:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                        }

                        // Tag filter button - shows count of active filters
                        Row(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { showTagFilterSheet = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (uiState.activeTags.isEmpty()) {
                                        "Filter by Tags"
                                    } else {
                                        "Filtering: ${uiState.activeTags.size} tags"
                                    }
                                )
                            }
                            if (uiState.activeTags.isNotEmpty()) {
                                FilledTonalIconButton(
                                    onClick = { viewModel.clearTagFilters() }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear filters")
                                }
                            }
                        }

                        // Sorting and Selection
                        FlowRow(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Sort Button
                                Box {
                                    TextButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sort")
                                    }
                                    DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false }
                                    ) {
                                        val current = uiState.sortOption
                                        DropdownMenuItem(
                                                text = { 
                                                    val suffix = if (current == SortOption.DATE_DESC) " (Desc)" else if (current == SortOption.DATE_ASC) " (Asc)" else ""
                                                    Text("By Date$suffix") 
                                                },
                                                onClick = {
                                                    val next = if (current == SortOption.DATE_DESC) SortOption.DATE_ASC else SortOption.DATE_DESC
                                                    viewModel.setSortOption(next)
                                                    showSortMenu = false
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = { 
                                                    val suffix = if (current == SortOption.NAME_DESC) " (Desc)" else if (current == SortOption.NAME_ASC) " (Asc)" else ""
                                                    Text("By Name$suffix") 
                                                },
                                                onClick = {
                                                    val next = if (current == SortOption.NAME_DESC) SortOption.NAME_ASC else SortOption.NAME_DESC
                                                    viewModel.setSortOption(next)
                                                    showSortMenu = false
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = { 
                                                    val suffix = if (current == SortOption.TAG_COUNT_DESC) " (Desc)" else if (current == SortOption.TAG_COUNT_ASC) " (Asc)" else ""
                                                    Text("By Tag Count$suffix") 
                                                },
                                                onClick = {
                                                    val next = if (current == SortOption.TAG_COUNT_DESC) SortOption.TAG_COUNT_ASC else SortOption.TAG_COUNT_DESC
                                                    viewModel.setSortOption(next)
                                                    showSortMenu = false
                                                }
                                        )
                                    }
                                }

                                if (uiState.selectedItems.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${uiState.selectedItems.size} sel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { showTagDialog = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Tags")
                                    }
                                }
                            }

                            // Select/Deselect Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.selectAll() }) {
                                    Text("Sel All")
                                }
                                TextButton(onClick = { viewModel.deselectAll() }) {
                                    Text("None")
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Navigation Buttons
                    if (filteredItems.isNotEmpty() && (gridState.firstVisibleItemIndex > 0 || gridState.isScrollInProgress)) {
                        SmallFloatingActionButton(
                            onClick = { coroutineScope.launch { gridState.animateScrollToItem(0) } }
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Go to Top")
                        }
                        SmallFloatingActionButton(
                            onClick = { coroutineScope.launch { gridState.animateScrollToItem(filteredItems.size - 1) } }
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Go to Bottom")
                        }
                    }

                    // Delete FAB (only show when items selected)
                    if (uiState.selectedItems.isNotEmpty()) {
                        FloatingActionButton(
                                onClick = { viewModel.removeSelectedItems() },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) { Icon(Icons.Default.Delete, contentDescription = "Delete selected") }
                    }

                    // Add FAB
                    FloatingActionButton(
                            onClick = {
                                pickMediaLauncher.launch(
                                        PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia
                                                        .ImageAndVideo
                                        )
                                )
                            }
                    ) { Icon(Icons.Default.Add, contentDescription = "Add media") }
                }
            }
    ) { paddingValues ->
        if (filteredItems.isEmpty()) { // Empty state
            Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues = paddingValues),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = if (uiState.activeTags.isEmpty()) {
                            "No media. Tap + to add images or videos"
                        } else {
                            "No items match the selected tags"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val columnSize =
                    when (columnCount) {
                        2 -> 180.dp
                        3 -> 120.dp
                        4 -> 90.dp
                        5 -> 72.dp
                        else -> 120.dp
                    }
            // Media grid
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues = paddingValues) // Handle TopBar/BottomBar here
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var previousDistance = -1f
                            
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val changes = event.changes.filter { it.pressed }
                                
                                if (changes.size >= 2) {
                                    var totalDistance = 0f
                                    var count = 0
                                    for (i in 0 until changes.size - 1) {
                                        for (j in i + 1 until changes.size) {
                                            val p1 = changes[i].position
                                            val p2 = changes[j].position
                                            val dx = p1.x - p2.x
                                            val dy = p1.y - p2.y
                                            totalDistance += sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                            count++
                                        }
                                    }
                                    val currentDistance = if (count > 0) totalDistance / count else 0f
                                    
                                    if (previousDistance > 0f && currentDistance > 0f) {
                                        val zoom = currentDistance / previousDistance
                                        if (zoom != 1f) {
                                            gridScale *= zoom
                                            gridScale = gridScale.coerceIn(0.4f, 2.5f)
                                            
                                            // Thresholds for changing steps
                                            if (gridScale < 0.6f) {
                                                if (currentStep < (6 * 3) - 1) {
                                                    currentStep++
                                                    gridScale = 1f
                                                }
                                            } else if (gridScale > 1.5f) {
                                                if (currentStep > 0) {
                                                    currentStep--
                                                    gridScale = 1f
                                                }
                                            }
                                        }
                                    }
                                    previousDistance = currentDistance
                                } else {
                                    previousDistance = -1f
                                }
                            } while (event.changes.any { change -> change.pressed })
                        }
                    }
            ) {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Adaptive(minSize = columnSize),
                    contentPadding =
                            PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = filteredItems, key = { it.uri }) { mediaItem ->
                        MediaCard(
                            uri = mediaItem.uri,
                            isSelected = mediaItem.uri in uiState.selectedItems,
                            isVideo = mediaItem.isVideo,
                            thumbnailRatio = activeRatio,
                            lastModified = mediaItem.lastModified,
                            tagCount = mediaItem.tags.size,
                            onClick = {
                                viewModel.toggleSelection(mediaItem.uri)
                            },
                            onLongClick = {
                                if (mediaItem.isVideo) {
                                    videoPlayerUri = mediaItem.uri
                                } else {
                                    fullscreenImageUri = mediaItem.uri
                                }
                            },
                            onCropClick = if (!mediaItem.isVideo) {
                                {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                        !android.os.Environment.isExternalStorageManager()
                                    ) {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        intent.data = Uri.parse("package:${activity.packageName}")
                                        manageStorageLauncher.launch(intent)
                                    } else {
                                        uriToCrop = mediaItem.uri
                                        CropHelper.launchCrop(activity, mediaItem.uri, cropLauncher)
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
    
    // Fullscreen image viewer
    fullscreenImageUri?.let { uri ->
        val item = uiState.mediaItems.find { it.uri == uri }
        FullscreenImageDialog(
            uri = uri, 
            name = item?.name ?: "",
            onDismiss = { fullscreenImageUri = null }
        )
    }

    // Video player
    videoPlayerUri?.let { uri ->
        val item = uiState.mediaItems.find { it.uri == uri }
        VideoPlayerDialog(
            uri = uri, 
            name = item?.name ?: "",
            onDismiss = { videoPlayerUri = null }
        )
    }


    if (showTagDialog) {
        val selectedItemsTags = uiState.mediaItems
            .filter { it.uri in uiState.selectedItems }
            .flatMap { it.tags }
            .toSet()
            
        TagManagementDialog(
            availableTags = uiState.availableTags,
            selectedItemsTags = selectedItemsTags,
            onAddTag = viewModel::addTagToSelected,
            onRemoveTag = viewModel::removeTagFromSelected,
            onAutotag = {
                viewModel.performAutoTag(uiState.selectedItems.toList())
            },
            onDismiss = { showTagDialog = false }
        )
    }

    if (uiState.showAutotagPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAutotagPrompt() },
            title = { Text("Autotag New Media?") },
            text = { Text("Do you want to automatically assign tags from your catalog to the newly added media?") },
            confirmButton = {
                Button(onClick = { viewModel.performAutoTag() }) { Text("Perform Autotag") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAutotagPrompt() }) { Text("Skip") }
            }
        )
    }
    
    if (showTagFilterSheet) {
        TagFilterBottomSheet(
            availableTags = uiState.availableTags,
            activeTags = uiState.activeTags,
            onToggleTag = { tag -> viewModel.toggleTagFilter(tag) },
            onClearAll = { viewModel.clearTagFilters() },
            onDismiss = { showTagFilterSheet = false }
        )
    }
}
}
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagManagementDialog(
    availableTags: Set<String>,
    selectedItemsTags: Set<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAutotag: () -> Unit,
    onDismiss: () -> Unit
) {
    var newTagText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sort tags alphabetically only (stable order, don't move selected to top)
    val allTags = remember(availableTags) {
        availableTags.toList().sortedWith(
            compareBy<String> { it != "Images" && it != "Videos" }.thenBy { it.lowercase() }
        )
    }
    
    val filteredTags = remember(allTags, searchQuery) {
        if (searchQuery.isBlank()) allTags
        else allTags.filter { it.lowercase().contains(searchQuery.lowercase()) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage Tags",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Row {
                        IconButton(onClick = onAutotag) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Autotag")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Add new tag section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            if (newTagText.isNotBlank()) {
                                val tags = newTagText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                tags.forEach { onAddTag(it) }
                                newTagText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tags...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Selected tags summary
                if (selectedItemsTags.isNotEmpty()) {
                    Text(
                        text = "Selected (${selectedItemsTags.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedItemsTags.sorted().forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveTag(tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // All tags grid
                Text(
                    text = "All Tags:",
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredTags.forEach { tag ->
                            val isSelected = tag in selectedItemsTags
                            val isSystemTag = tag == "Images" || tag == "Videos"
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) onRemoveTag(tag) else onAddTag(tag)
                                },
                                label = { Text(tag) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when {
                                            isSystemTag && tag == "Images" -> Icons.Default.Image
                                            isSystemTag && tag == "Videos" -> Icons.Default.Videocam
                                            isSelected -> Icons.Default.Check
                                            else -> Icons.AutoMirrored.Filled.Label
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    if (filteredTags.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tags found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Done button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
