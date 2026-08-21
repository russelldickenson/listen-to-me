package com.listentome.app.repository

import android.content.Context
import com.listentome.app.data.AppDatabase
import com.listentome.app.data.DownloadState
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.download.EpisodeDownloadManager
import com.listentome.app.network.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class PodcastRepository private constructor(context: Context) {
    private val db = AppDatabase.get(context)
    private val feedDao = db.feedDao()
    private val episodeDao = db.episodeDao()
    private val downloadManager = EpisodeDownloadManager(context)
    private val httpClient = OkHttpClient()

    fun observeFeeds(): Flow<List<Feed>> = feedDao.observeAll()

    fun observeFeed(feedId: Long): Flow<Feed?> = feedDao.observeById(feedId)

    fun observeEpisodes(feedId: Long): Flow<List<Episode>> = episodeDao.observeByFeed(feedId)

    fun observeEpisodes(feedId: Long, limit: Int): Flow<List<Episode>> =
        episodeDao.observeByFeedLimited(feedId, limit)

    fun observeEpisodeCount(feedId: Long): Flow<Int> = episodeDao.observeCountByFeed(feedId)

    fun observeEpisode(episodeId: Long): Flow<Episode?> = episodeDao.observeById(episodeId)

    fun observeQueue(): Flow<List<Episode>> = episodeDao.observeQueue()

    suspend fun addToQueue(episode: Episode) = withContext(Dispatchers.IO) {
        val nextPosition = (episodeDao.getMaxQueuePosition() ?: 0) + 1
        episodeDao.setQueuePosition(episode.id, nextPosition)
        if (episode.downloadState == DownloadState.NOT_DOWNLOADED && episode.audioUrl.isNotBlank()) {
            downloadEpisode(episode)
        }
    }

    suspend fun removeFromQueue(episodeId: Long) = withContext(Dispatchers.IO) {
        episodeDao.setQueuePosition(episodeId, null)
    }

    suspend fun markAsPlayed(episodeId: Long) = withContext(Dispatchers.IO) {
        episodeDao.markAsPlayed(episodeId)
    }

    /** Swaps the queue order of two episodes, used to move an item up/down in the queue. */
    suspend fun swapQueuePositions(episodeId: Long, otherEpisodeId: Long) = withContext(Dispatchers.IO) {
        val a = episodeDao.getById(episodeId) ?: return@withContext
        val b = episodeDao.getById(otherEpisodeId) ?: return@withContext
        val aPosition = a.queuePosition
        val bPosition = b.queuePosition
        if (aPosition != null && bPosition != null) {
            episodeDao.setQueuePosition(a.id, bPosition)
            episodeDao.setQueuePosition(b.id, aPosition)
        }
    }

    /** Fetches and parses a feed without saving anything, so it can be previewed before adding. */
    suspend fun previewFeed(url: String): com.listentome.app.network.ParsedFeed = withContext(Dispatchers.IO) {
        fetchAndParse(url.trim())
    }

    suspend fun addFeed(url: String, prefetched: com.listentome.app.network.ParsedFeed? = null): Feed = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        feedDao.getByUrl(normalizedUrl)?.let { return@withContext it }

        val parsed = prefetched ?: fetchAndParse(normalizedUrl)
        val feedId = feedDao.insert(
            Feed(
                url = normalizedUrl,
                title = parsed.title,
                description = parsed.description,
                imageUrl = parsed.imageUrl,
                lastRefreshedAt = System.currentTimeMillis()
            )
        )
        parsed.items.forEach { item ->
            episodeDao.insert(
                Episode(
                    feedId = feedId,
                    guid = item.guid,
                    title = item.title,
                    description = item.description,
                    audioUrl = item.audioUrl,
                    publishedAt = item.publishedAt,
                    durationSeconds = item.durationSeconds,
                    imageUrl = item.imageUrl
                )
            )
        }
        feedDao.getById(feedId) ?: error("Feed disappeared after insert")
    }

    suspend fun removeFeed(feedId: Long) = withContext(Dispatchers.IO) {
        feedDao.delete(feedId)
    }

    suspend fun updateKeepLatestCount(feedId: Long, count: Int) = withContext(Dispatchers.IO) {
        feedDao.updateKeepLatestCount(feedId, count.coerceAtLeast(0))
    }

    suspend fun refreshFeed(feedId: Long) = withContext(Dispatchers.IO) {
        val feed = feedDao.getById(feedId) ?: return@withContext
        val parsed = fetchAndParse(feed.url)

        parsed.items.forEach { item ->
            val existing = episodeDao.getByGuid(feedId, item.guid)
            if (existing == null) {
                episodeDao.insert(
                    Episode(
                        feedId = feedId,
                        guid = item.guid,
                        title = item.title,
                        description = item.description,
                        audioUrl = item.audioUrl,
                        publishedAt = item.publishedAt,
                        durationSeconds = item.durationSeconds,
                        imageUrl = item.imageUrl
                    )
                )
            } else if (existing.title != item.title || existing.description != item.description || existing.imageUrl != item.imageUrl) {
                episodeDao.update(existing.copy(title = item.title, description = item.description, imageUrl = item.imageUrl))
            }
        }

        feedDao.markRefreshed(feedId, System.currentTimeMillis())
        enforceRetention(feedId)
    }

    suspend fun refreshAllFeeds() = withContext(Dispatchers.IO) {
        val feeds = feedDao.observeAll().first()
        feeds.forEach { feed -> refreshFeed(feed.id) }
    }

    /** Downloads the latest N episodes for a feed (per its keepLatestCount) and prunes older downloads. */
    private suspend fun enforceRetention(feedId: Long) {
        val feed = feedDao.getById(feedId) ?: return
        if (feed.keepLatestCount <= 0) return
        val episodes = episodeDao.getByFeedSortedDesc(feedId)
        val toKeep = episodes.take(feed.keepLatestCount)
        val toPrune = episodes.drop(feed.keepLatestCount)

        toKeep.forEach { episode ->
            if (episode.downloadState == DownloadState.NOT_DOWNLOADED && episode.audioUrl.isNotBlank()) {
                downloadEpisode(episode)
            }
        }
        toPrune.forEach { episode ->
            if (episode.downloadState == DownloadState.DOWNLOADED && episode.queuePosition == null) {
                deleteDownload(episode)
            }
        }
    }

    suspend fun downloadEpisode(episode: Episode) = withContext(Dispatchers.IO) {
        try {
            episodeDao.updateDownloadState(episode.id, DownloadState.DOWNLOADING, null)
            val file = downloadManager.download(episode.feedId, episode.id, episode.audioUrl)
            episodeDao.updateDownloadState(episode.id, DownloadState.DOWNLOADED, file.absolutePath)
        } catch (e: IOException) {
            episodeDao.updateDownloadState(episode.id, DownloadState.NOT_DOWNLOADED, null)
        }
    }

    suspend fun deleteDownload(episode: Episode) = withContext(Dispatchers.IO) {
        downloadManager.delete(episode.feedId, episode.id)
        episodeDao.updateDownloadState(episode.id, DownloadState.NOT_DOWNLOADED, null)
    }

    suspend fun updatePlaybackProgress(episodeId: Long, positionMs: Long, isFinished: Boolean) =
        withContext(Dispatchers.IO) {
            episodeDao.updateProgress(episodeId, positionMs, isFinished)
        }

    private fun fetchAndParse(url: String): com.listentome.app.network.ParsedFeed {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch feed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty feed response")
            return body.byteStream().use { RssParser.parse(it) }
        }
    }

    companion object {
        @Volatile
        private var instance: PodcastRepository? = null

        fun get(context: Context): PodcastRepository =
            instance ?: synchronized(this) {
                instance ?: PodcastRepository(context.applicationContext).also { instance = it }
            }
    }
}
