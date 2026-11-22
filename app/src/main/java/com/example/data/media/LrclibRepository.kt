package com.example.data.media

import android.util.Log
import com.example.data.model.LyricLine
import com.example.data.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches time-synced LRC karaoke lyrics and plain lyrics from LRCLIB (lrclib.net).
 */
class LrclibRepository(private val client: OkHttpClient) {

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        durationSec: Long = 0L,
    ): LyricsResult = withContext(Dispatchers.IO) {
        val cleanTrack = sanitizeTitle(trackName)
        val cleanArtist = artistName.removeSuffix(" - Topic").trim()

        // 1. Try exact match endpoint first
        val exact = tryExactGet(cleanTrack, cleanArtist, durationSec)
        if (exact != null && (exact.syncedLines.isNotEmpty() || !exact.plainLyrics.isNullOrBlank())) {
            return@withContext exact
        }

        // 2. Fallback to fuzzy search endpoint
        val searched = trySearch(cleanTrack, cleanArtist)
        searched ?: LyricsResult()
    }

    private fun tryExactGet(trackName: String, artistName: String, durationSec: Long): LyricsResult? {
        return try {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", trackName)
                .addQueryParameter("artist_name", artistName)
            if (durationSec > 10) {
                urlBuilder.addQueryParameter("duration", durationSec.toString())
            }
            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "PrismAndroid/1.0 (https://github.com/prism-suite)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseLrclibJson(JSONObject(body))
            }
        } catch (e: Exception) {
            Log.w("LrclibRepo", "Exact lyrics fetch failed", e)
            null
        }
    }

    private fun trySearch(trackName: String, artistName: String): LyricsResult? {
        return try {
            val query = "$trackName $artistName".trim()
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PrismAndroid/1.0 (https://github.com/prism-suite)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val array = JSONArray(body)
                if (array.length() == 0) return null

                // Prioritize first entry that actually has synced lyrics
                var fallbackPlain: LyricsResult? = null
                for (i in 0 until array.length()) {
                    val parsed = parseLrclibJson(array.getJSONObject(i))
                    if (parsed.syncedLines.isNotEmpty()) return parsed
                    if (fallbackPlain == null && !parsed.plainLyrics.isNullOrBlank()) {
                        fallbackPlain = parsed
                    }
                }
                fallbackPlain
            }
        } catch (e: Exception) {
            Log.w("LrclibRepo", "Search lyrics fetch failed", e)
            null
        }
    }

    private fun parseLrclibJson(obj: JSONObject): LyricsResult {
        val syncedRaw = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plainRaw = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val instrumental = obj.optBoolean("instrumental", false)
        return LyricsResult(
            syncedLines = if (syncedRaw != null) parseLrc(syncedRaw) else emptyList(),
            plainLyrics = plainRaw,
            instrumental = instrumental,
        )
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)")
        return lrc.lines().mapNotNull { line ->
            val match = regex.find(line.trim()) ?: return@mapNotNull null
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val fracRaw = match.groupValues[3]
            val ms = when (fracRaw.length) {
                1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                3 -> fracRaw.toLongOrNull() ?: 0L
                else -> 0L
            }
            val text = match.groupValues[4].trim()
            if (text.isEmpty()) return@mapNotNull null
            LyricLine(
                timeMs = (min * 60L + sec) * 1000L + ms,
                text = text,
            )
        }.sortedBy { it.timeMs }
    }

    private fun sanitizeTitle(raw: String): String {
        return raw
            .replace(Regex("\\s*[\\(\\[](?:Official|Audio|Lyrics|Music Video|HD|HQ|Visualizer|feat\\..*?)[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { raw.trim() }
    }
}
