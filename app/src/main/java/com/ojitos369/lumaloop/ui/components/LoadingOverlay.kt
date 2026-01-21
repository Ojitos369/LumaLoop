package com.ojitos369.lumaloop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Modern, elegant loading overlay for file operations. Shows animated progress with file count and
 * current file name.
 */
@Composable
fun LoadingOverlay(
        isVisible: Boolean,
        title: String? = null,
        currentFileName: String = "",
        processedFiles: Int = 0,
        totalFiles: Int = 0,
        progress: Float = 0f,
        modifier: Modifier = Modifier
) {
    if (!isVisible) return

    // Pulsing animation for the ring
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val pulseScale by
            infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1000, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "pulse"
            )

    val ringAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.7f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1200, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "alpha"
            )

    // Rotating dots animation
    val rotation by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "rotation"
            )

    Box(
            modifier =
                    modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .zIndex(100f)
                            .clickable(enabled = false) {}, // Block touches
            contentAlignment = Alignment.Center
    ) {
        // Main container card
        Card(
                modifier = Modifier.widthIn(min = 280.dp, max = 340.dp).padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated icon with pulsing ring
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    // Outer pulsing ring
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .scale(pulseScale)
                                            .alpha(ringAlpha)
                                            .clip(CircleShape)
                                            .background(
                                                    Brush.radialGradient(
                                                            colors =
                                                                    listOf(
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary.copy(
                                                                                    alpha = 0.4f
                                                                            ),
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary.copy(
                                                                                    alpha = 0.1f
                                                                            ),
                                                                            Color.Transparent
                                                                    )
                                                    )
                                            )
                    )

                    // Inner circle with icon
                    Box(
                            modifier =
                                    Modifier.size(56.dp)
                                            .clip(CircleShape)
                                            .background(
                                                    Brush.verticalGradient(
                                                            colors =
                                                                    listOf(
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary,
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primaryContainer
                                                                    )
                                                    )
                                            ),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Title
                Text(
                        text =
                                title ?: if (totalFiles > 0) {
                                    "Agregando multimedia"
                                } else {
                                    "Procesando..."
                                },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                // Progress section
                if (totalFiles > 0) {
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // File counter
                        Text(
                                text = "Agregando $processedFiles de $totalFiles",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Progress bar
                        LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )

                        // Percentage
                        Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                        )

                        // Current file name
                        if (currentFileName.isNotEmpty()) {
                            Text(
                                    text = currentFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                            ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Indeterminate progress
                    CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}
