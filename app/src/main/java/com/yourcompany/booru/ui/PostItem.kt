package com.yourcompany.booru.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.yourcompany.booru.data.ImagePost

@Composable
fun PostItem(
    post: ImagePost,
    onFavorite: () -> Unit,
    onBlockTag: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onAddToSearch: (String) -> Unit,
    onImageClick: () -> Unit, // Изменено с (String) -> Unit на () -> Unit
    currentBooru: String,
    posts: List<ImagePost>
) {
    var isFavorited by remember { mutableStateOf(post.isFavorited) }
    var triggerAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (triggerAnimation) 1.3f else 1f,
        animationSpec = tween(durationMillis = 300)
    )
    val color by animateColorAsState(
        targetValue = if (isFavorited) Color.Red else Color(0xFF778DA9),
        animationSpec = tween(durationMillis = 300)
    )

    LaunchedEffect(post.isFavorited) {
        isFavorited = post.isFavorited
    }

    val fileUrl = post.effectiveFileUrl ?: post.effectivePreviewUrl ?: ""
    val previewUrl = post.effectivePreviewUrl ?: post.effectiveFileUrl
    val fileExtension = fileUrl.substringAfterLast(".", "").lowercase()
    val isImage = fileExtension in listOf("jpg", "jpeg", "png", "gif")
    val isVideo = fileExtension in listOf("mp4", "webm")
    val isFlash = fileExtension == "swf"

    Box(
        modifier = Modifier
            .padding(4.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B263B))
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clickable(onClick = onImageClick) // Просто вызываем onImageClick
        ) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(previewUrl ?: fileUrl)
                    .size(Size(150, 150))
                    .scale(Scale.FIT)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                placeholder = rememberAsyncImagePainter(Color(0xFF0D1B2A)),
                error = rememberAsyncImagePainter(Color(0xFF2A2A2A))
            )
            val state = painter.state

            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp),
                        color = Color(0xFF778DA9)
                    )
                }
                is AsyncImagePainter.State.Success -> {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Failed to load",
                            color = Color(0xFFE63946),
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Video",
                        color = Color.White,
                        style = MaterialTheme.typography.caption
                    )
                }
            } else if (isFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Flash",
                        color = Color.White,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        IconButton(
            onClick = {
                isFavorited = !isFavorited
                triggerAnimation = !triggerAnimation
                onFavorite()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
        ) {
            Icon(
                imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                tint = color,
                modifier = Modifier.scale(scale)
            )
        }
    }
}