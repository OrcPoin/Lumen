package com.yourcompany.booru.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.JsonElement
import com.yourcompany.booru.data.VideoPost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object PornhubShortiesClient {
    private const val BASE_URL = "https://www.pornhub.com/shorties?page="

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchVideos(context: Context, page: Int = 1): List<VideoPost> {
        val url = "$BASE_URL$page"
        println("Fetching Shorties URL via WebView: $url")

        return try {
            withTimeout(10000) { // Таймаут 10 секунд
                withContext(Dispatchers.Main) {
                    val deferred = CompletableDeferred<List<VideoPost>>()
                    val videoList = mutableListOf<VideoPost>()
                    val webView = WebView(context)
                    val handler = Handler(Looper.getMainLooper())

                    // Настройки WebView
                    webView.settings.javaScriptEnabled = true
                    webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    webView.settings.domStorageEnabled = true
                    webView.settings.loadWithOverviewMode = true
                    webView.settings.useWideViewPort = true
                    android.webkit.WebView.setWebContentsDebuggingEnabled(true) // Для отладки в chrome://inspect

                    webView.addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onData(json: String) {
                            println("Received JSON from WebView: ${json.take(500)}")
                            try {
                                val jsonArray = com.google.gson.JsonParser.parseString(json).asJsonArray
                                jsonArray.forEachIndexed { index, element ->
                                    val videoObj = element.asJsonObject
                                    val title = videoObj.get("videoTitle")?.asString ?: "Short Video $index"
                                    val mediaDefinitions = videoObj.get("mediaDefinitions")?.asJsonArray

                                    val videoUrl = mediaDefinitions?.let { defs ->
                                        val definitions = defs.mapNotNull { it as? JsonElement }.mapNotNull { it.asJsonObject }
                                        definitions.find { it.get("quality")?.asString == "720" }?.get("videoUrl")?.asString
                                            ?: definitions.firstOrNull()?.get("videoUrl")?.asString
                                            ?: ""
                                    } ?: ""

                                    if (videoUrl.isNotBlank()) {
                                        videoList.add(
                                            VideoPost(
                                                id = videoObj.get("videoId")?.asLong?.toInt() ?: (videoUrl.hashCode() + index),
                                                videoUrl = videoUrl,
                                                title = title,
                                                tags = title.split(" ").filter { it.isNotBlank() }.joinToString(" "),
                                                sourceType = "pornhub_shorties"
                                            )
                                        )
                                    }
                                }
                                println("Extracted ${videoList.size} videos: ${videoList.joinToString { it.videoUrl }}")
                                deferred.complete(videoList)
                            } catch (e: Exception) {
                                println("Error parsing WebView JSON: ${e.message}")
                                deferred.complete(emptyList())
                            }
                        }
                    }, "Android")

                    webView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            println("WebView: Page finished loading: $url")
                            val script = """
                                console.log('Page finished loading');
                                if (typeof JSON_SHORTIES !== 'undefined') {
                                    console.log('JSON_SHORTIES found:', JSON_SHORTIES);
                                    Android.onData(JSON.stringify(JSON_SHORTIES));
                                } else {
                                    console.log('JSON_SHORTIES not found, waiting...');
                                    setTimeout(() => {
                                        if (typeof JSON_SHORTIES !== 'undefined') {
                                            console.log('JSON_SHORTIES found after timeout:', JSON_SHORTIES);
                                            Android.onData(JSON.stringify(JSON_SHORTIES));
                                        } else {
                                            console.log('JSON_SHORTIES still not found');
                                            Android.onData('[]');
                                        }
                                    }, 2000);
                                }
                            """.trimIndent()
                            view?.evaluateJavascript(script, null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            println("WebView Error: $errorCode - $description ($failingUrl)")
                            deferred.complete(emptyList())
                        }
                    }

                    webView.loadUrl(url)
                    deferred.invokeOnCompletion {
                        handler.post { webView.destroy() }
                    }
                    deferred.await()
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("WebView timeout exceeded")
            emptyList()
        } catch (e: Exception) {
            println("WebView failed: ${e.message}")
            emptyList()
        }
    }
}