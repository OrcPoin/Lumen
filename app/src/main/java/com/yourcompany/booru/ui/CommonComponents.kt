package com.yourcompany.booru.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale

@Composable
fun AnimatedIconButton(
    onClick: () -> Unit, // Убрали @Composable
    icon: ImageVector?,
    description: String,
    color: Color,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, animationSpec = tween(150))

    Box(
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .background(color, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = description,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color(0xFFE0E0E0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AnimatedFloatingButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, animationSpec = tween(150))

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .background(Color(0xFF778DA9), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color(0xFFE0E0E0),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, animationSpec = tween(150))
    val backgroundColor by animateColorAsState(
        if (isPressed) color.copy(alpha = 0.8f) else color,
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFE0E0E0),
            style = MaterialTheme.typography.button
        )
    }
}

@Composable
fun TagChip(
    tag: String,
    onClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, animationSpec = tween(150))

    Row(
        modifier = Modifier
            .padding(4.dp)
            .scale(scale)
            .background(Color(0xFF1B263B), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag,
            color = Color(0xFF778DA9),
            style = MaterialTheme.typography.body2
        )
        Spacer(modifier = Modifier.width(4.dp))
        AnimatedIconButton(
            onClick = onFavoriteToggle, // Теперь корректно передаём обычную лямбда
            icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            description = "Favorite",
            color = if (isFavorite) Color(0xFF778DA9) else Color(0xFF415A77),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun DrawerItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color(0xFF778DA9),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.subtitle1,
            color = Color(0xFFE0E0E0)
        )
    }
}