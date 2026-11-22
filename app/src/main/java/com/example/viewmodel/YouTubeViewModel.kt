package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.PrismFirestoreRepository
import com.example.data.media.ActiveMediaSource
import com.example.data.media.NewPipeRepository
import com.example.data.media.PrismMediaController
import com.example.data.model.ExtractedYouTubeStream
import com.example.data.model.VideoQualityOption
import com.example.data.model.YouTubeCommentItem
import com.example.data.model.YouTubeSavedVideo
import com.example.data.model.YouTubeSubscription
import com.example.data.model.YouTubeVideoItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class YouTubeTab(val label: String) {
    HOME("Home"),
    SUBSCRIPTIONS("Subscriptions"),
    LIBRARY("Library"),
    SEARCH("Search"),
}

data class YouTubeUiState(
    val activeTab: YouTubeTab = YouTubeTab.HOME,
    val selectedCategory: String = "All",
    val feedVideos: List<YouTubeVideoItem> = emptyList(),
    val isLoadingFeed: Boolean = false,
    val feedError: String? = null,
    val searchQuery: String = "",
    val searchResults: List<YouTubeVideoItem> = emptyList(),
    val isSearching: Boolean = false,
    val activeStream: ExtractedYouTubeStream? = null,
    val activeQuality: VideoQualityOption? = null,
    val isExtractingStream: Boolean = false,
    val streamError: String? = null,
    val isPlayerOpen: Boolean = false,
    val isFullscreen: Boolean = false,
    val isScreenLocked: Boolean = false,
    val autoplayEnabled: Boolean = true,
    val ambientGlowEnabled: Boolean = true,
    val captionsEnabled: Boolean = false,
    val dislikedVideoIds: Set<String> = emptySet(),
    val comments: List<YouTubeCommentItem> = emptyList(),
    val isLoadingComments: Boolean = false,
    val statusMessage: String? = null,
)

class YouTubeViewModel(
    private val newPipe: NewPipeRepository,
    private val firestore: PrismFirestoreRepository,
    val mediaController: PrismMediaController,
) : ViewModel() {

    val categories: List<String> = listOf(
        "All",
        "Music",
        "Tech & Design",
        "Formula 1",
        "Gaming",
        "Study & Focus",
        "Cinema",
    )

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    val subscriptions: StateFlow<List<YouTubeSubscription>> = firestore.observeSubscriptions()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            _uiState.update { it.copy(statusMessage = "Unable to sync subscriptions") }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedVideos: StateFlow<List<YouTubeSavedVideo>> = firestore.observeSavedVideos()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            _uiState.update { it.copy(statusMessage = "Unable to sync saved videos") }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val watchHistory = mutableListOf<YouTubeVideoItem>()

    init {
        loadCategory("All")
    }

    fun selectTab(tab: YouTubeTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun loadCategory(category: String) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                isLoadingFeed = true,
                feedError = null,
            )
        }
        viewModelScope.launch {
            val result = if (category == "All") {
                newPipe.fetchTrendingVideos()
            } else {
                val query = when (category) {
                    "Tech & Design" -> "Apple design hardware review 4K"
                    "Formula 1" -> "Formula 1 highlights official trailer"
                    "Study & Focus" -> "ambient study desk setup productivity"
                    "Cinema" -> "official movie trailers 2025 4K"
                    else -> "$category trending 2025"
                }
                newPipe.searchVideos(query)
            }
            result.onSuccess { videos ->
                _uiState.update {
                    it.copy(
                        feedVideos = videos,
                        isLoadingFeed = false,
                        feedError = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        feedError = err.localizedMessage ?: "Couldn't load YouTube feed. Check connection and retry.",
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun executeSearch(explicitQuery: String? = null) {
        val query = (explicitQuery ?: _uiState.value.searchQuery).trim()
        if (query.isBlank()) return
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearching = true,
                feedError = null,
            )
        }
        viewModelScope.launch {
            newPipe.searchVideos(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            statusMessage = err.localizedMessage ?: "Search failed",
                        )
                    }
                }
        }
    }

    fun openVideo(item: YouTubeVideoItem) {
        if (watchHistory.lastOrNull()?.id != item.id) {
            watchHistory.add(item)
            if (watchHistory.size > 30) watchHistory.removeAt(0)
        }
        _uiState.update {
            it.copy(
                isPlayerOpen = true,
                isExtractingStream = true,
                streamError = null,
                isScreenLocked = false,
                comments = emptyList(),
            )
        }
        viewModelScope.launch {
            newPipe.extractVideoStream(item.url.ifBlank { item.id })
                .onSuccess { stream ->
                    val preferredQuality = stream.videoQualities.firstOrNull()
                    val playableUrl = preferredQuality?.url ?: stream.bestAudioUrl
                    if (playableUrl != null) {
                        mediaController.playStream(playableUrl, ActiveMediaSource.YOUTUBE)
                    }
                    _uiState.update {
                        it.copy(
                            activeStream = stream,
                            activeQuality = preferredQuality,
                            isExtractingStream = false,
                            streamError = if (playableUrl == null) "No direct video stream available" else null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isExtractingStream = false,
                            streamError = err.localizedMessage ?: "Failed to extract video stream",
                        )
                    }
                }
        }
    }

    fun selectQuality(option: VideoQualityOption) {
        val currentPos = mediaController.playback.value.positionMs
        _uiState.update { it.copy(activeQuality = option) }
        mediaController.playStream(option.url, ActiveMediaSource.YOUTUBE, startPositionMs = currentPos)
    }

    fun playNextRelated() {
        val next = _uiState.value.activeStream?.relatedVideos?.firstOrNull() ?: return
        openVideo(next)
    }

    fun playPreviousVideo() {
        if (watchHistory.size >= 2) {
            watchHistory.removeAt(watchHistory.lastIndex)
            val prev = watchHistory.last()
            openVideo(prev)
        } else {
            mediaController.seekTo(0L)
        }
    }

    fun handleTrackEnded() {
        if (_uiState.value.autoplayEnabled && _uiState.value.isPlayerOpen) {
            playNextRelated()
        }
    }

    fun closePlayer() {
        if (mediaController.playback.value.source == ActiveMediaSource.YOUTUBE) {
            mediaController.pause()
        }
        _uiState.update { it.copy(isPlayerOpen = false, isFullscreen = false, isScreenLocked = false) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun toggleScreenLock() {
        _uiState.update { it.copy(isScreenLocked = !it.isScreenLocked) }
    }

    fun toggleAutoplay() {
        _uiState.update { it.copy(autoplayEnabled = !it.autoplayEnabled) }
    }

    fun toggleAmbientGlow() {
        _uiState.update { it.copy(ambientGlowEnabled = !it.ambientGlowEnabled) }
    }

    fun toggleCaptions() {
        _uiState.update { it.copy(captionsEnabled = !it.captionsEnabled) }
    }

    fun loadComments() {
        val stream = _uiState.value.activeStream ?: return
        if (_uiState.value.isLoadingComments) return
        _uiState.update { it.copy(isLoadingComments = true) }
        viewModelScope.launch {
            newPipe.fetchComments(stream.url)
                .onSuccess { list ->
                    _uiState.update { it.copy(comments = list, isLoadingComments = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingComments = false) }
                }
        }
    }

    fun toggleLikeActiveVideo() {
        val stream = _uiState.value.activeStream ?: return
        val existing = savedVideos.value.firstOrNull { it.id == stream.videoId || it.videoUrl == stream.url }
        val nextLiked = !(existing?.liked ?: false)
        val currentSaved = existing?.saved ?: false
        if (nextLiked) {
            _uiState.update { it.copy(dislikedVideoIds = it.dislikedVideoIds - stream.videoId) }
        }
        viewModelScope.launch {
            firestore.saveYouTubeVideoState(
                videoId = stream.videoId,
                videoUrl = stream.url,
                title = stream.title,
                uploaderName = stream.uploaderName,
                thumbnailUrl = stream.thumbnailUrl,
                durationSec = stream.durationSec,
                liked = nextLiked,
                saved = currentSaved,
                alreadyExists = existing != null,
            )
        }
    }

    fun toggleDislikeActiveVideo() {
        val stream = _uiState.value.activeStream ?: return
        val currentlyDisliked = stream.videoId in _uiState.value.dislikedVideoIds
        _uiState.update {
            it.copy(
                dislikedVideoIds = if (currentlyDisliked) {
                    it.dislikedVideoIds - stream.videoId
                } else {
                    it.dislikedVideoIds + stream.videoId
                },
            )
        }
        val existing = savedVideos.value.firstOrNull { it.id == stream.videoId }
        if (!currentlyDisliked && existing?.liked == true) {
            viewModelScope.launch {
                firestore.saveYouTubeVideoState(
                    videoId = stream.videoId,
                    videoUrl = stream.url,
                    title = stream.title,
                    uploaderName = stream.uploaderName,
                    thumbnailUrl = stream.thumbnailUrl,
                    durationSec = stream.durationSec,
                    liked = false,
                    saved = existing.saved,
                    alreadyExists = true,
                )
            }
        }
    }

    fun toggleSaveActiveVideo() {
        val stream = _uiState.value.activeStream ?: return
        val existing = savedVideos.value.firstOrNull { it.id == stream.videoId || it.videoUrl == stream.url }
        val currentLiked = existing?.liked ?: false
        val nextSaved = !(existing?.saved ?: false)
        viewModelScope.launch {
            firestore.saveYouTubeVideoState(
                videoId = stream.videoId,
                videoUrl = stream.url,
                title = stream.title,
                uploaderName = stream.uploaderName,
                thumbnailUrl = stream.thumbnailUrl,
                durationSec = stream.durationSec,
                liked = currentLiked,
                saved = nextSaved,
                alreadyExists = existing != null,
            )
        }
    }

    fun toggleSubscribeActiveChannel() {
        val stream = _uiState.value.activeStream ?: return
        val subscribed = subscriptions.value.any {
            it.channelName.equals(stream.uploaderName, ignoreCase = true) ||
                (stream.uploaderUrl.isNotBlank() && it.channelUrl == stream.uploaderUrl)
        }
        viewModelScope.launch {
            firestore.toggleYouTubeSubscription(
                channelUrl = stream.uploaderUrl,
                channelName = stream.uploaderName,
                avatarUrl = stream.uploaderAvatarUrl,
                subscriberText = if (stream.subscriberCount > 0) "${stream.subscriberCount} subscribers" else "",
                isSubscribed = subscribed,
            )
        }
    }

    fun unsubscribeChannel(sub: YouTubeSubscription) {
        viewModelScope.launch {
            firestore.toggleYouTubeSubscription(
                channelUrl = sub.channelUrl,
                channelName = sub.channelName,
                avatarUrl = sub.avatarUrl,
                subscriberText = sub.subscriberText,
                isSubscribed = true,
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val newPipe: NewPipeRepository,
        private val firestore: PrismFirestoreRepository,
        private val mediaController: PrismMediaController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            YouTubeViewModel(newPipe, firestore, mediaController) as T
    }
}
