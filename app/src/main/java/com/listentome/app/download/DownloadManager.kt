package com.listentome.app.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class EpisodeDownloadManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private fun downloadsDir(feedId: Long): File {
        val dir = File(context.filesDir, "downloads/$feedId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileFor(feedId: Long, episodeId: Long): File =
        File(downloadsDir(feedId), "$episodeId.audio")

    suspend fun download(feedId: Long, episodeId: Long, audioUrl: String): File =
        withContext(Dispatchers.IO) {
            val destination = fileFor(feedId, episodeId)
            val request = Request.Builder().url(audioUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download episode: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val tempFile = File(destination.parentFile, "${destination.name}.tmp")
                tempFile.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
                if (destination.exists()) destination.delete()
                tempFile.renameTo(destination)
            }
            destination
        }

    suspend fun delete(feedId: Long, episodeId: Long) = withContext(Dispatchers.IO) {
        fileFor(feedId, episodeId).takeIf { it.exists() }?.delete()
        Unit
    }
}
