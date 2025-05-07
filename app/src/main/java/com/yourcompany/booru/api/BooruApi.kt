package com.yourcompany.booru.api

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.yourcompany.booru.data.ImagePost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface BooruApi {
    @GET("posts.json")
    suspend fun getDanbooruPosts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("page") page: Int
    ): Response<List<ImagePost>>

    @GET("index.php?page=dapi&s=post&q=index")
    suspend fun getDanbooruOldPosts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("pid") page: Int
    ): Response<List<ImagePost>>

    @GET("api/v1/search/images")
    suspend fun getPhilomenaPosts(
        @Query("q") tags: String,
        @Query("per_page") limit: Int,
        @Query("page") page: Int
    ): Response<PhilomenaResponse>

    @GET("api/posts")
    suspend fun getSzurubooruPosts(
        @Query("query") tags: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<List<ImagePost>>

    @GET("index.php?page=dapi&s=post&q=index&json=1")
    suspend fun getGelbooruV02Posts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("pid") page: Int
    ): Response<List<ImagePost>>

    @GET("index.php?page=dapi&s=post&q=index")
    suspend fun getGelbooruV01Posts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("pid") page: Int
    ): Response<List<ImagePost>>

    @GET("post.json")
    suspend fun getMoebooruPosts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("page") page: Int
    ): Response<List<ImagePost>>

    @GET("api/image/list")
    suspend fun getShimmiePosts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("page") page: Int
    ): Response<List<ImagePost>>
}

data class PhilomenaResponse(
    val images: List<ImagePost>
)

object BooruApiClient {
    private var retrofit: Retrofit? = null
    private var currentApi: BooruApi? = null
    private val cookieStore: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://booru.allthefallen.moe/")
            if (original.url.toString().contains("booru.allthefallen.moe")) {
                cookieStore["allthefallen"]?.let { cookie ->
                    requestBuilder.header("Cookie", cookie)
                }
            }
            val request = requestBuilder.build()
            val response = chain.proceed(request)
            if (response.headers("Set-Cookie").isNotEmpty()) {
                val cookies = response.headers("Set-Cookie").joinToString("; ")
                if (request.url.toString().contains("booru.allthefallen.moe")) {
                    cookieStore["allthefallen"] = cookies
                    Log.d("BooruApiClient", "Stored cookies for AllTheFallen: $cookies")
                }
            }
            response
        }
        .build()

    fun setBaseUrl(baseUrl: String) {
        val adjustedBaseUrl = when {
            baseUrl.contains("rule34.booru.org") -> "https://api.rule34.xxx/"
            baseUrl.contains("booru.allthefallen.moe") -> "https://booru.allthefallen.moe/"
            baseUrl.contains("reddit.com") -> baseUrl // Don't trim slash for Reddit
            else -> baseUrl.trimEnd('/')
        }
        retrofit = Retrofit.Builder()
            .baseUrl(adjustedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build()
        currentApi = retrofit!!.create(BooruApi::class.java)
    }

    suspend fun fetchPosts(
        tags: String = "",
        limit: Int = 20,
        page: Int = 0,
        baseUrl: String,
        context: Context? = null
    ): List<ImagePost> {
        // Check if it's a Reddit URL
        if (baseUrl.contains("reddit.com")) {
            val subreddit = baseUrl.substringAfter("reddit.com/r/").substringBefore("/")
            return RedditApiClient.fetchPosts(subreddit, tags, page + 1)
        }

        val api = currentApi ?: throw IllegalStateException("API not initialized. Call setBaseUrl first.")
        val isAllTheFallen = baseUrl.contains("booru.allthefallen.moe")
        val maxAttempts = if (isAllTheFallen) 3 else 1
        val attempts = listOf(
            suspend {
                val response = api.getDanbooruPosts(tags, limit, page)
                handleResponse(response, "Danbooru")
            },
            suspend {
                val response = api.getDanbooruOldPosts(tags, limit, page)
                handleResponse(response, "Danbooru Old")
            },
            suspend {
                val philomenaResponse = api.getPhilomenaPosts(tags, limit, page + 1)
                if (philomenaResponse.isSuccessful) philomenaResponse.body()?.images else {
                    Log.w("BooruApiClient", "Philomena API failed: ${philomenaResponse.code()} ${philomenaResponse.message()} Body: ${philomenaResponse.errorBody()?.string()}")
                    null
                }
            },
            suspend {
                val response = api.getSzurubooruPosts(tags, limit, page * limit)
                handleResponse(response, "Szurubooru")
            },
            suspend {
                val response = api.getGelbooruV02Posts(tags, limit, page)
                handleResponse(response, "Gelbooru v0.2")
            },
            suspend {
                val response = api.getGelbooruV01Posts(tags, limit, page)
                handleResponse(response, "Gelbooru v0.1")
            },
            suspend {
                val response = api.getMoebooruPosts(tags, limit, page)
                handleResponse(response, "Moebooru")
            },
            suspend {
                val response = api.getShimmiePosts(tags, limit, page)
                handleResponse(response, "Shimmie")
            }
        )

        for (attempt in attempts) {
            repeat(maxAttempts) { attemptNum ->
                try {
                    Log.d("BooruApiClient", "Trying API endpoint for $baseUrl, attempt ${attemptNum + 1}")
                    val result = attempt()
                    if (result != null && result.isNotEmpty()) {
                        Log.d("BooruApiClient", "Success: ${result.size} posts fetched")
                        return result.distinctBy { it.id }
                    }
                    if (isAllTheFallen && attemptNum < maxAttempts - 1) {
                        delay(2000)
                    }
                } catch (e: Exception) {
                    Log.w("BooruApiClient", "API attempt failed: ${e.message}", e)
                    if (isAllTheFallen && attemptNum < maxAttempts - 1) {
                        delay(2000)
                    }
                }
            }
        }

        return try {
            Log.d("BooruApiClient", "Falling back to HTML scraping for $baseUrl")
            if (isAllTheFallen) {
                if (context == null) {
                    Log.e("BooruApiClient", "Context is null, cannot use WebView for AllTheFallen")
                    throw IllegalStateException("Context is required for AllTheFallen WebView fallback")
                }
                Log.d("BooruApiClient", "Attempting WebView fallback for AllTheFallen")
                fetchPostsWithWebView(context, baseUrl, tags, limit, page)
            } else {
                scrapeHtmlPosts(baseUrl, tags, limit, page)
            }
        } catch (e: Exception) {
            Log.e("BooruApiClient", "Fallback failed: ${e.message}", e)
            throw Exception("No valid API endpoint found for this Booru")
        }
    }

    private fun <T> handleResponse(response: Response<T>, apiName: String): T? {
        if (response.isSuccessful) {
            return response.body()
        } else {
            Log.w("BooruApiClient", "$apiName API failed: ${response.code()} ${response.message()} Body: ${response.errorBody()?.string()}")
            return null
        }
    }

    private suspend fun scrapeHtmlPosts(baseUrl: String, tags: String, limit: Int, page: Int): List<ImagePost> {
        return withContext(Dispatchers.IO) {
            val url = if (tags.isEmpty()) {
                "$baseUrl/index.php?page=post&s=list&pid=${page * limit}"
            } else {
                "$baseUrl/index.php?page=post&s=list&tags=$tags&pid=${page * limit}"
            }
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
            val maxAttempts = if (baseUrl.contains("booru.allthefallen.moe")) 3 else 1
            var lastException: Exception? = null

            repeat(maxAttempts) { attemptNum ->
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Referer", baseUrl)
                        .apply {
                            cookieStore["allthefallen"]?.let { cookie ->
                                header("Cookie", cookie)
                            }
                        }
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.w("BooruApiClient", "HTML scraping failed with code ${response.code}: ${response.message}")
                        lastException = Exception("HTTP ${response.code}: ${response.message}")
                        return@repeat
                    }
                    val html = response.body?.string() ?: throw Exception("Failed to load HTML page")
                    if (html.contains("Anti-DDoS Flood Protection") || html.contains("cf-browser-verification")) {
                        Log.w("BooruApiClient", "Received Cloudflare verification page")
                        lastException = Exception("Blocked by Anti-DDoS protection")
                        return@repeat
                    }
                    val doc = Jsoup.parse(html)

                    val posts = mutableListOf<ImagePost>()
                    val postLinks = doc.select("a[href*=index.php?page=post&s=view&id]")
                    Log.d("BooruApiClient", "Found ${postLinks.size} post links on page $page")

                    for (link in postLinks.take(limit)) {
                        val postUrl = if (link.attr("href").startsWith("http")) link.attr("href") else "$baseUrl${link.attr("href")}"
                        val postRequest = Request.Builder()
                            .url(postUrl)
                            .header("User-Agent", userAgent)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                            .header("Referer", baseUrl)
                            .apply {
                                cookieStore["allthefallen"]?.let { cookie ->
                                    header("Cookie", cookie)
                                }
                            }
                            .build()
                        val postResponse = client.newCall(postRequest).execute()
                        if (!postResponse.isSuccessful) {
                            Log.w("BooruApiClient", "Post page failed with code ${postResponse.code}")
                            continue
                        }
                        val postHtml = postResponse.body?.string() ?: continue
                        val postDoc = Jsoup.parse(postHtml)

                        val imageUrl = postDoc.select("img#image").attr("src")
                        val id = link.attr("href").substringAfter("id=").toLongOrNull() ?: 0L // Changed to Long
                        val tagElements = postDoc.select("li.tag a[href*=tags=]").map { it.text() }
                        val tagsString = tagElements.joinToString(" ")

                        if (imageUrl.isNotEmpty()) {
                            val fullImageUrl = if (imageUrl.startsWith("http")) imageUrl else "$baseUrl$imageUrl"
                            posts.add(ImagePost(
                                id = id,
                                fileUrl = fullImageUrl,
                                previewUrl = fullImageUrl,
                                image = null,
                                directory = null,
                                tags = tagsString,
                                tagsString = tagsString,
                                width = null,
                                height = null,
                                score = null,
                                createdAt = null,
                                isFavorited = false,
                                sourceType = baseUrl // Added missing parameters
                            ))
                            Log.d("BooruApiClient", "Added post: id=$id, url=$fullImageUrl")
                        }
                    }

                    Log.d("BooruApiClient", "Total posts scraped: ${posts.size}")
                    return@withContext posts.distinctBy { it.id }
                } catch (e: Exception) {
                    Log.w("BooruApiClient", "HTML scraping attempt ${attemptNum + 1} failed: ${e.message}", e)
                    lastException = e
                    if (baseUrl.contains("booru.allthefallen.moe") && attemptNum < maxAttempts - 1) {
                        delay(2000)
                    }
                }
            }

            throw lastException ?: Exception("Failed to scrape HTML posts")
        }
    }

    private suspend fun fetchPostsWithWebView(
        context: Context,
        baseUrl: String,
        tags: String,
        limit: Int,
        page: Int
    ): List<ImagePost> = withContext(Dispatchers.Main) {
        var lastException: Exception? = null
        val maxAttempts = 3

        repeat(maxAttempts) { attemptNum ->
            var webView: WebView? = null
            try {
                return@withContext suspendCoroutine<List<ImagePost>> { continuation ->
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
                    }
                    val url = if (tags.isEmpty()) {
                        "$baseUrl/posts.json?limit=$limit&page=$page"
                    } else {
                        "$baseUrl/posts.json?tags=$tags&limit=$limit&page=$page"
                    }

                    // Sync cookies from cookieStore to WebView
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieStore["allthefallen"]?.let { cookies ->
                        cookies.split(";").forEach { cookie ->
                            cookieManager.setCookie(baseUrl, cookie.trim())
                        }
                        Log.d("BooruApiClient", "Set cookies for WebView: $cookies")
                    }

                    webView?.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            // Sync cookies from WebView back to cookieStore
                            val webViewCookies = cookieManager.getCookie(baseUrl)
                            if (webViewCookies != null) {
                                cookieStore["allthefallen"] = webViewCookies
                                Log.d("BooruApiClient", "Updated cookieStore with WebView cookies: $webViewCookies")
                            }

                            webView?.evaluateJavascript("(function() { return document.body.innerText; })()") { result ->
                                try {
                                    val json = result?.trim('"')?.replace("\\\"", "\"") ?: ""
                                    Log.d("BooruApiClient", "WebView raw response: $json")
                                    if (json.isEmpty() || json.contains("Anti-DDoS Flood Protection") || json.contains("cf-browser-verification")) {
                                        throw Exception("WebView received Anti-DDoS verification page")
                                    }
                                    val posts = Gson().fromJson<List<ImagePost>>(
                                        json,
                                        object : TypeToken<List<ImagePost>>() {}.type
                                    )
                                    Log.d("BooruApiClient", "WebView fetched ${posts.size} posts")
                                    continuation.resume(posts.distinctBy { it.id })
                                    webView?.destroy()
                                } catch (e: Exception) {
                                    Log.e("BooruApiClient", "WebView attempt ${attemptNum + 1} failed: ${e.message}", e)
                                    continuation.resumeWith(Result.failure(e))
                                    webView?.destroy()
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            Log.e("BooruApiClient", "WebView error: $errorCode $description")
                            continuation.resumeWith(Result.failure(Exception("WebView error: $description")))
                            webView?.destroy()
                        }
                    }

                    webView?.post { webView?.loadUrl(url) }
                }
            } catch (e: Exception) {
                lastException = e
                Log.w("BooruApiClient", "WebView attempt ${attemptNum + 1} failed: ${e.message}", e)
                if (attemptNum < maxAttempts - 1) {
                    delay(2000)
                }
            } finally {
                webView?.destroy()
            }
        }

        throw lastException ?: Exception("Failed to fetch posts via WebView after $maxAttempts attempts")
    }
}