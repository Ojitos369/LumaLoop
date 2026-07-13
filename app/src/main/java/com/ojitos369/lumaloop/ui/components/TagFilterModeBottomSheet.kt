package com.ojitos369.lumaloop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager.TagFilterMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterModeBottomSheet(
    currentMode: TagFilterMode,
    onDismiss: () -> Unit,
    onSave: (TagFilterMode) -> Unit
) {
    val modes = listOf(
        // Positive modes
        TagFilterMode.HAS_ALL to "Has all selected tags (may have more)",
        TagFilterMode.HAS_ANY to "Has at least one selected tag (may have more)",
        TagFilterMode.HAS_EXACTLY_ONE to "Has exactly one of the selected tags (XOR)",
        TagFilterMode.ONLY_SELECTED to "Has only selected tags, nothing else",
        TagFilterMode.EXACTLY_ALL to "Has exactly all selected tags and nothing else",
        // Negative modes (opposites)
        TagFilterMode.NOT_ANY to "Has none of the selected tags",
        TagFilterMode.NOT_ALL to "Does not have all of the selected tags",
        TagFilterMode.NOT_ONLY to "Exclude items that have only selected tags",
        TagFilterMode.NOT_EXACTLY to "Exclude items that have exactly the selected tags"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Tag Filter Mode",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            modes.forEach { (mode, description) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            onSave(mode)
                            onDismiss()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMode == mode)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = {
                                onSave(mode)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = mode.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
