package com.yourcompany.booru.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yourcompany.booru.ui.theme.BooruViewerTheme
import com.yourcompany.booru.viewmodel.MainViewModel

@ExperimentalFoundationApi
@Composable
fun DrawerContent(
    booruOptions: List<String>,
    selectedBooru: String,
    onBooruSelected: (String) -> Unit,
    onNewBooruAdded: (String) -> Unit,
    onBooruRemoved: (String) -> Unit,
    onBooruRenamed: (String, String) -> Unit,
    navController: NavController,
    viewModel: MainViewModel
) {
    BooruViewerTheme {
        var showAddBooruDialog by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf<String?>(null) }
        var showContextMenu by remember { mutableStateOf<String?>(null) }
        var homeBooru by remember { mutableStateOf(selectedBooru) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Lumen",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            item {
                DrawerItem(
                    icon = Icons.Default.RssFeed,
                    label = "Лента",
                    onClick = {
                        viewModel.page.value = 0
                        viewModel.shouldReloadFeedPosts.value = true
                        viewModel.loadFeedPosts(append = false)
                        navController.navigate("feed")
                    },
                    isHighlighted = true
                )
                DrawerItem(
                    icon = Icons.Default.Favorite,
                    label = "Избранное",
                    onClick = { navController.navigate("favorites") }
                )
                DrawerItem(
                    icon = Icons.Default.Block,
                    label = "Заблокированные теги",
                    onClick = { navController.navigate("blocked_tags") }
                )
                DrawerItem(
                    icon = Icons.Default.VideoLibrary,
                    label = "Shorties",
                    onClick = {
                        viewModel.page.value = 0
                        viewModel.shouldReloadPosts.value = true
                        viewModel.loadVideos()
                        navController.navigate("shorties")
                    }
                )
                Divider(
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                Text(
                    text = "Booru Галереи",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(booruOptions.filter { it != "shorties" }) { booru ->
                BooruItem(
                    booru = booru,
                    isSelected = booru == selectedBooru,
                    isHome = booru == homeBooru,
                    onClick = { onBooruSelected(booru) },
                    onLongClick = { showContextMenu = booru },
                    viewModel = viewModel
                )
                DropdownMenu(
                    expanded = showContextMenu == booru,
                    onDismissRequest = { showContextMenu = null },
                    modifier = Modifier.background(MaterialTheme.colors.surface)
                ) {
                    DropdownMenuItem(
                        onClick = {
                            homeBooru = booru
                            showContextMenu = null
                        }
                    ) {
                        Text(
                            text = "Сделать домашней",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                    DropdownMenuItem(
                        onClick = {
                            showRenameDialog = booru
                            showContextMenu = null
                        }
                    ) {
                        Text(
                            text = "Переименовать",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                    if (booruOptions.size > 1) {
                        DropdownMenuItem(
                            onClick = {
                                onBooruRemoved(booru)
                                showContextMenu = null
                            }
                        ) {
                            Text(
                                text = "Удалить",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showAddBooruDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Booru",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Добавить Booru",
                        style = MaterialTheme.typography.button
                    )
                }
            }
        }

        if (showAddBooruDialog) {
            AddBooruDialog(
                onDismiss = { showAddBooruDialog = false },
                onAdd = { newBooru ->
                    if (newBooru.isNotBlank() && !booruOptions.contains(newBooru)) {
                        onNewBooruAdded(newBooru)
                    }
                    showAddBooruDialog = false
                }
            )
        }

        if (showRenameDialog != null) {
            RenameBooruDialog(
                currentName = viewModel.getBooruDisplayName(showRenameDialog!!),
                onDismiss = { showRenameDialog = null },
                onRename = { newName ->
                    if (newName.isNotBlank()) {
                        onBooruRenamed(showRenameDialog!!, newName)
                    }
                    showRenameDialog = null
                }
            )
        }
    }
}

@Composable
fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isHighlighted) MaterialTheme.colors.primary.copy(alpha = 0.2f)
                else MaterialTheme.colors.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isHighlighted) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.body1.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isHighlighted) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
        )
    }
}

@ExperimentalFoundationApi
@Composable
fun BooruItem(
    booru: String,
    isSelected: Boolean,
    isHome: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewModel: MainViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.2f)
                else MaterialTheme.colors.surface
            )
            .clickable(onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isHome) Icons.Default.Home else Icons.Default.Image,
            contentDescription = booru,
            tint = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = viewModel.getBooruDisplayName(booru),
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AddBooruDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var booruUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colors.surface)
            .border(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.3f), MaterialTheme.shapes.large),
        title = {
            Text(
                text = "Добавить новую Booru",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface
            )
        },
        text = {
            TextField(
                value = booruUrl,
                onValueChange = { booruUrl = it },
                label = { Text("Enter Booru URL (e.g., https://danbooru.donmai.us/)") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onSurface,
                    backgroundColor = MaterialTheme.colors.background,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colors.primary
                ),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(booruUrl.trim()) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colors.primary
                )
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun RenameBooruDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать галерею") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Новое название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(newName) },
                enabled = newName.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}