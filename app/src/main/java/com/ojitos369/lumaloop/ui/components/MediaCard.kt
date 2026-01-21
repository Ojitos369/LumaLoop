package com.ojitos369.lumaloop.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ojitos369.lumaloop.ui.utils.VideoThumbnailLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    uri: Uri,
    isSelected: Boolean = false,
    isVideo: Boolean = false,
    thumbnailRatio: String = "3:4",
    lastModified: Long = 0,
    tagCount: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    onCropClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    val scope = rememberCoroutineScope()
    
    var videoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(uri, isVideo) {
        if (isVideo) {
            scope.launch {
                videoThumbnail = VideoThumbnailLoader.loadThumbnail(context, uri)
            }
        }
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale_animation"
    )
    
    // Calculate aspect ratio from thumbnailRatio string
    val aspectRatio = when (thumbnailRatio) {
        "9:16" -> 9f / 16f
        "16:9" -> 16f / 9f
        "3:4" -> 3f / 4f
        "4:3" -> 4f / 3f
        "1:1" -> 1f
        "Natural" -> null
        else -> 3f / 4f  // Default
    }
    
    Card(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            if (isVideo) {
                if (videoThumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = videoThumbnail!!.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle, "Loading",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(uri)
                        .setParameter("key", lastModified)
                        .build(),
                    contentDescription = "Image",
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    contentScale = ContentScale.Crop
                )
            }
            
            if (isVideo) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircle, "Video",
                        modifier = Modifier.size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(4.dp),
                        tint = Color.White
                    )
                }
            }
            
            if (isSelected && !isVideo && onCropClick != null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onCropClick,
                        modifier = Modifier.size(40.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Crop, "Crop",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (tagCount > 0) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        shape = CircleShape,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tagCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
