package com.yourcompany.booru.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yourcompany.booru.viewmodel.MainViewModel
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close

@Composable
fun BlockedTagsScreen(
    navController: NavController,
    viewModel: MainViewModel // Принимаем MainViewModel вместо blockedTags
) {
    Log.d("BlockedTagsScreen", "Entering BlockedTagsScreen with blockedTags: ${viewModel.blockedTags}")

    // Локальное состояние для списка тегов
    val localBlockedTags = remember { mutableStateListOf<String>() }
    var isInitialized by remember { mutableStateOf(false) }

    // Синхронизируем localBlockedTags с viewModel.blockedTags при первом входе
    LaunchedEffect(Unit) {
        if (!isInitialized) {
            localBlockedTags.clear()
            localBlockedTags.addAll(viewModel.blockedTags.distinct()) // Удаляем дубликаты
            isInitialized = true
        }
    }

    Scaffold(
        backgroundColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                title = { Text("Blocked Tags", color = Color(0xFFE0E0E0)) },
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                navigationIcon = {
                    AnimatedIconButton(
                        onClick = {
                            Log.d("BlockedTagsScreen", "Back pressed, saving blockedTags: $localBlockedTags")
                            // Синхронизируем viewModel.blockedTags перед выходом
                            viewModel.blockedTags.clear()
                            viewModel.blockedTags.addAll(localBlockedTags.distinct())
                            viewModel.saveBlockedTags() // Сохраняем изменения через ViewModel
                            navController.popBackStack()
                        },
                        icon = androidx.compose.material.icons.Icons.Default.Close,
                        description = "Back",
                        color = Color(0xFF415A77)
                    )
                }
            )
        }
    ) { paddingValues ->
        if (localBlockedTags.isEmpty()) {
            Text(
                text = "No blocked tags yet",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.h6
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                itemsIndexed(localBlockedTags, key = { index, tag -> "$index-$tag" }) { index, tag ->
                    AnimatedVisibility(
                        visible = localBlockedTags.contains(tag),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B263B))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tag,
                                color = Color(0xFFE0E0E0),
                                style = MaterialTheme.typography.body1
                            )
                            IconButton(
                                onClick = {
                                    Log.d("BlockedTagsScreen", "Clicked remove button for tag: $tag at index: $index")
                                    Log.d("BlockedTagsScreen", "Before removal: $localBlockedTags")
                                    localBlockedTags.removeAt(index)
                                    Log.d("BlockedTagsScreen", "After removal: $localBlockedTags")
                                    // Обновляем viewModel.blockedTags сразу после удаления
                                    viewModel.blockedTags.clear()
                                    viewModel.blockedTags.addAll(localBlockedTags.distinct())
                                    viewModel.saveBlockedTags() // Сохраняем изменения через ViewModel
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Red.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    tint = Color(0xFF778DA9),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}