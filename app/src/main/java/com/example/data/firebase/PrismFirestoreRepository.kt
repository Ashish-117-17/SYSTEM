package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ListeningHistoryEntry
import com.example.data.model.MusicFavorite
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import com.example.data.model.NoteAttachmentItem
import com.example.data.model.NoteChecklistItem
import com.example.data.model.NoteDocument
import com.example.data.model.NoteFolder
import com.example.data.model.NoteHighlightSpan
import com.example.data.model.YouTubeSavedVideo
import com.example.data.model.YouTubeSubscription
import com.example.data.model.sanitizeFirestoreId
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PrismFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    private fun <T> observeUserSubcollection(
        subcollection: String,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/$subcollection"
        emitAll(
            db.collection("users").document(uid).collection(subcollection)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    // ==================== NOTES & FOLDERS ====================

    fun observeFolders(): Flow<List<NoteFolder>> =
        observeUserSubcollection("folders", NoteFolder::class.java)
            .map { list -> list.sortedBy { it.sortOrder } }

    fun observeNotes(): Flow<List<NoteDocument>> =
        observeUserSubcollection("notes", NoteDocument::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<NoteDocument> { it.pinned }
                        .thenByDescending { it.updatedAt?.seconds ?: 0L },
                )
            }

    suspend fun ensureDefaultFolders(): Result<Unit> {
        var path = "users/unauthenticated/folders"
        return try {
            val uid = requireUserId()
            path = "users/$uid/folders"
            val col = db.collection("users").document(uid).collection("folders")
            val existing = col.get().await()
            if (existing.isEmpty) {
                val defaults = listOf(
                    Triple("notes_default", "Notes", "folder"),
                    Triple("study_hub", "Study & Lectures", "book"),
                    Triple("design_ideas", "Design ideas", "gear"),
                    Triple("thoughts_journal", "Thoughts", "folder"),
                )
                defaults.forEachIndexed { idx, (docId, name, icon) ->
                    col.document(docId).set(
                        mapOf(
                            "name" to name,
                            "iconKey" to icon,
                            "systemFolder" to (idx == 0),
                            "sortOrder" to idx.toLong(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createFolder(name: String, iconKey: String = "folder", sortOrder: Long = 10L): Result<String> {
        var path = "users/unauthenticated/folders"
        return try {
            val uid = requireUserId()
            val trimmed = name.trim().take(60)
            require(trimmed.isNotEmpty()) { "Folder name cannot be empty" }
            val docRef = db.collection("users").document(uid).collection("folders").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "name" to trimmed,
                    "iconKey" to iconKey.take(30),
                    "systemFolder" to false,
                    "sortOrder" to sortOrder.coerceIn(0L, 1000L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> {
        var path = "users/unauthenticated/folders/$folderId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("folders").document(folderId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        folderId: String,
        title: String = "",
        body: String = "",
        tags: List<String> = emptyList(),
        checklists: List<NoteChecklistItem> = emptyList(),
        highlights: List<NoteHighlightSpan> = emptyList(),
        attachments: List<NoteAttachmentItem> = emptyList(),
        pinned: Boolean = false,
        locked: Boolean = false,
    ): Result<String> {
        var path = "users/unauthenticated/notes"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document()
            path = docRef.path
            val safeBody = body.take(50000)
            val snippet = buildSnippet(safeBody, tags, attachments)
            docRef.set(
                mapOf(
                    "folderId" to folderId.ifBlank { "notes_default" }.take(128),
                    "title" to title.take(200),
                    "body" to safeBody,
                    "snippet" to snippet,
                    "tags" to tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(20),
                    "checklists" to checklists.take(100).map { it.toMap() },
                    "highlights" to highlights.take(50).map { it.toMap() },
                    "attachments" to attachments.take(30).map { it.toMap() },
                    "pinned" to pinned,
                    "locked" to locked,
                    "deleted" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        noteId: String,
        folderId: String,
        title: String,
        body: String,
        tags: List<String>,
        checklists: List<NoteChecklistItem>,
        highlights: List<NoteHighlightSpan>,
        attachments: List<NoteAttachmentItem>,
        pinned: Boolean,
        locked: Boolean,
        deleted: Boolean,
    ): Result<Unit> {
        var path = "users/unauthenticated/notes/$noteId"
        return try {
            val uid = requireUserId()
            require(noteId.isNotBlank()) { "Note ID required" }
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            path = docRef.path
            val safeBody = body.take(50000)
            val snippet = buildSnippet(safeBody, tags, attachments)
            docRef.update(
                mapOf(
                    "folderId" to folderId.ifBlank { "notes_default" }.take(128),
                    "title" to title.take(200),
                    "body" to safeBody,
                    "snippet" to snippet,
                    "tags" to tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(20),
                    "checklists" to checklists.take(100).map { it.toMap() },
                    "highlights" to highlights.take(50).map { it.toMap() },
                    "attachments" to attachments.take(30).map { it.toMap() },
                    "pinned" to pinned,
                    "locked" to locked,
                    "deleted" to deleted,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNoteFlags(
        noteId: String,
        pinned: Boolean? = null,
        locked: Boolean? = null,
        deleted: Boolean? = null,
    ): Result<Unit> {
        var path = "users/unauthenticated/notes/$noteId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            path = docRef.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (pinned != null) updates["pinned"] = pinned
            if (locked != null) updates["locked"] = locked
            if (deleted != null) updates["deleted"] = deleted
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun permanentlyDeleteNote(noteId: String): Result<Unit> {
        var path = "users/unauthenticated/notes/$noteId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun buildSnippet(
        body: String,
        tags: List<String>,
        attachments: List<NoteAttachmentItem>,
    ): String {
        val cleaned = body.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            cleaned.isNotEmpty() -> cleaned.take(200)
            tags.isNotEmpty() -> tags.joinToString(" ") { "#$it" }.take(200)
            attachments.isNotEmpty() -> attachments.first().title.ifBlank { "Attachment" }.take(200)
            else -> "No additional text"
        }
    }

    // ==================== MUSIC LIBRARY & REPLAY ====================

    fun observePlaylists(): Flow<List<MusicPlaylist>> =
        observeUserSubcollection("playlists", MusicPlaylist::class.java)
            .map { list -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L } }

    fun observeFavorites(): Flow<List<MusicFavorite>> =
        observeUserSubcollection("musicFavorites", MusicFavorite::class.java)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeListeningHistory(): Flow<List<ListeningHistoryEntry>> =
        observeUserSubcollection("listeningHistory", ListeningHistoryEntry::class.java)
            .map { list -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L } }

    suspend fun createPlaylist(name: String, coverStyle: Long): Result<String> {
        var path = "users/unauthenticated/playlists"
        return try {
            val uid = requireUserId()
            val safeName = name.trim().take(80)
            require(safeName.isNotEmpty()) { "Playlist name required" }
            val docRef = db.collection("users").document(uid).collection("playlists").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "name" to safeName,
                    "coverStyle" to coverStyle.coerceIn(0L, 10L),
                    "tracks" to emptyList<Map<String, Any>>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addTrackToPlaylist(playlist: MusicPlaylist, track: MusicTrack): Result<Unit> {
        var path = "users/unauthenticated/playlists/${playlist.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("playlists").document(playlist.id)
            path = docRef.path
            val current = playlist.parsedTracks()
            if (current.any { it.videoId == track.videoId }) return Result.success(Unit)
            val updatedTracks = (current + track).take(200).map { it.toTrackMap() }
            docRef.update(
                mapOf(
                    "tracks" to updatedTracks,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        var path = "users/unauthenticated/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMusicFavorite(track: MusicTrack, currentlyFavorite: Boolean): Result<Unit> {
        val docId = sanitizeFirestoreId(track.videoId)
        var path = "users/unauthenticated/musicFavorites/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("musicFavorites").document(docId)
            path = docRef.path
            if (currentlyFavorite) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "videoId" to track.videoId.ifBlank { docId }.take(64),
                        "title" to track.title.ifBlank { "Untitled Track" }.take(200),
                        "artist" to track.artist.ifBlank { "Unknown Artist" }.take(160),
                        "thumbnailUrl" to track.thumbnailUrl.take(500),
                        "durationSec" to track.durationSec.coerceIn(0L, 86400L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun recordListeningHistory(track: MusicTrack, listenedSec: Long): Result<Unit> {
        var path = "users/unauthenticated/listeningHistory"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("listeningHistory").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "videoId" to track.videoId.ifBlank { "track" }.take(64),
                    "title" to track.title.ifBlank { "Untitled" }.take(200),
                    "artist" to track.artist.ifBlank { "Unknown Artist" }.take(160),
                    "album" to track.album.take(160),
                    "thumbnailUrl" to track.thumbnailUrl.take(500),
                    "durationSec" to track.durationSec.coerceIn(0L, 86400L),
                    "listenedSec" to listenedSec.coerceIn(1L, 86400L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // ==================== YOUTUBE LIBRARY ====================

    fun observeSubscriptions(): Flow<List<YouTubeSubscription>> =
        observeUserSubcollection("ytSubscriptions", YouTubeSubscription::class.java)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeSavedVideos(): Flow<List<YouTubeSavedVideo>> =
        observeUserSubcollection("ytSavedVideos", YouTubeSavedVideo::class.java)
            .map { list -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L } }

    suspend fun toggleYouTubeSubscription(
        channelUrl: String,
        channelName: String,
        avatarUrl: String,
        subscriberText: String,
        isSubscribed: Boolean,
    ): Result<Unit> {
        val docId = sanitizeFirestoreId(channelUrl.substringAfterLast("/").ifBlank { channelName })
        var path = "users/unauthenticated/ytSubscriptions/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("ytSubscriptions").document(docId)
            path = docRef.path
            if (isSubscribed) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "channelUrl" to channelUrl.ifBlank { "https://www.youtube.com" }.take(300),
                        "channelName" to channelName.ifBlank { "Channel" }.take(160),
                        "avatarUrl" to avatarUrl.take(500),
                        "subscriberText" to subscriberText.take(80),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveYouTubeVideoState(
        videoId: String,
        videoUrl: String,
        title: String,
        uploaderName: String,
        thumbnailUrl: String,
        durationSec: Long,
        liked: Boolean,
        saved: Boolean,
        alreadyExists: Boolean,
    ): Result<Unit> {
        val docId = sanitizeFirestoreId(videoId)
        var path = "users/unauthenticated/ytSavedVideos/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("ytSavedVideos").document(docId)
            path = docRef.path
            if (!liked && !saved) {
                docRef.delete().await()
            } else if (alreadyExists) {
                docRef.update(
                    mapOf(
                        "title" to title.ifBlank { "Video" }.take(240),
                        "uploaderName" to uploaderName.ifBlank { "Creator" }.take(160),
                        "thumbnailUrl" to thumbnailUrl.take(500),
                        "durationSec" to durationSec.coerceIn(0L, 86400L),
                        "liked" to liked,
                        "saved" to saved,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "videoUrl" to videoUrl.ifBlank { "https://www.youtube.com/watch?v=$docId" }.take(300),
                        "title" to title.ifBlank { "Video" }.take(240),
                        "uploaderName" to uploaderName.ifBlank { "Creator" }.take(160),
                        "thumbnailUrl" to thumbnailUrl.take(500),
                        "durationSec" to durationSec.coerceIn(0L, 86400L),
                        "liked" to liked,
                        "saved" to saved,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
