package com.ojitos369.lumaloop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
        TagFilterMode.AND to "Must have ALL active tags",
        TagFilterMode.OR to "Can have ANY active tag",
        TagFilterMode.XAND to "Must NOT have all active tags",
        TagFilterMode.XOR to "Must have EXACTLY ONE active tag"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
