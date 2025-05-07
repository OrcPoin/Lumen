package com.yourcompany.booru.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImagePostDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPosts(posts: List<ImagePostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: ImagePostEntity)

    @Query("SELECT * FROM image_posts")
    suspend fun getAllPosts(): List<ImagePostEntity>

    @Query("SELECT * FROM image_posts WHERE isFavorited = 1")
    fun getFavoritePosts(): Flow<List<ImagePostEntity>>

    @Query("UPDATE image_posts SET isFavorited = :isFavorited WHERE id = :postId")
    suspend fun updateFavoriteStatus(postId: Long, isFavorited: Boolean) // Changed from Int to Long

    @Query("DELETE FROM image_posts")
    suspend fun clearAllPosts()
}