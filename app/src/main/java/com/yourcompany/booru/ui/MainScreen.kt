package com.yourcompany.booru.ui

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourcompany.booru.api.BooruApiClient
import com.yourcompany.booru.data.AppDatabase
import com.yourcompany.booru.data.ImagePost
import com.yourcompany.booru.viewmodel.MainViewModel
import com.yourcompany.booru.viewmodel.MainViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current)),
    database: AppDatabase
) {
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()
    val drawerState = scaffoldState.drawerState
    var isSearchOpen by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var isAutoSwipeEnabled by remember { mutableStateOf(false) }
    var autoSwipeInterval by remember { mutableStateOf(5000L) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // Load booru options from SharedPreferences
    val sharedPreferences = LocalContext.current.getSharedPreferences("BooruPrefs", android.content.Context.MODE_PRIVATE)
    val booruOptionsJson = sharedPreferences.getString("booruOptions", null)
    var booruOptions by remember { mutableStateOf(
        if (booruOptionsJson != null) {
            Gson().fromJson(booruOptionsJson, object : TypeToken<List<String>>() {}.type)
        } else {
            mutableListOf(
                "https://rule34.xxx/",
                "https://danbooru.donmai.us/",
                "https://gelbooru.com/",
                "https://rule34.booru.org/",
                "https://bgb.booru.org/",
                "https://www.pornhub.com/albums/",
                "shorties",
                "https://www.reddit.com/r/nsfw/",
                "https://www.reddit.com/r/realgirls/",
                "https://www.reddit.com/r/gonewild/"
            ).also {
                with(sharedPreferences.edit()) {
                    putString("booruOptions", Gson().toJson(it))
                    apply()
                }
            }
        }
    ) }

    fun handleBooruRename(booru: String, newName: String) {
        viewModel.renameBooru(booru, newName)
    }

    var lastPosition by remember { mutableStateOf<Int?>(null) }
    var hasMorePosts by remember { mutableStateOf(true) } // Флаг для отслеживания конца списка

    // Получение позиции скролла при возвращении
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle?.get<Int>("lastPosition")?.let { position ->
            lastPosition = position
            navController.currentBackStackEntry?.savedStateHandle?.remove<Int>("lastPosition")
        }
    }

    val lazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.scrollPosition,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )

    // Прокрутка к сохранённой позиции
    LaunchedEffect(lastPosition) {
        lastPosition?.let { position ->
            if (position in 0 until viewModel.posts.value.size) {
                lazyGridState.scrollToItem(position)
                lastPosition = null
            }
        }
    }

    // Показ ошибок через Snackbar
    LaunchedEffect(viewModel.errorMessage.value) {
        viewModel.errorMessage.value?.let { message ->
            scaffoldState.snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.errorMessage.value = null
        }
    }

    // Сохранение настроек
    LaunchedEffect(viewModel.blockedTags) { viewModel.saveBlockedTags() }
    LaunchedEffect(viewModel.searchHistory) { viewModel.saveSearchHistory() }
    LaunchedEffect(viewModel.favoriteTags) { viewModel.saveFavoriteTags() }

    // Подгрузка новых постов при скролле
    LaunchedEffect(lazyGridState) {
        snapshotFlow { lazyGridState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                
                // Проверяем, достигли ли мы конца списка
                if (totalItems > 0 && lastVisibleItem >= totalItems - 3 && !viewModel.isLoading.value) {
                    Log.d("MainScreen", "Triggering load more posts: lastVisibleItem=$lastVisibleItem, totalItems=$totalItems")
                    val previousSize = viewModel.posts.value.size
                    
                    // Загружаем новые посты
                    viewModel.loadPosts(append = true)
                    
                    // Проверяем, добавились ли новые посты
                    if (viewModel.posts.value.size == previousSize) {
                        // Если не добавились новые посты, увеличиваем страницу и пробуем еще раз
                        viewModel.page.value++
                        viewModel.loadPosts(append = true)
                    }
                }
            }
    }

    // Начальная загрузка постов
    LaunchedEffect(viewModel.shouldReloadPosts.value) {
        if (viewModel.shouldReloadPosts.value) {
            hasMorePosts = true // Сбрасываем флаг при перезагрузке
            viewModel.loadPosts()
            viewModel.shouldReloadPosts.value = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading.value && viewModel.posts.value.isNotEmpty(),
        onRefresh = {
            viewModel.page.value = 0
            viewModel.shouldReloadPosts.value = true
            hasMorePosts = true
            viewModel.loadPosts()
        }
    )

    Scaffold(
        scaffoldState = scaffoldState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        snackbarHost = {
            SnackbarHost(hostState = scaffoldState.snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    backgroundColor = if (viewModel.isOfflineMode.value) Color(0xFFFFA500) else MaterialTheme.colors.error,
                    contentColor = MaterialTheme.colors.onError
                ) {
                    Text(
                        text = data.message,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.body1
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    DrawerContent(
                        booruOptions = booruOptions,
                        selectedBooru = viewModel.selectedBooru.value,
                        onBooruSelected = { booru ->
                            viewModel.selectedBooru.value = booru
                            BooruApiClient.setBaseUrl(booru)
                            scope.launch { drawerState.close() }
                        },
                        onNewBooruAdded = { newBooru ->
                            if (newBooru.isNotBlank() && !booruOptions.contains(newBooru)) {
                                booruOptions.add(newBooru)
                                with(sharedPreferences.edit()) {
                                    putString("booruOptions", Gson().toJson(booruOptions))
                                    apply()
                                }
                            }
                        },
                        onBooruRemoved = { booru ->
                            booruOptions.remove(booru)
                            with(sharedPreferences.edit()) {
                                putString("booruOptions", Gson().toJson(booruOptions))
                                apply()
                            }
                            if (viewModel.selectedBooru.value == booru) {
                                viewModel.selectedBooru.value = booruOptions.firstOrNull() ?: "https://rule34.xxx/"
                                BooruApiClient.setBaseUrl(viewModel.selectedBooru.value)
                            }
                        },
                        onBooruRenamed = { booru, newName ->
                            handleBooruRename(booru, newName)
                        },
                        navController = navController,
                        viewModel = viewModel
                    )
                },
                drawerBackgroundColor = MaterialTheme.colors.surface,
                drawerShape = MaterialTheme.shapes.large,
                modifier = Modifier.background(MaterialTheme.colors.background)
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
                            searchQuery = viewModel.searchQuery.value,
                            onSearchChange = { viewModel.searchQuery.value = it },
                            onSearch = {
                                if (viewModel.searchQuery.value.isNotBlank()) {
                                    viewModel.page.value = 0
                                    if (!viewModel.searchHistory.contains(viewModel.searchQuery.value)) {
                                        viewModel.searchHistory.add(0, viewModel.searchQuery.value)
                                        if (viewModel.searchHistory.size > 10) viewModel.searchHistory.removeAt(10)
                                    }
                                    viewModel.shouldReloadPosts.value = true
                                    viewModel.loadPosts()
                                    isSearchOpen = false
                                }
                            },
                            onClose = { isSearchOpen = false },
                            onClear = {
                                viewModel.searchQuery.value = ""
                                viewModel.page.value = 0
                                viewModel.shouldReloadPosts.value = true
                                viewModel.loadPosts()
                                isSearchOpen = false
                            },
                            searchHistory = viewModel.searchHistory,
                            favoriteTags = viewModel.favoriteTags,
                            onFavoriteTagToggle = { viewModel.saveFavoriteTags() }
                        )
                    }
                    if (!isSearchOpen) {
                        TopBar(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = { isSearchOpen = true }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState)
                    ) {
                        when {
                            viewModel.isLoading.value && viewModel.posts.value.isEmpty() -> {
                                LoadingState()
                            }
                            viewModel.posts.value.isEmpty() -> {
                                EmptyState(viewModel.isOfflineMode.value)
                            }
                            else -> {
                                PostGrid(
                                    posts = viewModel.posts.value,
                                    lazyGridState = lazyGridState,
                                    viewModel = viewModel,
                                    onImageClick = { route ->
                                        viewModel.scrollPosition = lazyGridState.firstVisibleItemIndex
                                        viewModel.scrollOffset = lazyGridState.firstVisibleItemScrollOffset
                                        navController.navigate(route)
                                    },
                                    onFavorite = { postId, isFavorited ->
                                        viewModel.toggleFavorite(postId, isFavorited)
                                    },
                                    onBlockTag = { tag ->
                                        viewModel.blockedTags.add(tag)
                                        viewModel.shouldReloadPosts.value = true
                                        viewModel.loadPosts()
                                    },
                                    onSearchTag = { tag ->
                                        viewModel.searchQuery.value = tag
                                        viewModel.page.value = 0
                                        updateSearchHistory(viewModel, tag)
                                        viewModel.shouldReloadPosts.value = true
                                        viewModel.loadPosts()
                                    },
                                    onAddToSearch = { tag ->
                                        viewModel.searchQuery.value += " $tag"
                                        updateSearchHistory(viewModel, tag)
                                        viewModel.shouldReloadPosts.value = true
                                        viewModel.loadPosts(append = true)
                                    }
                                )
                            }
                        }

                        PullRefreshIndicator(
                            refreshing = viewModel.isLoading.value && viewModel.posts.value.isNotEmpty(),
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            backgroundColor = MaterialTheme.colors.surface,
                            contentColor = MaterialTheme.colors.primary
                        )

                        // FAB для сортировки
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
                                contentDescription = "Фильтры",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // FAB для скролла вверх
                        this@Column.AnimatedVisibility(
                            visible = lazyGridState.firstVisibleItemIndex > 10,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .navigationBarsPadding()
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        lazyGridState.animateScrollToItem(0)
                                        viewModel.scrollPosition = 0
                                        viewModel.scrollOffset = 0
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(6.dp, MaterialTheme.shapes.medium),
                                backgroundColor = MaterialTheme.colors.primary,
                                contentColor = MaterialTheme.colors.onPrimary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Scroll to top",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSortDialog) {
            SortFilterDialog(
                viewModel = viewModel,
                onDismiss = { showSortDialog = false }
            )
        }
    }
}

@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colors.surface)
                .shadow(4.dp, MaterialTheme.shapes.medium)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colors.primary
            )
        }

        var isTitleVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(5000)
            isTitleVisible = false
        }

        Box {
            this@Row.AnimatedVisibility(
                visible = isTitleVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Люмен",
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
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }

        if (!isTitleVisible) {
            Spacer(modifier = Modifier.weight(1f))
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colors.surface)
                .shadow(4.dp, MaterialTheme.shapes.medium)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
fun LoadingState() {
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
}

@Composable
fun EmptyState(isOffline: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Default.WifiOff else Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isOffline) "Offline Mode" else "No posts found",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            if (isOffline) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to the internet to load posts",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PostGrid(
    posts: List<ImagePost>,
    lazyGridState: LazyGridState,
    viewModel: MainViewModel,
    onImageClick: (String) -> Unit,
    onFavorite: (Long, Boolean) -> Unit,
    onBlockTag: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onAddToSearch: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        state = lazyGridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = posts,
            key = { post: ImagePost -> "${post.id}-${post.sourceType}" }
        ) { post: ImagePost ->
            PostItem(
                post = post,
                onFavorite = { onFavorite(post.id, !post.isFavorited) }, // Removed toLong() since post.id is already Long
                onBlockTag = onBlockTag,
                onSearchTag = onSearchTag,
                onAddToSearch = onAddToSearch,
                onImageClick = {
                    val route = "fullScreenImageScreen/${post.id.toString()}?index=${posts.indexOf(post)}&booru=${java.net.URLEncoder.encode(post.sourceType, "UTF-8")}"
                    onImageClick(route)
                },
                currentBooru = viewModel.selectedBooru.value,
                posts = posts
            )
        }
        if (viewModel.isLoading.value) {
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

@Composable
fun SortFilterDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Фильтры",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface
            )
        },
        text = {
            Column {
                FilterOption(
                    text = "Скрыть повторки",
                    checked = viewModel.hideDuplicates.value,
                    onCheckedChange = {
                        viewModel.hideDuplicates.value = it
                        viewModel.posts.value = viewModel.applyFiltersAndSort(viewModel.posts.value)
                    }
                )
                FilterOption(
                    text = "Только HD",
                    checked = viewModel.minWidth.value != null,
                    onCheckedChange = {
                        viewModel.minWidth.value = if (it) 1920 else null
                        viewModel.posts.value = viewModel.applyFiltersAndSort(viewModel.posts.value)
                    }
                )
                Text(
                    text = "Сортировать по:",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                SortOption(
                    text = "Новые",
                    selected = viewModel.sortOrder.value == MainViewModel.SortOrder.NEWEST,
                    onClick = {
                        viewModel.sortOrder.value = MainViewModel.SortOrder.NEWEST
                        viewModel.posts.value = viewModel.applyFiltersAndSort(viewModel.posts.value)
                    }
                )
                SortOption(
                    text = "Старые",
                    selected = viewModel.sortOrder.value == MainViewModel.SortOrder.OLDEST,
                    onClick = {
                        viewModel.sortOrder.value = MainViewModel.SortOrder.OLDEST
                        viewModel.posts.value = viewModel.applyFiltersAndSort(viewModel.posts.value)
                    }
                )
                SortOption(
                    text = "Популярность",
                    selected = viewModel.sortOrder.value == MainViewModel.SortOrder.POPULAR,
                    onClick = {
                        viewModel.sortOrder.value = MainViewModel.SortOrder.POPULAR
                        viewModel.posts.value = viewModel.applyFiltersAndSort(viewModel.posts.value)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.primary)
            ) {
                Text("OK", style = MaterialTheme.typography.button)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.secondary)
            ) {
                Text("Cancel", style = MaterialTheme.typography.button)
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
fun FilterOption(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colors.primary,
                uncheckedColor = MaterialTheme.colors.secondary
            )
        )
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface
        )
    }
}

@Composable
fun SortOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colors.primary,
                unselectedColor = MaterialTheme.colors.secondary
            )
        )
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface
        )
    }
}

fun loadBooruOptionsForMainScreen(sharedPreferences: SharedPreferences): MutableList<String> {
    val booruOptionsJson = sharedPreferences.getString("booruOptions", null)
    return if (booruOptionsJson != null) {
        val type = object : TypeToken<MutableList<String>>() {}.type
        Gson().fromJson(booruOptionsJson, type)
    } else {
        mutableListOf(
            "https://rule34.xxx/" // Updated to match MainViewModel.booruOptions
        )
    }
}

fun saveBooruOptions(sharedPreferences: SharedPreferences, booruOptions: List<String>) {
    with(sharedPreferences.edit()) {
        putString("booruOptions", Gson().toJson(booruOptions))
        apply()
    }
}

fun updateSearchHistory(viewModel: MainViewModel, tag: String) {
    if (!viewModel.searchHistory.contains(tag)) {
        viewModel.searchHistory.add(0, tag)
        if (viewModel.searchHistory.size > 10) viewModel.searchHistory.removeAt(10)
    }
}