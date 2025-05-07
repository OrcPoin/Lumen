package com.yourcompany.booru.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourcompany.booru.api.BooruApiClient
import com.yourcompany.booru.api.PornhubApiClient
import com.yourcompany.booru.api.PornhubShortiesClient
import com.yourcompany.booru.api.RedditApiClient
import com.yourcompany.booru.data.DatabaseProvider
import com.yourcompany.booru.data.ImagePost
import com.yourcompany.booru.data.VideoPost
import com.yourcompany.booru.data.toEntity
import com.yourcompany.booru.data.toImagePost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(private val context: Context) : ViewModel() {
    private val booruApiClient = BooruApiClient
    private val pornhubApiClient = PornhubApiClient
    private val shortiesClient = PornhubShortiesClient

    // Список доступных Booru источников
    val booruOptions = listOf(
        "https://rule34.xxx/"
    )

    val posts = mutableStateOf<List<ImagePost>>(emptyList())
    val feedPosts = mutableStateOf<List<ImagePost>>(emptyList())
    val videos = mutableStateOf<List<VideoPost>>(emptyList())
    val page = mutableStateOf(0)
    val searchQuery = mutableStateOf("")
    val feedSearchQuery = mutableStateOf("")
    val selectedBooru = mutableStateOf(booruOptions[0])
    val errorMessage = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    val isOfflineMode = mutableStateOf(false)

    // Новые поля для управления видео
    val isMuted = mutableStateOf(true)
    val exoPlayerPool = ConcurrentHashMap<String, ExoPlayer>()
    var currentExoPlayer: ExoPlayer? = null
    private var navController: NavController? = null
    val lastShortiesPosition = mutableStateOf<Int?>(null) // Добавлено для сохранения позиции в списке видео
    private val watchHistory = mutableSetOf<Int>() // История просмотров видео
    private val likedVideos = mutableSetOf<Int>() // Лайкнутые видео
    private val videoProgress = mutableMapOf<Int, Long>() // Прогресс видео

    var scrollPosition: Int = 0
    var scrollOffset: Int = 0
    var feedScrollPosition: Int = 0
    var feedScrollOffset: Int = 0
    val shouldReloadPosts = mutableStateOf(true)
    val shouldReloadFeedPosts = mutableStateOf(true)

    val searchHistory = mutableStateListOf<String>()
    val favoriteTags = mutableStateListOf<String>()
    val blockedTags = mutableStateListOf<String>()
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("BooruPrefs", Context.MODE_PRIVATE)
    private val database = DatabaseProvider.getDatabase(context)
    private val imagePostDao = database.imagePostDao()
    private val videoPostDao = database.videoPostDao()

    val hideDuplicates = mutableStateOf(false)
    val minWidth = mutableStateOf<Int?>(null)
    val sortOrder = mutableStateOf(SortOrder.NEWEST)

    // Храним уже показанные посты (для пагинации)
    private val seenPostKeys = ConcurrentHashMap.newKeySet<String>()
    private val pageMap = mutableMapOf<String, Int>() // Страницы для каждого источника

    // Map to store custom names for booru galleries
    private val booruCustomNames = mutableStateMapOf<String, String>()

    enum class SortOrder {
        NEWEST, OLDEST, POPULAR
    }

    init {
        loadSavedData()
        loadPostsFromDatabase()
        loadVideoRelatedData() // Загрузка данных, связанных с видео
        // Load custom names from SharedPreferences
        val customNamesJson = sharedPreferences.getString("booruCustomNames", null)
        if (customNamesJson != null) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val names = Gson().fromJson<Map<String, String>>(customNamesJson, type)
            booruCustomNames.putAll(names)
        }
    }

    private fun loadSavedData() {
        val savedBlockedTagsJson = sharedPreferences.getString("blockedTags", null)
        if (savedBlockedTagsJson != null && blockedTags.isEmpty()) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            val loadedTags = Gson().fromJson<MutableList<String>>(savedBlockedTagsJson, type).distinct()
            blockedTags.clear()
            blockedTags.addAll(loadedTags)
        }

        val savedHistoryJson = sharedPreferences.getString("searchHistory", null)
        if (savedHistoryJson != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            searchHistory.clear()
            searchHistory.addAll(Gson().fromJson(savedHistoryJson, type))
        }

        val savedFavoriteTagsJson = sharedPreferences.getString("favoriteTags", null)
        if (savedFavoriteTagsJson != null && favoriteTags.isEmpty()) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            val loadedTags = Gson().fromJson<MutableList<String>>(savedFavoriteTagsJson, type).distinct()
            favoriteTags.clear()
            favoriteTags.addAll(loadedTags)
        }

        val savedBooru = sharedPreferences.getString("selectedBooru", null)
        if (savedBooru != null && booruOptions.contains(savedBooru)) {
            selectedBooru.value = savedBooru
        }

        // Инициализация isMuted из SharedPreferences
        isMuted.value = sharedPreferences.getBoolean("isMuted", true)
    }

    // Загрузка данных, связанных с видео (лайки, история просмотров, прогресс)
    private fun loadVideoRelatedData() {
        val savedLikedVideosJson = sharedPreferences.getString("likedVideos", null)
        if (savedLikedVideosJson != null) {
            val type = object : TypeToken<MutableSet<Int>>() {}.type
            likedVideos.clear()
            likedVideos.addAll(Gson().fromJson(savedLikedVideosJson, type))
        }

        val savedWatchHistoryJson = sharedPreferences.getString("watchHistory", null)
        if (savedWatchHistoryJson != null) {
            val type = object : TypeToken<MutableSet<Int>>() {}.type
            watchHistory.clear()
            watchHistory.addAll(Gson().fromJson(savedWatchHistoryJson, type))
        }

        val savedLastShortiesPosition = sharedPreferences.getInt("lastShortiesPosition", -1)
        if (savedLastShortiesPosition != -1) {
            lastShortiesPosition.value = savedLastShortiesPosition
        }

        val savedVideoProgressJson = sharedPreferences.getString("videoProgress", null)
        if (savedVideoProgressJson != null) {
            val type = object : TypeToken<Map<Int, Long>>() {}.type
            videoProgress.clear()
            videoProgress.putAll(Gson().fromJson(savedVideoProgressJson, type))
        }
    }

    fun saveSelectedBooru() {
        with(sharedPreferences.edit()) {
            putString("selectedBooru", selectedBooru.value)
            apply()
        }
    }

    // Сохранение состояния звука
    fun saveMutedState() {
        with(sharedPreferences.edit()) {
            putBoolean("isMuted", isMuted.value)
            apply()
        }
    }

    // Сохранение данных, связанных с видео
    private fun saveVideoRelatedData() {
        with(sharedPreferences.edit()) {
            putString("likedVideos", Gson().toJson(likedVideos))
            putString("watchHistory", Gson().toJson(watchHistory))
            putString("videoProgress", Gson().toJson(videoProgress))
            val position = lastShortiesPosition.value ?: -1
            putInt("lastShortiesPosition", position)
            apply()
        }
    }

    // Сохранение прогресса видео
    fun saveVideoProgress(videoId: Int, position: Long) {
        videoProgress[videoId] = position
        saveVideoRelatedData()
    }

    // Получение прогресса видео
    fun getVideoProgress(videoId: Int): Long {
        return videoProgress[videoId] ?: 0L
    }

    fun setNavController(navController: NavController) {
        this.navController = navController
    }

    fun navigateBack() {
        navController?.popBackStack()
    }

    private fun loadPostsFromDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedPosts = imagePostDao.getAllPosts().map { it.toImagePost() }
            withContext(Dispatchers.Main) {
                if (cachedPosts.isNotEmpty()) {
                    posts.value = applyFiltersAndSort(cachedPosts)
                    feedPosts.value = applyFiltersAndSort(cachedPosts)
                    Log.d("MainViewModel", "Loaded ${cachedPosts.size} posts from database at startup")
                }
                if (shouldReloadPosts.value) {
                    loadPosts(append = false)
                }
                if (shouldReloadFeedPosts.value) {
                    loadFeedPosts(append = false)
                }
            }
        }
    }

    fun loadPosts(append: Boolean = false) {
        if (!shouldReloadPosts.value && !append) return
        if (isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            try {
                val existingFavorites = imagePostDao.getFavoritePosts().first().associateBy { it.id }
                booruApiClient.setBaseUrl(selectedBooru.value)
                
                // Reset pagination if not appending
                if (!append) {
                    if (selectedBooru.value.contains("reddit.com")) {
                        val subreddit = selectedBooru.value.substringAfter("reddit.com/r/").substringBefore("/")
                        RedditApiClient.resetPagination(subreddit)
                    }
                    page.value = 0 // Reset page counter when switching sources
                }
                
                val response: List<ImagePost> = booruApiClient.fetchPosts(
                    tags = searchQuery.value,
                    limit = 20,
                    page = page.value,
                    baseUrl = selectedBooru.value,
                    context = context
                )
                Log.d("MainViewModel", "API Response: $response")
                val postsWithSource = response.map { post ->
                    post.copy(sourceType = selectedBooru.value)
                }
                val uniquePosts = postsWithSource.distinctBy { "${it.id}-${it.sourceType}" }
                val filteredPosts: List<ImagePost> = uniquePosts.filter { post: ImagePost ->
                    post.getTagsList().none { tag -> tag in blockedTags }
                }

                withContext(Dispatchers.Main) {
                    val postsToInsert = filteredPosts.map { post ->
                        val existingFavorite = existingFavorites[post.id]
                        if (existingFavorite != null && existingFavorite.isFavorited) {
                            post.copy(isFavorited = true)
                        } else {
                            post
                        }.toEntity()
                    }
                    imagePostDao.insertPosts(postsToInsert)
                    Log.d("MainViewModel", "Saved ${filteredPosts.size} posts to database")

                    val updatedPosts: List<ImagePost> = if (append) {
                        val currentPosts = posts.value as List<ImagePost>
                        val newPosts = filteredPosts.filter { newPost ->
                            currentPosts.none { existingPost -> 
                                existingPost.id == newPost.id && existingPost.sourceType == newPost.sourceType
                            }
                        }
                        // Сохраняем порядок существующих постов и добавляем новые в конец
                        currentPosts + newPosts
                    } else {
                        filteredPosts
                    }
                    
                    // Применяем фильтры и сортировку только к новым постам при append
                    val finalPosts = if (append) {
                        val currentPosts = updatedPosts.take(posts.value.size)
                        val newPosts = applyFiltersAndSort(updatedPosts.drop(posts.value.size))
                        currentPosts + newPosts
                    } else {
                        applyFiltersAndSort(updatedPosts)
                    }
                    
                    posts.value = finalPosts
                    if (filteredPosts.isNotEmpty()) {
                        page.value++
                        errorMessage.value = null
                        shouldReloadPosts.value = false
                        isOfflineMode.value = false
                    } else if (!append) {
                        // Если это не append и нет постов, показываем сообщение
                        errorMessage.value = "No posts found"
                    }

                    val favorites = imagePostDao.getFavoritePosts().first()
                    Log.d("MainViewModel", "Favorites after load: ${favorites.size}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MainViewModel", "Load posts failed: ${e.message}", e)
                    val cachedPosts = imagePostDao.getAllPosts().map { it.toImagePost() }
                    Log.d("MainViewModel", "Loaded ${cachedPosts.size} posts from database due to error")
                    if (cachedPosts.isNotEmpty()) {
                        posts.value = applyFiltersAndSort(cachedPosts)
                        feedPosts.value = applyFiltersAndSort(cachedPosts)
                        errorMessage.value = "Офлайн-режим: загружены сохранённые посты"
                        isOfflineMode.value = true
                    } else {
                        errorMessage.value = "Нет сети и сохранённых данных: ${e.message}"
                        isOfflineMode.value = true
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
            }
        }
    }

    fun loadFeedPosts(append: Boolean = false) {
        if (!shouldReloadFeedPosts.value && !append) return
        if (isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            try {
                val existingFavorites = imagePostDao.getFavoritePosts().first().associateBy { it.id }
                val allPosts = mutableListOf<ImagePost>()
                val postsPerSource = 5

                if (!append) {
                    seenPostKeys.clear()
                    pageMap.clear()
                }

                val filteredBooruOptions: List<String> = booruOptions.filter { booruUrl: String ->
                    !booruUrl.contains("pornhub.com/shorties") && !booruUrl.contains("booru.allthefallen.moe")
                }

                val deferredPosts = filteredBooruOptions.map { booruUrl ->
                    async {
                        try {
                            val currentPage = pageMap.getOrPut(booruUrl) { if (append) 1 else 0 }
                            if (append) pageMap[booruUrl] = currentPage + 1

                            booruApiClient.setBaseUrl(booruUrl)
                            val response: List<ImagePost> = booruApiClient.fetchPosts(
                                tags = feedSearchQuery.value.takeIf { it.isNotBlank() } ?: favoriteTags.joinToString(" "),
                                limit = postsPerSource,
                                page = currentPage,
                                baseUrl = booruUrl,
                                context = context
                            )
                            response.filter { post: ImagePost ->
                                post.getTagsList().none { tag: String -> tag in blockedTags }
                            }.map { post: ImagePost ->
                                post.copy(sourceType = booruUrl)
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Failed to load posts from $booruUrl: ${e.message}")
                            emptyList<ImagePost>()
                        }
                    }
                }

                val results = deferredPosts.awaitAll()
                val sourcePostsMap = filteredBooruOptions.zip(results).toMap()

                val balancedPosts = mutableListOf<ImagePost>()
                repeat(postsPerSource) { iteration ->
                    sourcePostsMap.forEach { (source: String, posts: List<ImagePost>) ->
                        val postIndex = iteration
                        if (postIndex < posts.size) {
                            val post = posts[postIndex]
                            val key = "${post.id}-${post.sourceType}"
                            if (seenPostKeys.add(key)) {
                                balancedPosts.add(post)
                            }
                        }
                    }
                }

                val sortedPosts = balancedPosts.map { post: ImagePost ->
                    var score = 0.0

                    if (post.isFavorited) score += 2.0

                    val tags = post.getTagsList()
                    tags.forEach { tag ->
                        if (tag in favoriteTags) score += 1.0
                        if (tag in searchHistory) score += 0.5
                    }

                    post.score?.let { postScore ->
                        val normalizedScore = (postScore / 1000.0).coerceIn(0.0, 1.0)
                        score += normalizedScore
                    }

                    post to score
                }.sortedByDescending { it.second }
                    .map { it.first }

                withContext(Dispatchers.Main) {
                    val postsToInsert = sortedPosts.map { post ->
                        val existingFavorite = existingFavorites[post.id]
                        if (existingFavorite != null && existingFavorite.isFavorited) {
                            post.copy(isFavorited = true)
                        } else {
                            post
                        }.toEntity()
                    }
                    imagePostDao.insertPosts(postsToInsert)
                    Log.d("MainViewModel", "Saved ${sortedPosts.size} feed posts to database")

                    val updatedPosts: List<ImagePost> = if (append) {
                        val combinedPosts = feedPosts.value + sortedPosts
                        combinedPosts.distinctBy { "${it.id}-${it.sourceType}" }
                    } else {
                        sortedPosts
                    }
                    feedPosts.value = applyFiltersAndSort(updatedPosts)
                    Log.d("MainViewModel", "Updated feedPosts: ${feedPosts.value.map { "${it.id}-${it.sourceType}" }}")
                    errorMessage.value = null
                    shouldReloadFeedPosts.value = false
                    isOfflineMode.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MainViewModel", "Load feed posts failed: ${e.message}", e)
                    val cachedPosts = imagePostDao.getAllPosts().map { it.toImagePost() }
                    Log.d("MainViewModel", "Loaded ${cachedPosts.size} posts from database due to error")
                    if (cachedPosts.isNotEmpty()) {
                        feedPosts.value = applyFiltersAndSort(cachedPosts)
                        errorMessage.value = "Офлайн-режим: загружены сохранённые посты"
                        isOfflineMode.value = true
                    } else {
                        errorMessage.value = "Нет сети и сохранённых данных: ${e.message}"
                        isOfflineMode.value = true
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
            }
        }
    }

    fun loadVideos(append: Boolean = false) {
        if (isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            try {
                val existingFavorites = videoPostDao.getFavoriteVideos().associateBy { it.id }
                val currentPage = if (append) page.value + 1 else 1
                val response = shortiesClient.fetchVideos(context, currentPage)
                Log.d("MainViewModel", "Shorties Response: $response")

                val filteredVideos = response.filter { video ->
                    video.tags.split(" ").none { tag -> tag in blockedTags }
                }

                withContext(Dispatchers.Main) {
                    val videosToInsert = filteredVideos.map { video ->
                        val existingFavorite = existingFavorites[video.id]
                        if (existingFavorite != null && existingFavorite.isFavorited) {
                            video.copy(isFavorited = true)
                        } else {
                            video
                        }
                    }
                    videoPostDao.insertVideos(videosToInsert)
                    Log.d("MainViewModel", "Saved ${filteredVideos.size} videos to database")

                    val updatedVideos: List<VideoPost> = if (append) {
                        val combinedVideos = videos.value + filteredVideos
                        combinedVideos.distinctBy { it.id }
                    } else {
                        filteredVideos
                    }
                    videos.value = updatedVideos
                    if (filteredVideos.isNotEmpty() && append) page.value = currentPage
                    errorMessage.value = null
                    shouldReloadPosts.value = false
                    isOfflineMode.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MainViewModel", "Load videos failed: ${e.message}", e)
                    val cachedVideos = videoPostDao.getAllVideos()
                    if (cachedVideos.isNotEmpty()) {
                        videos.value = cachedVideos
                        errorMessage.value = "Офлайн-режим: загружены сохранённые видео"
                        isOfflineMode.value = true
                    } else {
                        errorMessage.value = "Нет сети и сохранённых видео: ${e.message}"
                    }
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun toggleFavorite(postId: Long, isFavorited: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                imagePostDao.updateFavoriteStatus(postId, isFavorited)
                withContext(Dispatchers.Main) {
                    val updatedFeedPosts = feedPosts.value.map { post ->
                        if (post.id == postId) post.copy(isFavorited = isFavorited) else post
                    }
                    feedPosts.value = applyFiltersAndSort(updatedFeedPosts)

                    val updatedPosts = posts.value.map { post ->
                        if (post.id == postId) post.copy(isFavorited = isFavorited) else post
                    }
                    posts.value = applyFiltersAndSort(updatedPosts)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to toggle favorite: ${e.message}")
            }
        }
    }

    fun toggleVideoFavorite(videoId: Int, isFavorited: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                videoPostDao.updateFavoriteStatus(videoId, isFavorited)
                withContext(Dispatchers.Main) {
                    val updatedVideos = videos.value.map { video ->
                        if (video.id == videoId) video.copy(isFavorited = isFavorited) else video
                    }
                    videos.value = updatedVideos
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to toggle video favorite: ${e.message}")
            }
        }
    }

    fun applyFiltersAndSort(posts: List<ImagePost>): List<ImagePost> {
        var result = posts

        result = result.distinctBy { "${it.id}-${it.sourceType}" }

        if (hideDuplicates.value) {
            result = result.distinctBy { it.effectiveFileUrl }
        }

        minWidth.value?.let { min ->
            result = result.filter { it.width != null && it.width >= min }
        }

        result = when (sortOrder.value) {
            SortOrder.NEWEST -> result.sortedByDescending { it.id }
            SortOrder.OLDEST -> result.sortedBy { it.id }
            SortOrder.POPULAR -> result.sortedByDescending { it.score ?: 0 }
        }

        return result
    }

    fun saveBlockedTags() {
        with(sharedPreferences.edit()) {
            putString("blockedTags", Gson().toJson(blockedTags))
            apply()
        }
    }

    fun saveSearchHistory() {
        with(sharedPreferences.edit()) {
            putString("searchHistory", Gson().toJson(searchHistory))
            apply()
        }
    }

    fun saveFavoriteTags() {
        with(sharedPreferences.edit()) {
            putString("favoriteTags", Gson().toJson(favoriteTags))
            apply()
        }
    }

    // Проверка, лайкнул ли пользователь видео
    fun isVideoLiked(videoId: Int): Boolean {
        return likedVideos.contains(videoId)
    }

    // Добавление лайка для видео
    fun likeVideo(videoId: Int) {
        likedVideos.add(videoId)
        saveVideoRelatedData()
    }

    // Удаление лайка для видео
    fun unlikeVideo(videoId: Int) {
        likedVideos.remove(videoId)
        saveVideoRelatedData()
    }

    // Добавление видео в историю просмотров
    fun addToWatchHistory(videoId: Int) {
        watchHistory.add(videoId)
        saveVideoRelatedData()
    }

    fun releaseExoPlayerPool() {
        exoPlayerPool.values.forEach { player ->
            player.release()
            Log.i("MainViewModel", "Released ExoPlayer for ${player.currentMediaItem?.mediaId}")
        }
        exoPlayerPool.clear()
        currentExoPlayer = null
        Log.i("MainViewModel", "ExoPlayer pool cleared")
    }

    override fun onCleared() {
        releaseExoPlayerPool()
        saveVideoRelatedData() // Сохранение данных перед очисткой
        super.onCleared()
    }

    fun getBooruDisplayName(booru: String): String {
        return booruCustomNames[booru] ?: booru.substringAfter("//").substringBefore("/")
    }

    fun renameBooru(booru: String, newName: String) {
        if (newName.isNotBlank()) {
            booruCustomNames[booru] = newName
            // Save to SharedPreferences
            with(sharedPreferences.edit()) {
                putString("booruCustomNames", Gson().toJson(booruCustomNames))
                apply()
            }
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}