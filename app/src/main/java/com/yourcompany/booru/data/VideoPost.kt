// java/com/yourcompany/booru/data/VideoPost.kt
package com.yourcompany.booru.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_posts")
data class VideoPost(
    @PrimaryKey val id: Int,
    val videoUrl: String,
    val title: String,
    val tags: String,
    val sourceType: String,
    val isFavorited: Boolean = false
)