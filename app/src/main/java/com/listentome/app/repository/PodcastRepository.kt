package com.listentome.app.repository

import android.content.Context
import com.listentome.app.data.AppDatabase
import com.listentome.app.data.DownloadState
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.download.EpisodeDownloadManager
import com.listentome.app.network.NetworkMonitor
import com.listentome.app.network.RssParser
import com.listentome.app.opml.OpmlOutline
import com.listentome.app.opml.OpmlParser
import com.listentome.app.opml.OpmlWriter
import com.listentome.app.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

data class OpmlImportResult(val added: Int, val skipped: Int, val failed: Int)

class PodcastRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(context)
    private val feedDao = db.feedDao()
    private val episodeDao = db.episodeDao()
    private val downloadManager = EpisodeDownloadManager(context)
    private val httpClient = OkHttpClient()
    private val appSettings = AppSettings.get(context)

    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    /** Fraction complete (0f..1f) per episode id, for episodes currently downloading. */
    val downloadProgress: StateFlow<Map<Long, Float>> = _downloadProgress.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Recover episodes left stuck in DOWNLOADING by a process death or a previous
        // version's bug where navigating away cancelled the in-flight download.
        ioScope.launch { episodeDao.resetStuckDownloads() }
    }

    fun observeFeeds(): Flow<List<Feed>> = feedDao.observeAll()

    fun observeDownloadedEpisodes(): Flow<List<Episode>> = episodeDao.observeDownloaded()

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
        if (isEligibleForDownload(episode)) {
            downloadEpisode(episode)
        }
    }

    private fun isEligibleForDownload(episode: Episode): Boolean =
        (episode.downloadState == DownloadState.NOT_DOWNLOADED || episode.downloadState == DownloadState.FAILED) &&
            episode.audioUrl.isNotBlank()

    suspend fun removeFromQueue(episodeId: Long) = withContext(Dispatchers.IO) {
        episodeDao.setQueuePosition(episodeId, null)
    }

    suspend fun markAsPlayed(episodeId: Long) = withContext(Dispatchers.IO) {
        episodeDao.markAsPlayed(episodeId)
        maybeAutoDeletePlayed(episodeId)
    }

    /** Deletes the episode's download if "auto-delete played episodes" is enabled and it's downloaded. */
    private suspend fun maybeAutoDeletePlayed(episodeId: Long) {
        if (!appSettings.autoDeletePlayedEnabled.value) return
        val episode = episodeDao.getById(episodeId) ?: return
        if (episode.downloadState == DownloadState.DOWNLOADED) {
            deleteDownload(episode)
        }
    }

    /** Persists a full drag-and-drop reorder of the queue, in the given order. */
    suspend fun reorderQueue(orderedEpisodeIds: List<Long>) = withContext(Dispatchers.IO) {
        orderedEpisodeIds.forEachIndexed { index, episodeId ->
            episodeDao.setQueuePosition(episodeId, (index + 1).toLong())
        }
    }

    /** Persists a manual drag-and-drop reorder of a podcast's episode list, in the given order. */
    suspend fun reorderEpisodes(orderedEpisodeIds: List<Long>) = withContext(Dispatchers.IO) {
        orderedEpisodeIds.forEachIndexed { index, episodeId ->
            episodeDao.setManualSortOrder(episodeId, (index + 1).toLong())
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
        val nextSortOrder = (feedDao.getMaxSortOrder() ?: 0) + 1
        val feedId = feedDao.insert(
            Feed(
                url = normalizedUrl,
                title = parsed.title,
                description = parsed.description,
                imageUrl = parsed.imageUrl,
                lastRefreshedAt = System.currentTimeMillis(),
                keepLatestCount = appSettings.defaultKeepLatestCount.value,
                sortOrder = nextSortOrder
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

    /** Persists a full drag-and-drop reorder of the podcast list, in the given order. */
    suspend fun reorderFeeds(orderedFeedIds: List<Long>) = withContext(Dispatchers.IO) {
        orderedFeedIds.forEachIndexed { index, feedId ->
            feedDao.setSortOrder(feedId, (index + 1).toLong())
        }
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
        feeds.forEach { feed ->
            try {
                refreshFeed(feed.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One feed failing to fetch/parse shouldn't stop the rest from refreshing.
            }
        }
    }

    /** Downloads the latest N episodes for a feed (per its keepLatestCount) and prunes older downloads. */
    private suspend fun enforceRetention(feedId: Long) {
        val feed = feedDao.getById(feedId) ?: return
        if (feed.keepLatestCount <= 0) return
        val episodes = episodeDao.getByFeedSortedDesc(feedId)
        val toKeep = episodes.take(feed.keepLatestCount)
        val toPrune = episodes.drop(feed.keepLatestCount)

        toKeep.forEach { episode ->
            if (isEligibleForDownload(episode)) {
                downloadEpisode(episode)
            }
        }
        toPrune.forEach { episode ->
            if (episode.downloadState == DownloadState.DOWNLOADED && episode.queuePosition == null) {
                deleteDownload(episode)
            }
        }
    }

    /**
     * Runs the whole download non-cancellably: navigating away from the screen that started
     * it must not cancel the in-flight transfer and leave the episode stuck in DOWNLOADING.
     */
    suspend fun downloadEpisode(episode: Episode) = withContext(Dispatchers.IO + NonCancellable) {
        if (appSettings.wifiOnlyDownloads.value && !NetworkMonitor.isOnWifi(appContext)) {
            return@withContext
        }
        try {
            episodeDao.updateDownloadState(episode.id, DownloadState.DOWNLOADING, null)
            _downloadProgress.update { it + (episode.id to 0f) }
            val file = downloadManager.download(episode.feedId, episode.id, episode.audioUrl) { bytesRead, totalBytes ->
                if (totalBytes > 0) {
                    _downloadProgress.update { it + (episode.id to (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)) }
                }
            }
            episodeDao.updateDownloadState(episode.id, DownloadState.DOWNLOADED, file.absolutePath)
            if (appSettings.autoplayQueueEnabled.value && episode.queuePosition == null) {
                val nextPosition = (episodeDao.getMaxQueuePosition() ?: 0) + 1
                episodeDao.setQueuePosition(episode.id, nextPosition)
            }
            enforceGlobalStorageCap()
        } catch (e: Exception) {
            episodeDao.updateDownloadState(episode.id, DownloadState.FAILED, null)
        } finally {
            _downloadProgress.update { it - episode.id }
        }
    }

    /** Deletes oldest-published, non-queued downloads until total download storage is under the configured cap. */
    private suspend fun enforceGlobalStorageCap() {
        val capBytes = appSettings.maxDownloadStorageBytes.value
        if (capBytes <= 0) return

        val candidates = episodeDao.getEvictionCandidates().toMutableList()
        var totalBytes = episodeDao.observeDownloaded().first().sumOf { episode ->
            episode.localFilePath?.let { File(it).takeIf(File::exists)?.length() } ?: 0L
        }

        while (totalBytes > capBytes && candidates.isNotEmpty()) {
            val oldest = candidates.removeAt(0)
            val size = oldest.localFilePath?.let { File(it).takeIf(File::exists)?.length() } ?: 0L
            deleteDownload(oldest)
            totalBytes -= size
        }
    }

    suspend fun deleteDownload(episode: Episode) = withContext(Dispatchers.IO) {
        downloadManager.delete(episode.feedId, episode.id)
        episodeDao.updateDownloadState(episode.id, DownloadState.NOT_DOWNLOADED, null)
    }

    suspend fun updatePlaybackProgress(episodeId: Long, positionMs: Long, isFinished: Boolean) =
        withContext(Dispatchers.IO) {
            episodeDao.updateProgress(episodeId, positionMs, isFinished)
            if (isFinished) maybeAutoDeletePlayed(episodeId)
        }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        OpmlWriter.write(feedDao.observeAll().first())
    }

    suspend fun parseOpml(input: InputStream): List<OpmlOutline> = withContext(Dispatchers.IO) {
        input.use { OpmlParser.parse(it) }
    }

    suspend fun importOpml(outlines: List<OpmlOutline>): OpmlImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        var failed = 0
        outlines.forEach { outline ->
            val normalizedUrl = outline.xmlUrl.trim()
            if (feedDao.getByUrl(normalizedUrl) != null) {
                skipped++
                return@forEach
            }
            try {
                addFeed(normalizedUrl)
                added++
            } catch (e: Exception) {
                failed++
            }
        }
        OpmlImportResult(added = added, skipped = skipped, failed = failed)
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
