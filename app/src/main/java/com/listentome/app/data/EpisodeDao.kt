package com.listentome.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun observeByFeed(feedId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY publishedAt DESC LIMIT :limit")
    fun observeByFeedLimited(feedId: Long, limit: Int): Flow<List<Episode>>

    @Query("SELECT COUNT(*) FROM episodes WHERE feedId = :feedId")
    fun observeCountByFeed(feedId: Long): Flow<Int>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun observeById(episodeId: Long): Flow<Episode?>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getById(episodeId: Long): Episode?

    @Query("SELECT * FROM episodes WHERE feedId = :feedId AND guid = :guid LIMIT 1")
    suspend fun getByGuid(feedId: Long, guid: String): Episode?

    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY publishedAt DESC")
    suspend fun getByFeedSortedDesc(feedId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE downloadState = 'DOWNLOADED'")
    fun observeDownloaded(): Flow<List<Episode>>

    /** Downloaded, not-queued, not-partially-played episodes ordered oldest-published-first, for global storage cap eviction. */
    @Query("""
        SELECT * FROM episodes
        WHERE downloadState = 'DOWNLOADED' AND queuePosition IS NULL
        AND (playbackPositionMs = 0 OR isFinished = 1)
        ORDER BY publishedAt ASC
    """)
    suspend fun getEvictionCandidates(): List<Episode>

    @Query("SELECT * FROM episodes WHERE queuePosition IS NOT NULL ORDER BY queuePosition ASC")
    fun observeQueue(): Flow<List<Episode>>

    @Query("SELECT MAX(queuePosition) FROM episodes")
    suspend fun getMaxQueuePosition(): Long?

    @Query("UPDATE episodes SET queuePosition = :position WHERE id = :episodeId")
    suspend fun setQueuePosition(episodeId: Long, position: Long?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(episode: Episode): Long

    @Update
    suspend fun update(episode: Episode)

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs, isFinished = :isFinished WHERE id = :episodeId")
    suspend fun updateProgress(episodeId: Long, positionMs: Long, isFinished: Boolean)

    @Query("UPDATE episodes SET isFinished = 1 WHERE id = :episodeId")
    suspend fun markAsPlayed(episodeId: Long)

    @Query("UPDATE episodes SET downloadState = :state, localFilePath = :path WHERE id = :episodeId")
    suspend fun updateDownloadState(episodeId: Long, state: DownloadState, path: String?)
}
