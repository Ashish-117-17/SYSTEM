package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.PrismFirestoreRepository
import com.example.data.media.ActiveMediaSource
import com.example.data.media.LrclibRepository
import com.example.data.media.NewPipeRepository
import com.example.data.media.PrismMediaController
import com.example.data.model.ListeningHistoryEntry
import com.example.data.model.LyricsResult
import com.example.data.model.MusicFavorite
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import com.example.data.model.ReplaySummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MusicTab(val label: String) {
    HOME("Home"),
    NEW("New"),
    RADIO("Radio"),
    LIBRARY("Library"),
    SEARCH("Search"),
}

data class RadioStationCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val query: String,
    val gradientIndex: Int,
)

data class MusicGenreCard(
    val title: String,
    val query: String,
    val gradientIndex: Int,
)

data class MusicUiState(
    val activeTab: MusicTab = MusicTab.HOME,
    val topPicksTracks: List<MusicTrack> = emptyList(),
    val latestSongs: List<MusicTrack> = emptyList(),
    val newReleases: List<MusicTrack> = emptyList(),
    val isLoadingDiscovery: Boolean = false,
    val discoveryError: String? = null,
    val searchQuery: String = "",
    val searchResults: List<MusicTrack> = emptyList(),
    val isSearching: Boolean = false,
    val currentTrack: MusicTrack? = null,
    val queue: List<MusicTrack> = emptyList(),
    val queueIndex: Int = -1,
    val isResolvingStream: Boolean = false,
    val lyrics: LyricsResult = LyricsResult(),
    val isLoadingLyrics: Boolean = false,
    val showLyricsView: Boolean = true,
    val isNowPlayingOpen: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatOne: Boolean = false,
    val vocalAttenuation: Float = 1.0f,
    val selectedPlaylistDetailId: String? = null,
    val showFavoritesDetail: Boolean = false,
    val showCreatePlaylistModal: Boolean = false,
    val trackPendingPlaylistAdd: MusicTrack? = null,
    val showReplayHub: Boolean = false,
    val statusMessage: String? = null,
)

class MusicViewModel(
    private val newPipe: NewPipeRepository,
    private val lrclib: LrclibRepository,
    private val firestore: PrismFirestoreRepository,
    val mediaController: PrismMediaController,
) : ViewModel() {

    val radioStations: List<RadioStationCard> = listOf(
        RadioStationCard("prism_1", "Prism 1", "Global pop, hip-hop & new hits", "top global pop hits official audio 2025", 2),
        RadioStationCard("prism_hits", "Music Hits", "Timeless anthems & chart classics", "greatest pop rock hits official audio", 0),
        RadioStationCard("prism_chill", "Chill & Lo-Fi", "Late-night study beats & ambient", "lofi chill beats ambient study music", 4),
        RadioStationCard("prism_club", "Club & Electronic", "High-energy house & synthwave", "electronic dance club hits official audio", 5),
        RadioStationCard("prism_indie", "Indie Pulse", "Alternative & bedroom pop", "indie alternative pop hits official audio", 1),
    )

    val browseGenres: List<MusicGenreCard> = listOf(
        MusicGenreCard("Pop", "pop hits official audio", 2),
        MusicGenreCard("Hip-Hop", "hip hop rap hits official audio", 3),
        MusicGenreCard("R&B & Soul", "r&b soul hits official audio", 5),
        MusicGenreCard("Indie", "indie alternative hits official audio", 1),
        MusicGenreCard("Electronic", "electronic house synth official audio", 0),
        MusicGenreCard("Focus & Study", "ambient focus instrumental piano", 4),
        MusicGenreCard("Spatial Audio", "cinematic orchestral atmospheric music", 6),
        MusicGenreCard("Hits", "billboard top 50 songs official audio", 2),
    )

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    val playlists: StateFlow<List<MusicPlaylist>> = firestore.observePlaylists()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            _uiState.update { it.copy(statusMessage = "Unable to sync playlists") }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<MusicFavorite>> = firestore.observeFavorites()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listeningHistory: StateFlow<List<ListeningHistoryEntry>> = firestore.observeListeningHistory()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val replaySummary: StateFlow<ReplaySummary> = listeningHistory
        .map { entries -> computeReplaySummary(entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReplaySummary())

    init {
        loadDiscoveryFeeds()
    }

    fun selectTab(tab: MusicTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                selectedPlaylistDetailId = null,
                showFavoritesDetail = false,
            )
        }
    }

    fun loadDiscoveryFeeds() {
        if (_uiState.value.isLoadingDiscovery) return
        _uiState.update { it.copy(isLoadingDiscovery = true, discoveryError = null) }
        viewModelScope.launch {
            val topRes = newPipe.searchMusicTracks("top pop hits 2025 official audio")
            val latestRes = newPipe.searchMusicTracks("new music releases 2025 official audio")
            val topTracks = topRes.getOrDefault(emptyList())
            val latestTracks = latestRes.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    topPicksTracks = topTracks.take(12),
                    latestSongs = latestTracks.take(12),
                    newReleases = (latestTracks.drop(4) + topTracks.drop(4)).distinctBy { t -> t.videoId }.take(10),
                    isLoadingDiscovery = false,
                    discoveryError = if (topTracks.isEmpty() && latestTracks.isEmpty()) {
                        "Unable to load Music catalog. Check internet connection."
                    } else null,
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun searchMusic(explicitQuery: String? = null) {
        val query = (explicitQuery ?: _uiState.value.searchQuery).trim()
        if (query.isBlank()) return
        _uiState.update {
            it.copy(
                activeTab = MusicTab.SEARCH,
                searchQuery = query,
                isSearching = true,
            )
        }
        viewModelScope.launch {
            newPipe.searchMusicTracks(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            statusMessage = err.localizedMessage ?: "Music search failed",
                        )
                    }
                }
        }
    }

    fun playRadioStation(station: RadioStationCard) {
        _uiState.update { it.copy(isResolvingStream = true, statusMessage = "Tuning into ${station.title}...") }
        viewModelScope.launch {
            newPipe.searchMusicTracks(station.query)
                .onSuccess { tracks ->
                    if (tracks.isNotEmpty()) {
                        playTrack(tracks.first(), tracks)
                    } else {
                        _uiState.update { it.copy(isResolvingStream = false, statusMessage = "Station unavailable") }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isResolvingStream = false, statusMessage = "Could not load station") }
                }
        }
    }

    fun playTrack(track: MusicTrack, contextQueue: List<MusicTrack> = emptyList()) {
        val nextQueue = when {
            contextQueue.isNotEmpty() -> contextQueue
            _uiState.value.queue.any { it.videoId == track.videoId } -> _uiState.value.queue
            else -> listOf(track) + _uiState.value.topPicksTracks.filter { it.videoId != track.videoId }.take(10)
        }
        val idx = nextQueue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                currentTrack = track,
                queue = nextQueue,
                queueIndex = idx,
                isResolvingStream = true,
                lyrics = LyricsResult(),
                isLoadingLyrics = true,
            )
        }

        // Concurrently resolve real audio stream via NewPipeExtractor & synced lyrics via LRCLIB
        viewModelScope.launch {
            newPipe.extractDirectAudioUrl(track.videoId)
                .onSuccess { audioUrl ->
                    mediaController.playStream(audioUrl, ActiveMediaSource.MUSIC)
                    _uiState.update { it.copy(isResolvingStream = false) }
                    firestore.recordListeningHistory(
                        track = track,
                        listenedSec = track.durationSec.takeIf { d -> d > 0 } ?: 180L,
                    )
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            statusMessage = err.localizedMessage ?: "Couldn't stream track",
                        )
                    }
                }
        }

        viewModelScope.launch {
            val fetchedLyrics = lrclib.fetchLyrics(track.title, track.artist, track.durationSec)
            _uiState.update { state ->
                if (state.currentTrack?.videoId == track.videoId) {
                    state.copy(lyrics = fetchedLyrics, isLoadingLyrics = false)
                } else {
                    state
                }
            }
        }
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val nextIdx = if (state.shuffleEnabled && state.queue.size > 1) {
            (state.queue.indices.filter { it != state.queueIndex }).random()
        } else {
            (state.queueIndex + 1) % state.queue.size
        }
        playTrack(state.queue[nextIdx], state.queue)
    }

    fun skipPrevious() {
        val pos = mediaController.playback.value.positionMs
        if (pos > 3500L) {
            mediaController.seekTo(0L)
            return
        }
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val prevIdx = if (state.queueIndex > 0) state.queueIndex - 1 else state.queue.lastIndex
        playTrack(state.queue[prevIdx], state.queue)
    }

    fun handleTrackEnded() {
        val state = _uiState.value
        val track = state.currentTrack ?: return
        if (state.repeatOne) {
            mediaController.seekTo(0L)
            mediaController.togglePlayPause()
        } else if (state.queue.size > 1) {
            skipNext()
        } else {
            playTrack(track, state.queue)
        }
    }

    fun openNowPlaying() {
        if (_uiState.value.currentTrack != null) {
            _uiState.update { it.copy(isNowPlayingOpen = true) }
        }
    }

    fun closeNowPlaying() {
        _uiState.update { it.copy(isNowPlayingOpen = false) }
    }

    fun toggleLyricsView() {
        _uiState.update { it.copy(showLyricsView = !it.showLyricsView) }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun toggleRepeatOne() {
        _uiState.update { it.copy(repeatOne = !it.repeatOne) }
    }

    fun setVocalAttenuation(value: Float) {
        _uiState.update { it.copy(vocalAttenuation = value.coerceIn(0f, 1f)) }
    }

    fun toggleFavorite(track: MusicTrack) {
        val isFav = favorites.value.any { it.videoId == track.videoId }
        viewModelScope.launch {
            firestore.toggleMusicFavorite(track, isFav)
        }
    }

    fun openPlaylistDetail(playlistId: String) {
        _uiState.update { it.copy(selectedPlaylistDetailId = playlistId, showFavoritesDetail = false) }
    }

    fun openFavoritesDetail() {
        _uiState.update { it.copy(showFavoritesDetail = true, selectedPlaylistDetailId = null) }
    }

    fun closeCollectionDetail() {
        _uiState.update { it.copy(selectedPlaylistDetailId = null, showFavoritesDetail = false) }
    }

    fun setShowCreatePlaylist(show: Boolean) {
        _uiState.update { it.copy(showCreatePlaylistModal = show) }
    }

    fun createPlaylist(name: String, coverStyle: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            firestore.createPlaylist(name, coverStyle).onSuccess {
                _uiState.update { it.copy(showCreatePlaylistModal = false, statusMessage = "Created \"$name\"") }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            firestore.deletePlaylist(playlistId)
            _uiState.update { it.copy(selectedPlaylistDetailId = null) }
        }
    }

    fun requestAddToPlaylist(track: MusicTrack) {
        _uiState.update { it.copy(trackPendingPlaylistAdd = track) }
    }

    fun dismissAddToPlaylist() {
        _uiState.update { it.copy(trackPendingPlaylistAdd = null) }
    }

    fun addTrackToPlaylist(playlist: MusicPlaylist, track: MusicTrack) {
        viewModelScope.launch {
            firestore.addTrackToPlaylist(playlist, track).onSuccess {
                _uiState.update {
                    it.copy(
                        trackPendingPlaylistAdd = null,
                        statusMessage = "Added to ${playlist.name}",
                    )
                }
            }
        }
    }

    fun setShowReplayHub(show: Boolean) {
        _uiState.update { it.copy(showReplayHub = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun computeReplaySummary(entries: List<ListeningHistoryEntry>): ReplaySummary {
        if (entries.isEmpty()) return ReplaySummary()
        val totalSec = entries.sumOf { it.listenedSec.coerceAtLeast(30L) }
        val topArtist = entries.groupingBy { it.artist.ifBlank { "Unknown" } }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: "Unknown"
        val topSongEntry = entries.groupBy { it.videoId }
            .maxByOrNull { it.value.size }?.value?.firstOrNull()
        val topCovers = entries.map { it.thumbnailUrl }.filter { it.isNotBlank() }.distinct().take(6)
        return ReplaySummary(
            totalMinutes = (totalSec / 60L).coerceAtLeast(1L),
            topArtist = topArtist,
            topSong = topSongEntry?.title ?: "No tracks yet",
            topSongArtist = topSongEntry?.artist.orEmpty(),
            topAlbum = topSongEntry?.album?.ifBlank { "Top Mix" } ?: "Top Mix",
            topCovers = topCovers,
        )
    }

    class Factory(
        private val newPipe: NewPipeRepository,
        private val lrclib: LrclibRepository,
        private val firestore: PrismFirestoreRepository,
        private val mediaController: PrismMediaController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MusicViewModel(newPipe, lrclib, firestore, mediaController) as T
    }
}
