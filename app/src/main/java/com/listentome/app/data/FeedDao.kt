package com.listentome.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Feed>>

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    fun observeById(feedId: Long): Flow<Feed?>

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    suspend fun getById(feedId: Long): Feed?

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): Feed?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feed: Feed): Long

    @Update
    suspend fun update(feed: Feed)

    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun delete(feedId: Long)

    @Query("UPDATE feeds SET lastRefreshedAt = :timestamp WHERE id = :feedId")
    suspend fun markRefreshed(feedId: Long, timestamp: Long)

    @Query("UPDATE feeds SET keepLatestCount = :count WHERE id = :feedId")
    suspend fun updateKeepLatestCount(feedId: Long, count: Int)
}
