package com.yourcompany.booru.data

import com.google.gson.annotations.SerializedName

// Data class for business logic (not a Room entity)
data class ImagePost(
    val id: Long, // Changed from Int to Long to match API and ImagePostEntity
    @SerializedName("file_url") val fileUrl: String?,
    @SerializedName("preview_url") val previewUrl: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("directory") val directory: Int?,
    val tags: String?, // Nullable to match API response
    @SerializedName("tags_string") val tagsString: String?,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val isFavorited: Boolean = false,
    val sourceType: String? = null
) {
    fun getTagsList(): List<String> {
        return (tagsString ?: tags ?: "").split(" ").filter { it.isNotBlank() }
    }

    val effectiveFileUrl: String?
        get() = fileUrl ?: image?.let { "https://tbib.org/images/$directory/$it" }

    val effectivePreviewUrl: String?
        get() = previewUrl ?: effectiveFileUrl
}

fun ImagePost.toEntity(): ImagePostEntity {
    return ImagePostEntity(
        id = id, // No conversion needed since both are Long
        fileUrl = fileUrl,
        previewUrl = previewUrl,
        image = image,
        directory = directory,
        tags = tags ?: "", // Handle nullable tags to match non-nullable tags in ImagePostEntity
        tagsString = tagsString,
        width = width,
        height = height,
        score = score,
        createdAt = createdAt,
        isFavorited = isFavorited,
        sourceType = sourceType
    )
}