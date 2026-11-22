package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PrismApp(val title: String) {
    YOUTUBE("YouTube"),
    MUSIC("Music"),
    NOTES("Notes"),
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

// --- NOTES FIRESTORE MODELS ---

@Keep
@IgnoreExtraProperties
data class NoteFolder(
    @DocumentId val id: String = "",
    val name: String = "",
    val iconKey: String = "folder",
    val systemFolder: Boolean = false,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class NoteChecklistItem(
    val text: String = "",
    val checked: Boolean = false,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "text" to text.take(240),
        "checked" to checked,
    )
}

@Keep
@IgnoreExtraProperties
data class NoteHighlightSpan(
    val text: String = "",
    val colorKey: String = "blue",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "text" to text.take(300),
        "colorKey" to colorKey.take(24),
    )
}

@Keep
@IgnoreExtraProperties
data class NoteAttachmentItem(
    val id: String = "",
    val type: String = "photo", // photo, scan, audio, drawing
    val title: String = "",
    val localPath: String = "",
    val durationSec: Long = 0L,
    val transcript: String = "",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(64),
        "type" to type.take(20),
        "title" to title.take(120),
        "localPath" to localPath.take(400),
        "durationSec" to durationSec.coerceAtLeast(0L),
        "transcript" to transcript.take(1000),
    )
}

@Keep
@IgnoreExtraProperties
data class NoteDocument(
    @DocumentId val id: String = "",
    val folderId: String = "notes_default",
    val title: String = "",
    val body: String = "",
    val snippet: String = "",
    val tags: List<String> = emptyList(),
    val checklists: List<Map<String, Any>> = emptyList(),
    val highlights: List<Map<String, Any>> = emptyList(),
    val attachments: List<Map<String, Any>> = emptyList(),
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun parsedChecklists(): List<NoteChecklistItem> = checklists.map { m ->
        NoteChecklistItem(
            text = m["text"] as? String ?: "",
            checked = m["checked"] as? Boolean ?: false,
        )
    }

    fun parsedHighlights(): List<NoteHighlightSpan> = highlights.map { m ->
        NoteHighlightSpan(
            text = m["text"] as? String ?: "",
            colorKey = m["colorKey"] as? String ?: "blue",
        )
    }

    fun parsedAttachments(): List<NoteAttachmentItem> = attachments.map { m ->
        NoteAttachmentItem(
            id = m["id"] as? String ?: "",
            type = m["type"] as? String ?: "photo",
            title = m["title"] as? String ?: "",
            localPath = m["localPath"] as? String ?: "",
            durationSec = (m["durationSec"] as? Number)?.toLong() ?: 0L,
            transcript = m["transcript"] as? String ?: "",
        )
    }
}

// --- MUSIC FIRESTORE MODELS ---

@Keep
@IgnoreExtraProperties
data class MusicTrack(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Long = 0L,
    val streamUrl: String = "",
    val explicit: Boolean = false,
) {
    fun toTrackMap(): Map<String, Any> = mapOf(
        "videoId" to videoId.take(64),
        "title" to title.take(200),
        "artist" to artist.take(160),
        "thumbnailUrl" to thumbnailUrl.take(500),
        "durationSec" to durationSec.coerceIn(0L, 86400L),
    )

    companion object {
        fun fromMap(m: Map<String, Any>): MusicTrack = MusicTrack(
            videoId = m["videoId"] as? String ?: "",
            title = m["title"] as? String ?: "",
            artist = m["artist"] as? String ?: "",
            thumbnailUrl = m["thumbnailUrl"] as? String ?: "",
            durationSec = (m["durationSec"] as? Number)?.toLong() ?: 0L,
        )
    }
}

@Keep
@IgnoreExtraProperties
data class MusicPlaylist(
    @DocumentId val id: String = "",
    val name: String = "",
    val coverStyle: Long = 0L,
    val tracks: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun parsedTracks(): List<MusicTrack> = tracks.map { MusicTrack.fromMap(it) }
}

@Keep
@IgnoreExtraProperties
data class MusicFavorite(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSec = durationSec,
    )
}

@Keep
@IgnoreExtraProperties
data class ListeningHistoryEntry(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Long = 0L,
    val listenedSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        thumbnailUrl = thumbnailUrl,
        durationSec = durationSec,
    )
}

// --- YOUTUBE FIRESTORE MODELS ---

@Keep
@IgnoreExtraProperties
data class YouTubeSubscription(
    @DocumentId val id: String = "",
    val channelUrl: String = "",
    val channelName: String = "",
    val avatarUrl: String = "",
    val subscriberText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class YouTubeSavedVideo(
    @DocumentId val id: String = "",
    val videoUrl: String = "",
    val title: String = "",
    val uploaderName: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Long = 0L,
    val liked: Boolean = false,
    val saved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

// --- RUNTIME MEDIA & EXTRACTION MODELS ---

data class YouTubeVideoItem(
    val id: String,
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String = "",
    val uploaderAvatarUrl: String = "",
    val thumbnailUrl: String,
    val durationSec: Long,
    val viewCount: Long = -1L,
    val uploadDateText: String = "",
)

data class VideoQualityOption(
    val resolution: String,
    val formatLabel: String,
    val url: String,
    val isVideoOnly: Boolean = false,
)

data class ExtractedYouTubeStream(
    val videoId: String,
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val uploaderAvatarUrl: String,
    val subscriberCount: Long,
    val thumbnailUrl: String,
    val durationSec: Long,
    val viewCount: Long,
    val likeCount: Long,
    val description: String,
    val videoQualities: List<VideoQualityOption>,
    val bestAudioUrl: String?,
    val relatedVideos: List<YouTubeVideoItem>,
)

data class YouTubeCommentItem(
    val author: String,
    val avatarUrl: String,
    val text: String,
    val likeCount: Int,
    val publishedTime: String,
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class LyricsResult(
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val instrumental: Boolean = false,
)

data class ReplaySummary(
    val totalMinutes: Long = 0L,
    val topArtist: String = "Play songs to unlock",
    val topSong: String = "No tracks yet",
    val topSongArtist: String = "",
    val topAlbum: String = "Your Mix",
    val topCovers: List<String> = emptyList(),
)

data class NotesFilterOptions(
    val query: String = "",
    val selectedTag: String? = null,
    val onlyPinned: Boolean = false,
    val onlyLocked: Boolean = false,
    val onlyWithChecklists: Boolean = false,
    val onlyWithDrawings: Boolean = false,
    val onlyWithScans: Boolean = false,
    val onlyWithAttachments: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = selectedTag != null || onlyPinned || onlyLocked || onlyWithChecklists ||
            onlyWithDrawings || onlyWithScans || onlyWithAttachments
}

fun sanitizeFirestoreId(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
    return cleaned.ifBlank { "id_${System.currentTimeMillis()}" }
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}

fun formatMillis(ms: Long): String = formatDuration((ms.coerceAtLeast(0L)) / 1000L)

fun formatCompactCount(count: Long): String {
    if (count < 0) return ""
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
