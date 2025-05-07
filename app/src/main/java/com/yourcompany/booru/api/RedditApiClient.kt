package com.yourcompany.booru.api

import com.yourcompany.booru.data.ImagePost
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

object RedditApiClient {
    private const val BASE_URL = "https://www.reddit.com/r/"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val lastAfterMap = mutableMapOf<String, String?>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun resetPagination(subreddit: String) {
        lastAfterMap[subreddit] = null
    }

    suspend fun fetchPosts(subreddit: String = "nsfw", query: String = "", page: Int = 1): List<ImagePost> {
        val lastAfter = lastAfterMap[subreddit]
        val searchUrl = if (query.isEmpty()) {
            "$BASE_URL$subreddit/hot.json?limit=100${if (lastAfter != null) "&after=$lastAfter" else ""}"
        } else {
            "$BASE_URL$subreddit/search.json?q=$query&limit=100${if (lastAfter != null) "&after=$lastAfter" else ""}"
        }

        return try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyList()

            val json = JSONObject(responseBody)
            val data = json.getJSONObject("data")
            lastAfterMap[subreddit] = data.optString("after", null)
            val children = data.getJSONArray("children")

            val posts = mutableListOf<ImagePost>()
            
            for (i in 0 until children.length()) {
                val post = children.getJSONObject(i).getJSONObject("data")
                val url = post.getString("url")
                
                // Skip non-image posts
                if (!url.endsWith(".jpg") && !url.endsWith(".jpeg") && !url.endsWith(".png") && !url.endsWith(".gif")) {
                    continue
                }

                val title = post.getString("title")
                val tags = title.split(" ").filter { it.isNotBlank() }
                val createdUtc = post.optLong("created_utc", 0) * 1000
                val createdAt = if (createdUtc > 0) {
                    dateFormat.format(Date(createdUtc))
                } else null
                
                posts.add(
                    ImagePost(
                        id = post.getString("id").hashCode().toLong(),
                        fileUrl = url,
                        previewUrl = url,
                        image = null,
                        directory = null,
                        tags = tags.joinToString(" "),
                        tagsString = tags.joinToString(" "),
                        width = null,
                        height = null,
                        score = post.optInt("score", 0),
                        createdAt = createdAt,
                        isFavorited = false,
                        sourceType = "reddit"
                    )
                )
            }
            
            posts
        } catch (e: Exception) {
            Log.e("RedditApiClient", "Error fetching posts: ${e.message}", e)
            emptyList()
        }
    }
} 