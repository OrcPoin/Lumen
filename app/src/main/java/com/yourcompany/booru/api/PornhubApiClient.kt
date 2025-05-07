package com.yourcompany.booru.api

import com.yourcompany.booru.data.ImagePost
import org.jsoup.Jsoup
import java.io.IOException

object PornhubApiClient {
    private const val BASE_URL = "https://www.pornhub.com/albums?search="

    suspend fun fetchPosts(query: String = "", page: Int = 1): List<ImagePost> {
        val searchUrl = "$BASE_URL${query}&page=$page"
        println("Fetching URL: $searchUrl")
        return try {
            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.pornhub.com")
                .timeout(10000)
                .get()

            // Новый селектор для блоков с изображениями
            val imageElements = doc.select("div.photoAlbumListBlock.js_lazy_bkg")
            if (imageElements.isEmpty()) {
                println("No albums found. HTML sample: ${doc.html().substring(0, minOf(2000, doc.html().length))}")
                return emptyList()
            } else {
                println("Found ${imageElements.size} albums")
            }

            imageElements.mapIndexed { index, element ->
                val imageUrl = element.attr("data-bkg") // Извлекаем URL из data-bkg
                val tags = element.attr("title").split(" ").filter { it.isNotBlank() } // Теги из title

                ImagePost(
                    id = (imageUrl.hashCode() + index).toLong(), // Convert Int to Long
                    fileUrl = imageUrl,
                    previewUrl = imageUrl,
                    image = null,
                    directory = null,
                    tags = tags.joinToString(" "),
                    tagsString = tags.joinToString(" "),
                    width = null,
                    height = null,
                    score = null,
                    createdAt = null,
                    isFavorited = false,
                    sourceType = "pornhub"
                )
            }
        } catch (e: IOException) {
            println("Error fetching Pornhub posts: ${e.message}")
            emptyList()
        }
    }
}