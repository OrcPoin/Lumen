package com.yourcompany.booru.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.yourcompany.booru.data.AppDatabase
import com.yourcompany.booru.data.ImagePost
import com.yourcompany.booru.data.toEntity
import com.yourcompany.booru.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FullScreenImageScreen(
    post: ImagePost,
    navController: NavController,
    posts: List<ImagePost> = emptyList(),
    initialIndex: Int = 0,
    viewModel: MainViewModel,
    database: AppDatabase
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var isAutoSwipeEnabled by remember { mutableStateOf(false) }
    var autoSwipeInterval by remember { mutableStateOf(5000L) } // По умолчанию 5 секунд
    var isUserInteracting by remember { mutableStateOf(false) } // Отслеживаем взаимодействие пользователя
    val arguments = navController.currentBackStackEntry?.arguments
    val currentBooru = arguments?.getString("booru")?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    } ?: "https://rule34.xxx/"

    // Получаем информацию о статус-баре и навигационном баре через WindowInsets
    val density = LocalDensity.current
    val view = LocalView.current
    val windowInsets = WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
    val statusBarHeight = with(density) { windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top.toDp() }
    val navBarHeight = with(density) { windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom.toDp() }

    // Динамический список постов для бесконечного листания
    val effectivePosts: SnapshotStateList<ImagePost> = remember { mutableStateListOf<ImagePost>() }

    // Инициализация списка постов
    LaunchedEffect(posts, viewModel.posts.value) {
        effectivePosts.clear()
        if (posts.isNotEmpty()) {
            effectivePosts.addAll(posts)
        } else {
            val viewModelPosts = viewModel.posts.value
            if (viewModelPosts.isNotEmpty()) {
                effectivePosts.addAll(viewModelPosts)
            } else {
                effectivePosts.add(post)
            }
        }
    }

    // Используем initialIndex напрямую, если posts не пуст, иначе ищем по id
    val safeInitialIndex by remember(initialIndex, posts, effectivePosts, post) {
        derivedStateOf {
            if (posts.isNotEmpty() && initialIndex >= 0 && initialIndex < effectivePosts.size) {
                initialIndex
            } else {
                val index = effectivePosts.indexOfFirst { it.id == post.id && it.sourceType == post.sourceType }
                if (index != -1) index else 0
            }
        }
    }

    // Создаём pagerState
    val pagerState = rememberPagerState(
        pageCount = { effectivePosts.size },
        initialPage = safeInitialIndex
    )

    // Прокручиваем к правильному индексу после обновления списка
    LaunchedEffect(safeInitialIndex, effectivePosts) {
        if (effectivePosts.isNotEmpty() && safeInitialIndex in 0 until effectivePosts.size) {
            pagerState.scrollToPage(safeInitialIndex)
            Log.d("FullScreenImage", "Scrolled to safe initial index: $safeInitialIndex, Post ID: ${effectivePosts[safeInitialIndex].id}")
        }
    }

    // Автопрокрутка с паузой при взаимодействии пользователя
    LaunchedEffect(isAutoSwipeEnabled, autoSwipeInterval, isUserInteracting) {
        if (isAutoSwipeEnabled && effectivePosts.isNotEmpty() && !isUserInteracting) {
            while (true) {
                delay(autoSwipeInterval)
                if (isAutoSwipeEnabled && !isUserInteracting && pagerState.currentPage < effectivePosts.size - 1) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                } else if (isAutoSwipeEnabled && !isUserInteracting && pagerState.currentPage == effectivePosts.size - 1) {
                    pagerState.animateScrollToPage(0) // Возвращаемся к началу
                }
            }
        }
    }

    // Подгрузка новых постов при достижении конца списка
    LaunchedEffect(pagerState.currentPage) {
        val currentPage = pagerState.currentPage
        val totalPages = effectivePosts.size
        if (totalPages > 0 && currentPage >= totalPages - 5 && !viewModel.isLoading.value) {
            viewModel.loadPosts(append = true)
        }
    }

    // Обновление списка постов при изменении данных в viewModel
    LaunchedEffect(viewModel.posts.value) {
        val newPosts = viewModel.posts.value.filterNot { newPost ->
            effectivePosts.any { it.id == newPost.id && it.sourceType == newPost.sourceType }
        }
        if (newPosts.isNotEmpty()) {
            effectivePosts.addAll(newPosts)
            Log.d("FullScreenImage", "Added ${newPosts.size} new posts, new size: ${effectivePosts.size}")
        }
    }

    // Загружаем избранные посты из базы данных
    val favoritePosts by database.imagePostDao().getFavoritePosts().collectAsState(initial = emptyList())
    var isFavorited by remember { mutableStateOf(post.isFavorited) }

    LaunchedEffect(pagerState.currentPage, favoritePosts) {
        val currentPost = effectivePosts.getOrNull(pagerState.currentPage)
        isFavorited = currentPost?.let { post ->
            favoritePosts.any { it.id == post.id && it.isFavorited }
        } ?: false
        Log.d("FullScreenImage", "Updated isFavorited: $isFavorited for post ${currentPost?.id}")
    }

    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()

    // Запрос разрешения на запись для Android 9 и ниже
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            scope.launch {
                scaffoldState.snackbarHostState.showSnackbar("Storage permission denied")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Log.d("FullScreenImage", "Extracted Current Booru: $currentBooru, Initial Index: $initialIndex, Safe Initial Index: $safeInitialIndex, Posts Size: ${effectivePosts.size}")

    // Консолидированная функция для обработки действий с тегами
    fun handleTagAction(tag: String, action: TagAction) {
        when (action) {
            TagAction.SEARCH -> {
                viewModel.searchQuery.value = tag
                viewModel.page.value = 0
                viewModel.shouldReloadPosts.value = true
                viewModel.loadPosts()
            }
            TagAction.ADD_TO_SEARCH -> {
                viewModel.searchQuery.value += " $tag"
                viewModel.shouldReloadPosts.value = true
                viewModel.loadPosts(append = true)
            }
            TagAction.BLOCK -> {
                if (!viewModel.blockedTags.contains(tag)) {
                    viewModel.blockedTags.add(tag)
                    viewModel.saveBlockedTags()
                    viewModel.shouldReloadPosts.value = true
                    viewModel.loadPosts()
                }
            }
        }
        navController.popBackStack()
    }

    fun downloadImage(url: String?): Uri? {
        if (url == null) {
            scope.launch {
                scaffoldState.snackbarHostState.showSnackbar("Download failed: URL is null")
            }
            return null
        }

        return try {
            val fileExtension = url.substringAfterLast(".", "jpg")
            val fileName = "Booru_${System.currentTimeMillis()}.$fileExtension"
            val connection = URL(url).openConnection()
            connection.connect()
            val input = connection.getInputStream()

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Для Android 10+ используем MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/$fileExtension")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: throw Exception("Failed to create MediaStore entry")

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                uri
            } else {
                // Для Android 9 и ниже используем внешнее хранилище с разрешением
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    throw Exception("Storage permission required")
                }

                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(picturesDir, fileName)
                val output = FileOutputStream(file)
                input.copyTo(output)
                output.flush()
                output.close()
                Uri.fromFile(file)
            }

            input.close()
            scope.launch {
                scaffoldState.snackbarHostState.showSnackbar("File downloaded to Pictures")
            }
            uri
        } catch (e: Exception) {
            scope.launch {
                scaffoldState.snackbarHostState.showSnackbar("Download failed: ${e.message}")
            }
            Log.e("FullScreenImage", "Download failed: ${e.message}", e)
            null
        }
    }

    fun shareImage(url: String?) {
        if (url == null) {
            scope.launch {
                scaffoldState.snackbarHostState.showSnackbar("Share failed: URL is null")
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            val uri = downloadImage(url) // Скачиваем файл и получаем URI
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "image/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                }
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
                        )
                    )
                    .padding(16.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.h6,
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(effectivePosts.getOrNull(pagerState.currentPage)?.getTagsList() ?: emptyList()) { tag ->
                        TagChip(
                            tag = tag,
                            onClick = { selectedTag = tag },
                            isFavorite = viewModel.favoriteTags.contains(tag),
                            onFavoriteToggle = {
                                if (viewModel.favoriteTags.contains(tag)) {
                                    viewModel.favoriteTags.remove(tag)
                                    Log.d("FullScreenImage", "Removed tag from favorites: $tag")
                                } else {
                                    viewModel.favoriteTags.add(tag)
                                    Log.d("FullScreenImage", "Added tag to favorites: $tag")
                                }
                                viewModel.saveFavoriteTags()
                            }
                        )
                    }
                }
            }
        },
        sheetBackgroundColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Scaffold(
            backgroundColor = Color(0xFF0D1B2A),
            scaffoldState = scaffoldState,
            topBar = {
                TopAppBar(
                    title = { Text("Content ${pagerState.currentPage + 1}/${effectivePosts.size}", color = Color(0xFFE0E0E0)) },
                    backgroundColor = Color.Transparent,
                    elevation = 0.dp,
                    navigationIcon = {
                        AnimatedIconButton(
                            onClick = {
                                navController.previousBackStackEntry?.savedStateHandle?.set("lastPosition", pagerState.currentPage)
                                navController.popBackStack()
                            },
                            icon = Icons.Default.Close,
                            description = "Back",
                            color = Color(0xFF415A77),
                            modifier = Modifier.padding(start = 16.dp, top = statusBarHeight)
                        )
                    },
                    modifier = Modifier.padding(top = statusBarHeight)
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBarHeight)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
                        )
                    )
            ) {
                if (effectivePosts.isEmpty()) {
                    Text(
                        text = "No content available",
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.h6
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 8.dp
                    ) { page ->
                        if (page < 0 || page >= effectivePosts.size) {
                            return@HorizontalPager
                        }
                        val currentPost = effectivePosts[page]
                        Log.d("FullScreenImage", "Loading content: ${currentPost.effectiveFileUrl}, preview: ${currentPost.effectivePreviewUrl}")

                        val fileUrl = currentPost.effectiveFileUrl ?: currentPost.effectivePreviewUrl ?: ""
                        val fileExtension = fileUrl.substringAfterLast(".", "").lowercase()
                        val isImage = fileExtension in listOf("jpg", "jpeg", "png", "gif")
                        val isVideo = fileExtension in listOf("mp4", "webm")
                        val isFlash = fileExtension == "swf"

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            showContextMenu = true
                                            isUserInteracting = true
                                        },
                                        onPress = {
                                            isUserInteracting = true
                                            awaitRelease()
                                            isUserInteracting = false
                                        }
                                    )
                                }
                        ) {
                            when {
                                isImage -> {
                                    val painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(fileUrl)
                                            .crossfade(true)
                                            .size(coil.size.Size.ORIGINAL) // Загружаем оригинальный размер
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/89.0.4389.114")
                                            .error(android.R.drawable.ic_menu_close_clear_cancel)
                                            .build(),
                                        placeholder = rememberAsyncImagePainter(currentPost.effectivePreviewUrl ?: fileUrl),
                                        onError = { error ->
                                            Log.e("FullScreenImage", "Image load failed: ${error.result.throwable.message}")
                                        }
                                    )
                                    val isLoading = painter.state is coil.compose.AsyncImagePainter.State.Loading

                                    Image(
                                        painter = painter,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(48.dp),
                                            color = Color(0xFF778DA9)
                                        )
                                    }
                                }
                                isVideo -> {
                                    VideoPlayer(
                                        videoUrl = fileUrl,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                isFlash -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFFE63946),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Flash (.swf) is not supported on this device",
                                                color = Color(0xFFE63946),
                                                style = MaterialTheme.typography.h6,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFFE63946),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Unsupported format: $fileExtension",
                                                color = Color(0xFFE63946),
                                                style = MaterialTheme.typography.h6,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Индикатор автопрокрутки
                    if (isAutoSwipeEnabled && !isUserInteracting) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val progress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = autoSwipeInterval.toInt(), easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .align(Alignment.TopCenter),
                            color = Color(0xFF778DA9),
                            backgroundColor = Color(0xFF415A77).copy(alpha = 0.3f)
                        )
                    }
                }

                // Контекстное меню с анимацией
                AnimatedVisibility(
                    visible = showContextMenu,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = tween(300, easing = FastOutSlowInEasing)),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                onClick = { showContextMenu = false },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF1B263B), Color(0xFF0D1B2A))
                                    )
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Кнопка "Лайк"
                            val scale by animateFloatAsState(
                                targetValue = if (isFavorited) 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Favorite,
                                text = if (isFavorited) "Unlike" else "Like",
                                iconColor = if (isFavorited) Color(0xFFE63946) else Color(0xFFE0E0E0),
                                modifier = Modifier.scale(scale),
                                onClick = {
                                    if (effectivePosts.isNotEmpty()) {
                                        val currentPost = effectivePosts[pagerState.currentPage]
                                        scope.launch {
                                            // Сохраняем пост в базе данных, если его там нет
                                            database.imagePostDao().insert(currentPost.toEntity())
                                            // Обновляем статус избранного
                                            database.imagePostDao().updateFavoriteStatus(
                                                postId = currentPost.id,
                                                isFavorited = !isFavorited
                                            )
                                            isFavorited = !isFavorited
                                            Log.d("FullScreenImage", "Favorite toggled for post ${currentPost.id}, isFavorited: $isFavorited")
                                        }
                                    }
                                    showContextMenu = false
                                }
                            )

                            // Кнопка "Скачать"
                            ContextMenuItem(
                                icon = Icons.Default.Download,
                                text = "Download",
                                iconColor = Color(0xFFE0E0E0),
                                onClick = {
                                    val currentPost = effectivePosts.getOrNull(pagerState.currentPage)
                                    downloadImage(currentPost?.effectiveFileUrl)
                                    showContextMenu = false
                                }
                            )

                            // Кнопка "Теги"
                            ContextMenuItem(
                                icon = Icons.Default.List,
                                text = "Tags",
                                iconColor = Color(0xFFE0E0E0),
                                onClick = {
                                    scope.launch { sheetState.show() }
                                    showContextMenu = false
                                }
                            )

                            // Кнопка "Поделиться"
                            ContextMenuItem(
                                icon = Icons.Default.Share,
                                text = "Share",
                                iconColor = Color(0xFFE0E0E0),
                                onClick = {
                                    val currentPost = effectivePosts.getOrNull(pagerState.currentPage)
                                    shareImage(currentPost?.effectiveFileUrl)
                                    showContextMenu = false
                                }
                            )

                            // Кнопка "Авто-свайп"
                            ContextMenuItem(
                                icon = if (isAutoSwipeEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                                text = if (isAutoSwipeEnabled) "Stop Auto-Swipe" else "Start Auto-Swipe",
                                iconColor = if (isAutoSwipeEnabled) Color(0xFFE63946) else Color(0xFFE0E0E0),
                                onClick = {
                                    if (isAutoSwipeEnabled) {
                                        isAutoSwipeEnabled = false
                                        showContextMenu = false
                                    } else {
                                        showSpeedDialog = true // Показываем диалог для выбора скорости
                                    }
                                }
                            )
                        }
                    }
                }

                // Диалог выбора скорости автопрокрутки
                if (showSpeedDialog) {
                    AlertDialog(
                        onDismissRequest = { showSpeedDialog = false },
                        title = { Text("Select Auto-Swipe Speed", color = Color(0xFFE0E0E0)) },
                        backgroundColor = Color(0xFF0D1B2A),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1B2A)),
                        buttons = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SpeedOption(
                                    text = "3 seconds",
                                    onClick = {
                                        autoSwipeInterval = 3000L
                                        isAutoSwipeEnabled = true
                                        showSpeedDialog = false
                                        showContextMenu = false
                                    }
                                )
                                SpeedOption(
                                    text = "5 seconds",
                                    onClick = {
                                        autoSwipeInterval = 5000L
                                        isAutoSwipeEnabled = true
                                        showSpeedDialog = false
                                        showContextMenu = false
                                    }
                                )
                                SpeedOption(
                                    text = "10 seconds",
                                    onClick = {
                                        autoSwipeInterval = 10000L
                                        isAutoSwipeEnabled = true
                                        showSpeedDialog = false
                                        showContextMenu = false
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    selectedTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { selectedTag = null },
            title = { Text("Tag: $tag", color = Color(0xFFE0E0E0)) },
            buttons = {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    AnimatedButton(
                        onClick = { handleTagAction(tag, TagAction.SEARCH); selectedTag = null },
                        text = "Search",
                        color = Color(0xFF778DA9)
                    )
                    AnimatedButton(
                        onClick = { handleTagAction(tag, TagAction.ADD_TO_SEARCH); selectedTag = null },
                        text = "Add to search",
                        color = Color(0xFF778DA9),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    AnimatedButton(
                        onClick = { handleTagAction(tag, TagAction.BLOCK); selectedTag = null },
                        text = "Block",
                        color = Color(0xFF778DA9),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            backgroundColor = Color(0xFF0D1B2A),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0D1B2A))
        )
    }
}

@Composable
fun ContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF415A77).copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFFE0E0E0),
            style = MaterialTheme.typography.body1
        )
    }
}

@Composable
fun SpeedOption(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF415A77).copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color(0xFFE0E0E0),
            style = MaterialTheme.typography.body1
        )
    }
}

@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_ALL // Зацикливаем воспроизведение
        }
    }

    LaunchedEffect(videoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = exoPlayer
            }
        )
        FloatingActionButton(
            onClick = { isMuted = !isMuted },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp),
            backgroundColor = Color(0xFF0D1B2A).copy(alpha = 0.8f),
            contentColor = Color(0xFFE0E0E0)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Toggle mute",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

enum class TagAction {
    SEARCH, ADD_TO_SEARCH, BLOCK
}