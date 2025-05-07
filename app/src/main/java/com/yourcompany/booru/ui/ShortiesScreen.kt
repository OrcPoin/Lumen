package com.yourcompany.booru.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.yourcompany.booru.data.VideoPost
import com.yourcompany.booru.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ShortiesScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val videos by viewModel.videos
    val isLoading by viewModel.isLoading
    val pagerState = rememberPagerState(pageCount = { videos.size })
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current as ComponentActivity

    LaunchedEffect(Unit) {
        WindowCompat.setDecorFitsSystemWindows(context.window, false)
        val insetsController = WindowCompat.getInsetsController(context.window, context.window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        context.window.statusBarColor = android.graphics.Color.TRANSPARENT
        context.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        viewModel.setNavController(navController)
    }

    LaunchedEffect(pagerState.currentPage) {
        val threshold = 5
        if (videos.isNotEmpty() && pagerState.currentPage >= videos.size - threshold && !isLoading) {
            viewModel.loadVideos(append = true)
        }
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.currentExoPlayer?.play()
        } else {
            viewModel.currentExoPlayer?.pause()
        }
    }

    LaunchedEffect(Unit) {
        if (videos.isEmpty()) {
            viewModel.page.value = 0
            viewModel.loadVideos()
        }
        val lastPosition = viewModel.lastShortiesPosition.value
        if (lastPosition != null && lastPosition < videos.size) {
            scope.launch {
                pagerState.scrollToPage(lastPosition)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (videos.isEmpty()) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    color = Color.White,
                    strokeWidth = 6.dp
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No videos available",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadVideos() }) {
                        Text("Refresh")
                    }
                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = tween(
                        durationMillis = if (abs(pagerState.currentPageOffsetFraction) > 0.5f) 200 else 400,
                        easing = FastOutSlowInEasing
                    )
                )
            ) { page ->
                val video = videos[page]
                VideoPlayer(
                    video = video,
                    isVisible = pagerState.currentPage == page,
                    viewModel = viewModel,
                    videos = videos,
                    pagerState = pagerState,
                    snackbarHostState = snackbarHostState,
                    onNext = {
                        scope.launch {
                            if (page < videos.size - 1) {
                                pagerState.animateScrollToPage(page + 1)
                            } else {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    vibrator.vibrate(50)
                                }
                                viewModel.loadVideos(append = true)
                            }
                        }
                    },
                    onPrevious = {
                        scope.launch {
                            if (page > 0) {
                                pagerState.animateScrollToPage(page - 1)
                            } else {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    vibrator.vibrate(50)
                                }
                            }
                        }
                    }
                )
            }

            if (videos.isNotEmpty()) {
                Text(
                    text = "${pagerState.currentPage + 1}/${videos.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + pagerState.currentPageOffsetFraction) / (videos.size - 1).coerceAtLeast(1).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter),
                    color = Color.White.copy(alpha = 0.3f),
                    trackColor = Color.Transparent
                )
            }

            AnimatedVisibility(
                visible = pagerState.currentPage == videos.size - 1 && pagerState.currentPageOffsetFraction == 0f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No more videos",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadVideos(append = true) }) {
                        Text("Load More")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun VideoPlayer(
    video: VideoPost,
    isVisible: Boolean,
    viewModel: MainViewModel,
    videos: List<VideoPost>,
    pagerState: PagerState,
    snackbarHostState: SnackbarHostState,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val displayMetrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val display = (context as ComponentActivity).display
        display?.getRealMetrics(displayMetrics)
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(displayMetrics)
    }
    val screenHeight = displayMetrics.heightPixels.toFloat()
    val swipeThreshold by remember { mutableFloatStateOf(screenHeight * 0.1f) }

    val exoPlayer = remember(video.videoUrl) {
        viewModel.exoPlayerPool.getOrPut(video.videoUrl) {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    val dataSourceFactory = DefaultHttpDataSource.Factory()
                    val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(video.videoUrl))
                    setMediaSource(hlsMediaSource)
                    prepare()
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_ONE
                    seekTo(viewModel.getVideoProgress(video.id))
                }
        }
    }

    val showControls = remember { mutableStateOf(true) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isBuffering = remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(0f) }
    val savedPosition = remember { mutableLongStateOf(0L) }
    val likes = remember { mutableIntStateOf(Random.nextInt(100, 1000)) }
    val comments = remember { mutableIntStateOf(Random.nextInt(50, 500)) }
    val views = remember { mutableIntStateOf(Random.nextInt(1000, 10000)) }
    val isLiked = remember { mutableStateOf(viewModel.isVideoLiked(video.id)) }
    val heartOffset = animateFloatAsState(
        targetValue = if (isLiked.value) -200f else 0f,
        animationSpec = tween(durationMillis = 500)
    )
    val heartScale = animateFloatAsState(
        targetValue = if (isLiked.value) 1.5f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    val pulseAnimation = rememberInfiniteTransition()
    val pulseScale = pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val floatAnimation = rememberInfiniteTransition()
    val floatOffset = floatAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val scrollAnimation = rememberInfiniteTransition()
    val scrollOffset = scrollAnimation.animateFloat(
        initialValue = 0f,
        targetValue = -100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val isPaused = remember { mutableStateOf(false) }
    val showPauseIcon = remember { mutableStateOf(false) }
    val lastTapTime = remember { mutableLongStateOf(0L) }
    var isScrolling by remember { mutableStateOf(false) }
    var lastSwipeDirection by remember { mutableFloatStateOf(0f) }
    val fadeEffect = animateFloatAsState(
        targetValue = if (isScrolling) 0.7f else 1f,
        animationSpec = tween(durationMillis = 200)
    )
    val uiShift = animateFloatAsState(
        targetValue = if (isScrolling) lastSwipeDirection / 10f else 0f,
        animationSpec = tween(durationMillis = 200)
    )
    val swipeProgress = animateFloatAsState(
        targetValue = abs(pagerState.currentPageOffsetFraction),
        animationSpec = tween(durationMillis = 100)
    )

    LaunchedEffect(showControls.value) {
        if (showControls.value) {
            delay(5000)
            showControls.value = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                val duration = exoPlayer.duration.toFloat()
                val currentPosition = exoPlayer.currentPosition.toFloat()
                progress.floatValue = if (duration > 0f) currentPosition / duration else 0f
            }
            delay(1000)
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            exoPlayer.seekTo(savedPosition.longValue)
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            viewModel.currentExoPlayer = exoPlayer
            viewModel.addToWatchHistory(video.id)
            val currentIndex = pagerState.currentPage
            val indicesToPreload = listOf(
                (currentIndex + 1).coerceAtMost(videos.size - 1),
                (currentIndex + 2).coerceAtMost(videos.size - 1),
                (currentIndex + 3).coerceAtMost(videos.size - 1),
                (currentIndex - 1).coerceAtLeast(0),
                (currentIndex - 2).coerceAtLeast(0)
            ).distinct()
            indicesToPreload.forEach { index ->
                if (index < videos.size && abs(index - currentIndex) <= 3) {
                    val nextVideo = videos[index]
                    viewModel.exoPlayerPool.getOrPut(nextVideo.videoUrl) {
                        ExoPlayer.Builder(context)
                            .build()
                            .apply {
                                val dataSourceFactory = DefaultHttpDataSource.Factory()
                                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                                    .createMediaSource(MediaItem.fromUri(nextVideo.videoUrl))
                                setMediaSource(hlsMediaSource)
                                prepare()
                            }
                    }
                }
            }
        } else {
            savedPosition.longValue = exoPlayer.currentPosition
            exoPlayer.pause()
            viewModel.saveVideoProgress(video.id, exoPlayer.currentPosition)
        }
    }

    LaunchedEffect(pagerState.currentPageOffsetFraction) {
        if (abs(pagerState.currentPageOffsetFraction) > 0.1f) {
            isScrolling = true
            exoPlayer.pause()
        } else {
            isScrolling = false
            if (isVisible) {
                exoPlayer.play()
                if (exoPlayer.duration < 5000 && pagerState.currentPageOffsetFraction > 0) {
                    onNext()
                }
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> onNext()
                    Player.STATE_BUFFERING -> {
                        isBuffering.value = true
                        if (isScrolling) {
                            scope.launch {
                                pagerState.scrollToPage(pagerState.currentPage)
                            }
                        }
                    }
                    Player.STATE_READY -> isBuffering.value = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMessage.value = "Error: ${error.message}"
                scope.launch {
                    snackbarHostState.showSnackbar("Playback error: ${error.message}")
                    pagerState.scrollToPage(pagerState.currentPage)
                }
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isVisible) {
                savedPosition.longValue = exoPlayer.currentPosition
                exoPlayer.pause()
                viewModel.lastShortiesPosition.value = pagerState.currentPage
            }
            val currentIndex = pagerState.currentPage
            viewModel.exoPlayerPool.entries
                .filter { entry ->
                    val index = videos.indexOfFirst { it.videoUrl == entry.key }
                    index == -1 || abs(index - currentIndex) > 3
                }
                .forEach { (key, player) ->
                    player.release()
                    viewModel.exoPlayerPool.remove(key)
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(if (isVisible) 1f else 0.5f)
            .pointerInput(Unit) {
                var dragAmountY = 0f
                var dragAmountX = 0f
                detectDragGestures(
                    onDragStart = {
                        dragAmountY = 0f
                        dragAmountX = 0f
                        isScrolling = true
                    },
                    onDragEnd = {
                        isScrolling = false
                        if (abs(dragAmountY) > swipeThreshold) {
                            if (dragAmountY < 0) { // Свайп снизу вверх — вперед
                                onNext()
                            } else { // Свайп сверху вниз — назад
                                onPrevious()
                            }
                        }
                        dragAmountY = 0f
                        dragAmountX = 0f
                        lastSwipeDirection = 0f
                    },
                    onDrag = { change, drag ->
                        dragAmountY += drag.y
                        dragAmountX += drag.x
                        lastSwipeDirection = drag.y

                        if (abs(dragAmountY) < swipeThreshold) {
                            change.consume()
                            return@detectDragGestures
                        }
                        val x = change.position.x
                        if (x in (size.width * 0.3f)..(size.width * 0.7f) && abs(dragAmountX) > abs(dragAmountY)) {
                            val delta = drag.x / size.width.toFloat()
                            val seekDelta = (delta * exoPlayer.duration).toLong()
                            exoPlayer.seekTo((exoPlayer.currentPosition + seekDelta).coerceIn(0L, exoPlayer.duration))
                            if (abs(dragAmountX) > size.width * 0.1f) {
                                val speed = (abs(dragAmountX) / size.width.toFloat() * 5f).coerceAtMost(5f)
                                exoPlayer.setPlaybackParameters(PlaybackParameters(speed))
                            } else {
                                exoPlayer.setPlaybackParameters(PlaybackParameters(1f))
                            }
                            dragAmountX += drag.x
                            showControls.value = true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        val x = it.x
                        if (currentTime - lastTapTime.longValue < 300) {
                            if (x < size.width * 0.3f || x > size.width * 0.7f) {
                                exoPlayer.pause()
                                isPaused.value = true
                                showPauseIcon.value = true
                            } else {
                                exoPlayer.seekTo(exoPlayer.currentPosition + 10000L)
                            }
                        } else if (currentTime - lastTapTime.longValue < 600) {
                            if (x in (size.width * 0.3f)..(size.width * 0.7f)) {
                                exoPlayer.setPlaybackParameters(PlaybackParameters(2f))
                            }
                        } else if (currentTime - lastTapTime.longValue < 900) {
                            if (x in (size.width * 0.3f)..(size.width * 0.7f)) {
                                exoPlayer.seekTo(0L)
                            }
                        } else {
                            if (x in (size.width * 0.3f)..(size.width * 0.7f)) {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    isPaused.value = true
                                    showPauseIcon.value = true
                                } else {
                                    exoPlayer.play()
                                    isPaused.value = false
                                    showPauseIcon.value = true
                                }
                            }
                        }
                        lastTapTime.longValue = currentTime
                        showControls.value = true
                    },
                    onLongPress = {
                        exoPlayer.setPlaybackParameters(PlaybackParameters(1.5f))
                        showControls.value = true
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (isScrolling) 0.3f else 0f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isScrolling) 0.3f else 0f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = isScrolling,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { swipeProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center),
                color = Color.White.copy(alpha = 0.5f),
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
        }

        AnimatedVisibility(
            visible = isScrolling && abs(lastSwipeDirection) > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = if (lastSwipeDirection < 0) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = "Swipe direction",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL // Видео заполняет экран
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isVisible) fadeEffect.value else 0f)
        )

        if (isBuffering.value) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .scale(pulseScale.value),
                color = Color.White,
                strokeWidth = 4.dp
            )
        }

        errorMessage.value?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }) {
                    Text("Retry")
                }
            }
        }

        AnimatedVisibility(
            visible = showControls.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { progress.floatValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .scale(pulseScale.value),
                color = Color.White,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
        }

        AnimatedVisibility(
            visible = showPauseIcon.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPaused.value) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused.value) "Play" else "Pause",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(80.dp)
                        .padding(8.dp)
                        .scale(pulseScale.value)
                )
            }
            LaunchedEffect(showPauseIcon.value) {
                delay(500)
                showPauseIcon.value = false
            }
        }

        AnimatedVisibility(
            visible = showControls.value,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, bottom = 60.dp)
                .offset(y = floatOffset.value.dp, x = uiShift.value.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            isLiked.value = !isLiked.value
                            if (isLiked.value) {
                                likes.intValue += 1
                                viewModel.likeVideo(video.id)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    vibrator.vibrate(50)
                                }
                            } else {
                                viewModel.unlikeVideo(video.id)
                            }
                            showControls.value = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Like video",
                            tint = if (isLiked.value) Color.Red.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = if (likes.intValue >= 1000) "${likes.intValue / 1000}K" else likes.intValue.toString(),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            showControls.value = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = "Comments",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = if (comments.intValue >= 1000) "${comments.intValue / 1000}K" else comments.intValue.toString(),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.toggleVideoFavorite(video.id, !video.isFavorited)
                        showControls.value = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (video.isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Add to favorites",
                        tint = if (video.isFavorited) Color.Yellow.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, video.videoUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Video"))
                        showControls.value = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share video",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            val randomPage = Random.nextInt(videos.size)
                            pagerState.animateScrollToPage(randomPage)
                        }
                        showControls.value = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Random video",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = heartScale.value > 0f,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.Red.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(120.dp)
                    .scale(heartScale.value)
                    .offset(y = heartOffset.value.dp)
            )
        }

        AnimatedVisibility(
            visible = showControls.value,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.7f)
                .padding(start = 16.dp, end = 16.dp, bottom = 60.dp)
                .offset(x = uiShift.value.dp)
        ) {
            Column(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls.value = true
                            }
                        )
                    }
            ) {
                Text(
                    text = "@username",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.customShadow()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.customShadow()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (views.intValue >= 1000) "${views.intValue / 1000}K views" else "${views.intValue} views",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                    modifier = Modifier.customShadow()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "#fyp #trend",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.customShadow()
                )
            }
        }

        AnimatedVisibility(
            visible = showControls.value,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.7f)
                .padding(start = 16.dp, bottom = 20.dp)
                .offset(x = uiShift.value.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Music",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Original Sound - Artist Name",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(x = scrollOffset.value.dp)
                        .wrapContentWidth()
                )
            }
        }
    }
}

fun Modifier.customShadow(
    color: Color = Color.Black,
    offsetX: Dp = 1.dp,
    offsetY: Dp = 1.dp,
    blurRadius: Dp = 1.dp
) = this.drawBehind {
    drawIntoCanvas { canvas: Canvas ->
        val paint = Paint().apply {
            this.color = color
            style = PaintingStyle.Fill
            if (blurRadius != 0.dp) {
                this.asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.save()
        canvas.translate(offsetX.toPx(), offsetY.toPx())
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
        canvas.restore()
    }
}