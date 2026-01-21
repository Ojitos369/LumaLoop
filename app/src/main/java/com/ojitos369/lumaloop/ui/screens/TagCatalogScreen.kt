package com.ojitos369.lumaloop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager
import com.ojitos369.lumaloop.ui.components.LoadingOverlay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagCatalogScreen(
    preferencesManager: SharedPreferencesManager = SharedPreferencesManager.fromContext(LocalContext.current)
) {
    val context = LocalContext.current
    val viewModel: TagCatalogViewModel = viewModel(
        factory = TagCatalogViewModelFactory(context, preferencesManager)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportTags(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importTags(it) }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<TagInfo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<TagInfo?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAutotagConfirm by remember { mutableStateOf(false) }
    var specificAutotagTag by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tag Catalog") },
                actions = {
                    IconButton(onClick = { showAutotagConfirm = true }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Autotag Now")
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = { viewModel.setSortOption(TagSortOption.NAME_ASC); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z-A)") },
                            onClick = { viewModel.setSortOption(TagSortOption.NAME_DESC); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Most Elements") },
                            onClick = { viewModel.setSortOption(TagSortOption.COUNT_DESC); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Least Elements") },
                            onClick = { viewModel.setSortOption(TagSortOption.COUNT_ASC); showSortMenu = false }
                        )
                    }
                    
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Tags") },
                            onClick = {
                                showOptionsMenu = false
                                exportLauncher.launch("tags_backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Tags") },
                            onClick = {
                                showOptionsMenu = false
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Tag")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.tags.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tags in catalog. Add some or use images/videos.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.tags) { tagInfo ->
                        TagItem(
                            tagInfo = tagInfo,
                            onRename = { showRenameDialog = tagInfo },
                            onDelete = { showDeleteConfirm = tagInfo }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            
            LoadingOverlay(
                isVisible = uiState.isAutoTagging,
                title = "Autotagging...",
                currentFileName = uiState.currentAutoTagItem,
                processedFiles = uiState.processedCount,
                totalFiles = uiState.totalCount,
                progress = if (uiState.totalCount > 0) uiState.processedCount.toFloat() / uiState.totalCount else 0f
            )
        }
    }
    
    // Dialogs
    if (showAddDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Tag") },
            text = {
                TextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    placeholder = { Text("Tag name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTagName.isNotBlank()) {
                        val tags = newTagName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        tags.forEach { viewModel.addTag(it) }
                        specificAutotagTag = if (tags.size == 1) tags[0] else null
                    }
                    showAddDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    showRenameDialog?.let { tag ->
        var newName by remember { mutableStateOf(tag.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Tag") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank() && trimmed != tag.name) {
                        viewModel.renameTag(tag.name, trimmed)
                        specificAutotagTag = trimmed
                        showRenameDialog = null
                    } else {
                        showRenameDialog = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }
    
    showDeleteConfirm?.let { tag ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Tag") },
            text = { Text("Are you sure you want to delete '${tag.name}'? This will remove the tag from all media items. The media items themselves will NOT be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTag(tag.name)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
    
    if (showAutotagConfirm) {
        AlertDialog(
            onDismissRequest = { showAutotagConfirm = false },
            title = { Text("Run Autotag") },
            text = { Text("This will analyze all media names and automatically assign tags from your catalog. This may take some time depending on the number of items.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.performAutoTag()
                    showAutotagConfirm = false
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showAutotagConfirm = false }) { Text("Cancel") }
            }
        )
    }

    specificAutotagTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { specificAutotagTag = null },
            title = { Text("Run Autotag for '$tag'") },
            text = { Text("Do you want to run autotag for the tag '$tag' over all current elements? Elements whose names contain this tag will have it assigned automatically.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.performAutoTag(targetTag = tag)
                    specificAutotagTag = null
                }) { Text("Run Now") }
            },
            dismissButton = {
                TextButton(onClick = { specificAutotagTag = null }) { Text("No, thanks") }
            }
        )
    }
}

@Composable
fun TagItem(
    tagInfo: TagInfo,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = tagInfo.name,
                fontWeight = FontWeight.Bold
            ) 
        },
        supportingContent = { 
            Text(text = "${tagInfo.count} items associated") 
        },
        trailingContent = {
            if (!tagInfo.isSystemTag) {
                Row {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
