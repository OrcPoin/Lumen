package com.yourcompany.booru.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_posts")
data class ImagePostEntity(
    @PrimaryKey val id: Long, // Changed from Int to Long to match the database schema
    val fileUrl: String?,
    val previewUrl: String?,
    val image: String?,
    val directory: Int?,
    val tags: String,
    val tagsString: String?,
    val width: Int?,
    val height: Int?,
    val score: Int?,
    val createdAt: String?,
    val isFavorited: Boolean,
    val sourceType: String? = null
)

fun ImagePostEntity.toImagePost(): ImagePost {
    return ImagePost(
        id = id, // No conversion needed since both are Long
        fileUrl = fileUrl,
        previewUrl = previewUrl,
        image = image,
        directory = directory,
        tags = tags,
        tagsString = tagsString,
        width = width,
        height = height,
        score = score,
        createdAt = createdAt,
        isFavorited = isFavorited,
        sourceType = sourceType // Include sourceType
    )
}