package com.example.data.media

import android.util.Log
import com.example.data.model.ExtractedYouTubeStream
import com.example.data.model.MusicTrack
import com.example.data.model.VideoQualityOption
import com.example.data.model.YouTubeCommentItem
import com.example.data.model.YouTubeVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

private class PrismOkHttpDownloader(
    val client: OkHttpClient,
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val okHeadersBuilder = Headers.Builder()
        okHeadersBuilder.add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
        )
        for ((key, valueList) in headers) {
            okHeadersBuilder.removeAll(key)
            for (value in valueList) {
                okHeadersBuilder.add(key, value)
            }
        }

        val requestBody = when {
            dataToSend != null -> {
                val contentType = headers["Content-Type"]?.firstOrNull() ?: "application/json; charset=utf-8"
                dataToSend.toRequestBody(contentType.toMediaTypeOrNull())
            }
            httpMethod.equals("POST", ignoreCase = true) -> ByteArray(0).toRequestBody(null)
            else -> null
        }

        val okRequest = okhttp3.Request.Builder()
            .url(url)
            .headers(okHeadersBuilder.build())
            .method(httpMethod, requestBody)
            .build()

        client.newCall(okRequest).execute().use { okResponse ->
            if (okResponse.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            val bodyString = okResponse.body?.string() ?: ""
            return Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                bodyString,
                okResponse.request.url.toString(),
            )
        }
    }
}

class NewPipeRepository {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(
                        PrismOkHttpDownloader(httpClient),
                        Localization.DEFAULT,
                        ContentCountry.DEFAULT,
                    )
                    initialized = true
                }
            }
        }
    }

    suspend fun fetchTrendingVideos(): Result<List<YouTubeVideoItem>> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val kiosk = ServiceList.YouTube.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            val items = kiosk.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
                .filter { it.url.isNotBlank() }
            if (items.isNotEmpty()) {
                Result.success(items)
            } else {
                searchVideos("trending music tech design 2025")
            }
        } catch (e: Exception) {
            Log.w("NewPipeRepo", "Kiosk fetch failed, falling back to search", e)
            searchVideos("trending videos")
        }
    }

    suspend fun searchVideos(query: String): Result<List<YouTubeVideoItem>> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val trimmed = query.trim().ifBlank { "Formula 1 official trailer" }
            val extractor = ServiceList.YouTube.getSearchExtractor(trimmed)
            extractor.fetchPage()
            val items = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
                .filter { it.url.isNotBlank() }
            Result.success(items)
        } catch (e: Exception) {
            Log.e("NewPipeRepo", "Video search failed for $query", e)
            Result.failure(e)
        }
    }

    suspend fun searchMusicTracks(query: String): Result<List<MusicTrack>> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val trimmed = query.trim().ifBlank { "top pop hits official audio" }
            val extractor = try {
                ServiceList.YouTube.getSearchExtractor(trimmed, listOf("music_songs"), "")
            } catch (_: Exception) {
                ServiceList.YouTube.getSearchExtractor("$trimmed official audio")
            }
            extractor.fetchPage()
            val items = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val videoId = extractVideoId(item.url)
                    MusicTrack(
                        videoId = videoId,
                        title = item.name.orEmpty().ifBlank { "Untitled Track" },
                        artist = cleanArtistName(item.uploaderName.orEmpty()),
                        album = "Single",
                        thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url
                            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        durationSec = item.duration.coerceAtLeast(0L),
                    )
                }
                .filter { it.videoId.isNotBlank() }
            Result.success(items)
        } catch (e: Exception) {
            Log.e("NewPipeRepo", "Music search failed for $query", e)
            Result.failure(e)
        }
    }

    suspend fun extractVideoStream(videoUrl: String): Result<ExtractedYouTubeStream> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "https://www.youtube.com/watch?v=$videoUrl"
            val info = StreamInfo.getInfo(ServiceList.YouTube, fullUrl)

            val muxedQualities = info.videoStreams
                .filter { !it.content.isNullOrBlank() }
                .sortedByDescending { parseResolutionHeight(it.resolution) }
                .distinctBy { it.resolution }
                .map { stream ->
                    VideoQualityOption(
                        resolution = stream.resolution.ifBlank { "HD" },
                        formatLabel = stream.format?.suffix?.uppercase() ?: "MP4",
                        url = stream.content,
                        isVideoOnly = false,
                    )
                }

            val bestAudio = info.audioStreams
                .filter { !it.content.isNullOrBlank() }
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()?.content

            val related = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(15)
                .map { it.toVideoItem() }

            val videoId = extractVideoId(info.url.ifBlank { fullUrl })
            Result.success(
                ExtractedYouTubeStream(
                    videoId = videoId,
                    url = info.url.ifBlank { fullUrl },
                    title = info.name.orEmpty().ifBlank { "YouTube Video" },
                    uploaderName = info.uploaderName.orEmpty().ifBlank { "Creator" },
                    uploaderUrl = info.uploaderUrl.orEmpty(),
                    uploaderAvatarUrl = info.uploaderAvatars.maxByOrNull { it.width }?.url.orEmpty(),
                    subscriberCount = info.uploaderSubscriberCount,
                    thumbnailUrl = info.thumbnails.maxByOrNull { it.width }?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    durationSec = info.duration.coerceAtLeast(0L),
                    viewCount = info.viewCount,
                    likeCount = info.likeCount,
                    description = info.description?.content.orEmpty(),
                    videoQualities = muxedQualities,
                    bestAudioUrl = bestAudio ?: muxedQualities.firstOrNull()?.url,
                    relatedVideos = related,
                ),
            )
        } catch (e: Exception) {
            Log.e("NewPipeRepo", "Stream extraction failed for $videoUrl", e)
            Result.failure(e)
        }
    }

    suspend fun extractDirectAudioUrl(videoIdOrUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val fullUrl = if (videoIdOrUrl.startsWith("http")) {
                videoIdOrUrl
            } else {
                "https://www.youtube.com/watch?v=$videoIdOrUrl"
            }
            val info = StreamInfo.getInfo(ServiceList.YouTube, fullUrl)
            val bestAudio = info.audioStreams
                .filter { !it.content.isNullOrBlank() }
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()?.content
                ?: info.videoStreams.firstOrNull { !it.content.isNullOrBlank() }?.content
                ?: return@withContext Result.failure(IllegalStateException("No playable audio stream available"))
            Result.success(bestAudio)
        } catch (e: Exception) {
            Log.e("NewPipeRepo", "Audio extraction failed for $videoIdOrUrl", e)
            Result.failure(e)
        }
    }

    suspend fun fetchComments(videoUrl: String): Result<List<YouTubeCommentItem>> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val commentsInfo = CommentsInfo.getInfo(ServiceList.YouTube, videoUrl)
            val comments = commentsInfo.relatedItems.take(25).map { item ->
                YouTubeCommentItem(
                    author = item.uploaderName.orEmpty().ifBlank { "Viewer" },
                    avatarUrl = item.uploaderAvatars.maxByOrNull { it.width }?.url.orEmpty(),
                    text = item.commentText?.content.orEmpty(),
                    likeCount = item.likeCount,
                    publishedTime = item.textualUploadDate.orEmpty(),
                )
            }
            Result.success(comments)
        } catch (e: Exception) {
            Log.w("NewPipeRepo", "Comments fetch failed", e)
            Result.failure(e)
        }
    }

    private fun StreamInfoItem.toVideoItem(): YouTubeVideoItem {
        val videoId = extractVideoId(url.orEmpty())
        return YouTubeVideoItem(
            id = videoId,
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Untitled Video" },
            uploaderName = uploaderName.orEmpty().ifBlank { "Creator" },
            uploaderUrl = uploaderUrl.orEmpty(),
            uploaderAvatarUrl = uploaderAvatars.maxByOrNull { it.width }?.url.orEmpty(),
            thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            durationSec = duration.coerceAtLeast(0L),
            viewCount = viewCount,
            uploadDateText = textualUploadDate.orEmpty(),
        )
    }

    private fun extractVideoId(url: String): String {
        val regex = Regex("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_\\-]{6,20})")
        return regex.find(url)?.groupValues?.getOrNull(1)
            ?: url.substringAfterLast("/").take(24).ifBlank { "vid_${url.hashCode()}" }
    }

    private fun parseResolutionHeight(res: String?): Int {
        return Regex("(\\d+)").find(res.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun cleanArtistName(raw: String): String {
        return raw.removeSuffix(" - Topic").trim().ifBlank { "Unknown Artist" }
    }
}
