package com.yourcompany.booru.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourcompany.booru.ui.theme.BooruViewerTheme

@Composable
fun SearchPanel(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    searchHistory: MutableList<String>,
    favoriteTags: MutableList<String>,
    onFavoriteTagToggle: () -> Unit
) {
    BooruViewerTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colors.background)
                    .border(
                        1.dp,
                        MaterialTheme.colors.primary.copy(alpha = 0.3f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Закрыть",
                    tint = MaterialTheme.colors.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onClose() }
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Поиск тегов", style = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))) },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Transparent),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onSurface
                    ),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colors.primary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onSearch() }
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                            exit = scaleOut(tween(200)) + fadeOut(tween(200))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить",
                                tint = MaterialTheme.colors.onSurface,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onClear() }
                                    .padding(2.dp)
                            )
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "История",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface
                )
                if (searchHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { searchHistory.clear() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Очистить",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchHistory.take(10)) { tag ->
                    TagChip(
                        tag = tag,
                        onClick = {
                            onSearchChange(tag)
                            onSearch()
                        },
                        isFavorite = favoriteTags.contains(tag),
                        onFavoriteToggle = {
                            if (favoriteTags.contains(tag)) favoriteTags.remove(tag) else favoriteTags.add(tag)
                            onFavoriteTagToggle()
                        }
                    )
                }
            }

            Text(
                text = "Избранные теги",
                style = MaterialTheme.typography.subtitle2,
                color = MaterialTheme.colors.onSurface
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favoriteTags) { tag ->
                    TagChip(
                        tag = tag,
                        onClick = {
                            onSearchChange(tag)
                            onSearch()
                        },
                        isFavorite = true,
                        onFavoriteToggle = {
                            favoriteTags.remove(tag)
                            onFavoriteTagToggle()
                        }
                    )
                }
            }
        }
    }
}