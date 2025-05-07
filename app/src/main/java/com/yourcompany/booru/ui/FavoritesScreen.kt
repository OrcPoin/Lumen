package com.yourcompany.booru.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yourcompany.booru.data.AppDatabase
import com.yourcompany.booru.data.toImagePost
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun FavoritesScreen(
    navController: NavController,
    database: AppDatabase
) {
    val favoritePostsState = database.imagePostDao().getFavoritePosts().collectAsState(initial = emptyList())
    val posts = favoritePostsState.value.map { it.toImagePost() }

    Scaffold(
        backgroundColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                title = { Text("Favorites", color = Color(0xFFE0E0E0)) },
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                navigationIcon = {
                    AnimatedIconButton(
                        onClick = { navController.popBackStack() },
                        icon = Icons.Default.Close,
                        description = "Back",
                        color = Color(0xFF415A77)
                    )
                }
            )
        }
    ) { paddingValues ->
        if (posts.isEmpty()) {
            Text(
                text = "No favorites yet",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.h6
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(posts) { post ->
                    val scope = rememberCoroutineScope()
                    PostItem(
                        post = post,
                        onFavorite = {
                            scope.launch {
                                database.imagePostDao().updateFavoriteStatus(
                                    postId = post.id,
                                    isFavorited = !post.isFavorited
                                )
                            }
                        },
                        onBlockTag = {},
                        onSearchTag = {},
                        onAddToSearch = {},
                        onImageClick = {
                            val route = "fullScreenImageScreen/${post.id}?index=${posts.indexOf(post)}&booru=${URLEncoder.encode(post.sourceType ?: "", "UTF-8")}"
                            navController.navigate(route)
                        },
                        currentBooru = post.sourceType ?: "",
                        posts = posts
                    )
                }
            }
        }
    }
}