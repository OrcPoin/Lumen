package com.yourcompany.booru.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoPostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoPost>)

    @Query("SELECT * FROM video_posts")
    suspend fun getAllVideos(): List<VideoPost>

    @Query("SELECT * FROM video_posts WHERE isFavorited = 1")
    suspend fun getFavoriteVideos(): List<VideoPost>

    @Query("UPDATE video_posts SET isFavorited = :isFavorited WHERE id = :videoId")
    suspend fun updateFavoriteStatus(videoId: Int, isFavorited: Boolean)
}