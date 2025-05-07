package com.yourcompany.booru.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.yourcompany.booru.data.AppDatabase
import com.yourcompany.booru.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun FeedScreen(
    navController: NavController,
    viewModel: MainViewModel,
    database: AppDatabase
) {
    val scope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoading.value)
    var isSearchOpen by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.shouldReloadFeedPosts.value) {
            viewModel.loadFeedPosts()
        }
    }

    LaunchedEffect(lazyGridState) {
        snapshotFlow { lazyGridState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val lastVisibleItem = visibleItems.lastOrNull()?.index ?: 0
                if (lastVisibleItem >= viewModel.feedPosts.value.size - 5 && !viewModel.isLoading.value && viewModel.feedPosts.value.isNotEmpty()) {
                    viewModel.loadFeedPosts(append = true)
                }
            }
    }

    LaunchedEffect(viewModel.errorMessage.value) {
        viewModel.errorMessage.value?.let { message ->
            scope.launch {
                val snackbarHostState = SnackbarHostState()
                snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
                viewModel.errorMessage.value = null
            }
        }
    }

    LaunchedEffect(viewModel.blockedTags) {
        viewModel.saveBlockedTags()
    }

    LaunchedEffect(viewModel.favoriteTags) {
        viewModel.saveFavoriteTags()
    }

    LaunchedEffect(viewModel.searchHistory) {
        viewModel.saveSearchHistory()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Лента",
                        style = MaterialTheme.typography.h6.copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colors.primary,
                                    MaterialTheme.colors.secondary,
                                    MaterialTheme.colors.primary
                                ),
                                start = Offset(-100f, 0f),
                                end = Offset(100f, 0f)
                            )
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigate("main") },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Main",
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchOpen = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                },
                backgroundColor = MaterialTheme.colors.background,
                elevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colors.primary.copy(alpha = 0.9f),
                                MaterialTheme.colors.background
                            )
                        )
                    )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    SearchPanel(
                        searchQuery = viewModel.feedSearchQuery.value,
                        onSearchChange = { newQuery -> viewModel.feedSearchQuery.value = newQuery },
                        onSearch = {
                            if (viewModel.feedSearchQuery.value.isNotBlank()) {
                                viewModel.page.value = 0
                                if (!viewModel.searchHistory.contains(viewModel.feedSearchQuery.value)) {
                                    viewModel.searchHistory.add(0, viewModel.feedSearchQuery.value)
                                    if (viewModel.searchHistory.size > 10) viewModel.searchHistory.removeAt(10)
                                }
                                viewModel.shouldReloadFeedPosts.value = true
                                viewModel.loadFeedPosts()
                                isSearchOpen = false
                            }
                        },
                        onClose = { isSearchOpen = false },
                        onClear = {
                            viewModel.feedSearchQuery.value = ""
                            viewModel.page.value = 0
                            viewModel.shouldReloadFeedPosts.value = true
                            viewModel.loadFeedPosts()
                            isSearchOpen = false
                        },
                        searchHistory = viewModel.searchHistory,
                        favoriteTags = viewModel.favoriteTags,
                        onFavoriteTagToggle = { viewModel.saveFavoriteTags() }
                    )
                }

                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = {
                        viewModel.page.value = 0
                        viewModel.shouldReloadFeedPosts.value = true
                        viewModel.loadFeedPosts()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    if (viewModel.isLoading.value && viewModel.feedPosts.value.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.background.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(8.dp, MaterialTheme.shapes.medium),
                                color = MaterialTheme.colors.primary,
                                strokeWidth = 6.dp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            state = lazyGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.background),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = viewModel.feedPosts.value,
                                key = { index, post -> "$index-${post.id}-${post.sourceType ?: "unknown"}" }
                            ) { _, post ->
                                PostItem(
                                    post = post,
                                    onFavorite = {
                                        viewModel.toggleFavorite(post.id.toLong(), !post.isFavorited)
                                    },
                                    onBlockTag = { tag ->
                                        viewModel.blockedTags.add(tag)
                                        viewModel.shouldReloadFeedPosts.value = true
                                        viewModel.loadFeedPosts()
                                    },
                                    onSearchTag = { tag ->
                                        viewModel.feedSearchQuery.value = tag
                                        viewModel.page.value = 0
                                        if (!viewModel.searchHistory.contains(tag)) {
                                            viewModel.searchHistory.add(0, tag)
                                            if (viewModel.searchHistory.size > 10) viewModel.searchHistory.removeAt(10)
                                        }
                                        viewModel.shouldReloadFeedPosts.value = true
                                        viewModel.loadFeedPosts()
                                    },
                                    onAddToSearch = { tag ->
                                        viewModel.feedSearchQuery.value += " $tag"
                                        if (!viewModel.searchHistory.contains(tag)) {
                                            viewModel.searchHistory.add(0, tag)
                                            if (viewModel.searchHistory.size > 10) viewModel.searchHistory.removeAt(10)
                                        }
                                        viewModel.shouldReloadFeedPosts.value = true
                                        viewModel.loadFeedPosts(append = true)
                                    },
                                    onImageClick = {
                                        val route = "fullScreenImageScreen/${post.id}?index=${viewModel.feedPosts.value.indexOf(post)}&booru=${URLEncoder.encode(post.sourceType ?: "", "UTF-8")}"
                                        navController.navigate(route)
                                    },
                                    currentBooru = post.sourceType ?: "",
                                    posts = viewModel.feedPosts.value
                                )
                            }
                            if (viewModel.isLoading.value && viewModel.feedPosts.value.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally),
                                        color = MaterialTheme.colors.primary,
                                        strokeWidth = 4.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showSortDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(44.dp)
                    .shadow(6.dp, MaterialTheme.shapes.medium),
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort and Filter",
                    modifier = Modifier.size(24.dp)
                )
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        lazyGridState.scrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .size(48.dp)
                    .shadow(6.dp, CircleShape),
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Scroll to top",
                    modifier = Modifier.size(24.dp)
                )
            }

            if (showSortDialog) {
                AlertDialog(
                    onDismissRequest = { showSortDialog = false },
                    title = {
                        Text(
                            text = "Sort & Filter",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.onSurface
                        )
                    },
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = viewModel.hideDuplicates.value,
                                    onCheckedChange = {
                                        viewModel.hideDuplicates.value = it
                                        viewModel.feedPosts.value = viewModel.applyFiltersAndSort(viewModel.feedPosts.value)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colors.primary,
                                        uncheckedColor = MaterialTheme.colors.secondary
                                    )
                                )
                                Text(
                                    text = "Hide Duplicates",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = viewModel.minWidth.value != null,
                                    onCheckedChange = {
                                        viewModel.minWidth.value = if (it) 1920 else null
                                        viewModel.feedPosts.value = viewModel.applyFiltersAndSort(viewModel.feedPosts.value)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colors.primary,
                                        uncheckedColor = MaterialTheme.colors.secondary
                                    )
                                )
                                Text(
                                    text = "HD Only (1920px+)",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            Text(
                                text = "Sort By:",
                                style = MaterialTheme.typography.subtitle1,
                                color = MaterialTheme.colors.onSurface
                            )
                            Row {
                                RadioButton(
                                    selected = viewModel.sortOrder.value == MainViewModel.SortOrder.NEWEST,
                                    onClick = {
                                        viewModel.sortOrder.value = MainViewModel.SortOrder.NEWEST
                                        viewModel.feedPosts.value = viewModel.applyFiltersAndSort(viewModel.feedPosts.value)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colors.primary,
                                        unselectedColor = MaterialTheme.colors.secondary
                                    )
                                )
                                Text(
                                    text = "Newest",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            Row {
                                RadioButton(
                                    selected = viewModel.sortOrder.value == MainViewModel.SortOrder.OLDEST,
                                    onClick = {
                                        viewModel.sortOrder.value = MainViewModel.SortOrder.OLDEST
                                        viewModel.feedPosts.value = viewModel.applyFiltersAndSort(viewModel.feedPosts.value)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colors.primary,
                                        unselectedColor = MaterialTheme.colors.secondary
                                    )
                                )
                                Text(
                                    text = "Oldest",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            Row {
                                RadioButton(
                                    selected = viewModel.sortOrder.value == MainViewModel.SortOrder.POPULAR,
                                    onClick = {
                                        viewModel.sortOrder.value = MainViewModel.SortOrder.POPULAR
                                        viewModel.feedPosts.value = viewModel.applyFiltersAndSort(viewModel.feedPosts.value)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colors.primary,
                                        unselectedColor = MaterialTheme.colors.secondary
                                    )
                                )
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showSortDialog = false },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colors.primary
                            )
                        ) {
                            Text("OK", style = MaterialTheme.typography.button)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showSortDialog = false },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colors.secondary
                            )
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.button)
                        }
                    },
                    backgroundColor = MaterialTheme.colors.surface,
                    shape = MaterialTheme.shapes.large
                )
            }
        }
    }
}