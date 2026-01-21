package com.ojitos369.lumaloop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailRatioBottomSheet(
    selectedRatio: String,
    onRatioSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Thumbnail Aspect Ratio",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            val ratios = listOf(
                "3:4" to "3:4 (Portrait)",
                "1:1" to "1:1 (Square)",
                "4:3" to "4:3 (Landscape)",
                "Natural" to "Natural (Original)"
            )
            
            ratios.forEach { (value, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    modifier = Modifier.clickable {
                        onRatioSelected(value)
                        onDismiss()
                    },
                    trailingContent = {
                        if (selectedRatio == value) {
                            RadioButton(
                                selected = true,
                                onClick = null
                            )
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
