package com.example.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.ActiveMediaSource
import com.example.data.model.ListeningHistoryEntry
import com.example.data.model.MusicFavorite
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import com.example.data.model.ReplaySummary
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.shell.ProfileGlassOrbButton
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.MusicRose
import com.example.ui.theme.PlaylistCoverGradients
import com.example.viewmodel.MusicGenreCard
import com.example.viewmodel.MusicTab
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.RadioStationCard

@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    userInitials: String,
    userPhotoUrl: String?,
    onOpenProfileSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.listeningHistory.collectAsStateWithLifecycle()
    val replay by viewModel.replaySummary.collectAsStateWithLifecycle()
    val playback by viewModel.mediaController.playback.collectAsStateWithLifecycle()
    val colors = LocalPrismColors.current

    val isMusicPlaying = playback.source == ActiveMediaSource.MUSIC && playback.isPlaying
    val favoriteIds = favorites.map { it.videoId }.toSet()

    val activePlaylistDetail = uiState.selectedPlaylistDetailId?.let { id ->
        playlists.firstOrNull { it.id == id }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        LiquidBackdropHost(
            modifier = Modifier.fillMaxSize(),
            overlay = {
                // Floating Mini-Player Pill stacked cleanly above 5-Tab Liquid Glass Pill Bar
                if (!uiState.isNowPlayingOpen) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth()
                            .widthIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MusicFloatingMiniPlayer(
                            track = uiState.currentTrack,
                            isPlaying = isMusicPlaying,
                            isResolving = uiState.isResolvingStream,
                            onTapPill = viewModel::openNowPlaying,
                            onTogglePlay = {
                                if (uiState.currentTrack != null) {
                                    viewModel.mediaController.togglePlayPause()
                                } else {
                                    uiState.topPicksTracks.firstOrNull()?.let { viewModel.playTrack(it, uiState.topPicksTracks) }
                                }
                            },
                            onNext = viewModel::skipNext,
                        )

                        MusicFloatingTabBar(
                            activeTab = uiState.activeTab,
                            onSelectTab = viewModel::selectTab,
                        )
                    }
                }
            },
        ) {
            when {
                activePlaylistDetail != null -> {
                    MusicCollectionDetailScreen(
                        title = activePlaylistDetail.name,
                        subtitle = "${activePlaylistDetail.parsedTracks().size} songs",
                        gradientIndex = activePlaylistDetail.coverStyle.toInt(),
                        tracks = activePlaylistDetail.parsedTracks(),
                        favoriteIds = favoriteIds,
                        onBack = viewModel::closeCollectionDetail,
                        onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = viewModel::requestAddToPlaylist,
                        onDeletePlaylist = { viewModel.deletePlaylist(activePlaylistDetail.id) },
                    )
                }

                uiState.showFavoritesDetail -> {
                    val favTracks = favorites.map { it.toMusicTrack() }
                    MusicCollectionDetailScreen(
                        title = stringResource(R.string.music_favourite_songs),
                        subtitle = "${favTracks.size} favourite songs",
                        gradientIndex = 2,
                        tracks = favTracks,
                        favoriteIds = favoriteIds,
                        onBack = viewModel::closeCollectionDetail,
                        onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = viewModel::requestAddToPlaylist,
                        onDeletePlaylist = null,
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    ) {
                        // Top Header matching Apple Music large display title + Profile Orb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = when (uiState.activeTab) {
                                    MusicTab.HOME -> stringResource(R.string.music_tab_home)
                                    MusicTab.NEW -> stringResource(R.string.music_tab_new)
                                    MusicTab.RADIO -> stringResource(R.string.music_tab_radio)
                                    MusicTab.LIBRARY -> stringResource(R.string.music_tab_library)
                                    MusicTab.SEARCH -> stringResource(R.string.music_tab_search)
                                },
                                style = MaterialTheme.typography.displayMedium,
                                color = colors.label,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (uiState.activeTab == MusicTab.LIBRARY) {
                                    LiquidGlassIconButton(
                                        onClick = { viewModel.setShowCreatePlaylist(true) },
                                        size = 40.dp,
                                        modifier = Modifier.testTag("music_create_playlist_button"),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = stringResource(R.string.music_new_playlist),
                                            tint = MusicRose,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                ProfileGlassOrbButton(
                                    userInitials = userInitials,
                                    userPhotoUrl = userPhotoUrl,
                                    onClick = onOpenProfileSwitcher,
                                )
                            }
                        }

                        when (uiState.activeTab) {
                            MusicTab.HOME -> MusicHomeTab(
                                topPicks = uiState.topPicksTracks,
                                recentlyPlayed = history,
                                replay = replay,
                                isLoading = uiState.isLoadingDiscovery,
                                error = uiState.discoveryError,
                                onOpenFavorites = viewModel::openFavoritesDetail,
                                onOpenReplay = { viewModel.setShowReplayHub(true) },
                                onRetry = viewModel::loadDiscoveryFeeds,
                                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            )

                            MusicTab.NEW -> MusicNewTab(
                                latestSongs = uiState.latestSongs,
                                newReleases = uiState.newReleases,
                                favoriteIds = favoriteIds,
                                isLoading = uiState.isLoadingDiscovery,
                                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = viewModel::requestAddToPlaylist,
                            )

                            MusicTab.RADIO -> MusicRadioTab(
                                stations = viewModel.radioStations,
                                onTuneStation = viewModel::playRadioStation,
                            )

                            MusicTab.LIBRARY -> MusicLibraryTab(
                                playlists = playlists,
                                favorites = favorites,
                                history = history,
                                onOpenFavorites = viewModel::openFavoritesDetail,
                                onOpenPlaylist = viewModel::openPlaylistDetail,
                                onCreatePlaylist = { viewModel.setShowCreatePlaylist(true) },
                                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            )

                            MusicTab.SEARCH -> MusicSearchTab(
                                query = uiState.searchQuery,
                                genres = viewModel.browseGenres,
                                results = uiState.searchResults,
                                favoriteIds = favoriteIds,
                                isSearching = uiState.isSearching,
                                onQueryChange = viewModel::updateSearchQuery,
                                onSearch = { viewModel.searchMusic() },
                                onGenreClick = { genre -> viewModel.searchMusic(genre.query) },
                                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = viewModel::requestAddToPlaylist,
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Now Playing + Synced LRCLIB Lyrics Overlay
        if (uiState.isNowPlayingOpen && uiState.currentTrack != null) {
            MusicNowPlayingSheet(
                viewModel = viewModel,
                uiState = uiState,
                isFavorite = uiState.currentTrack!!.videoId in favoriteIds,
            )
        }

        // Create Playlist Modal
        if (uiState.showCreatePlaylistModal) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.setShowCreatePlaylist(false) },
                onCreate = viewModel::createPlaylist,
            )
        }

        // Add to Playlist Picker
        uiState.trackPendingPlaylistAdd?.let { pendingTrack ->
            AddToPlaylistSheet(
                track = pendingTrack,
                playlists = playlists,
                onDismiss = viewModel::dismissAddToPlaylist,
                onCreateNew = {
                    viewModel.dismissAddToPlaylist()
                    viewModel.setShowCreatePlaylist(true)
                },
                onSelectPlaylist = { pl -> viewModel.addTrackToPlaylist(pl, pendingTrack) },
            )
        }

        // Replay Annual Hub Modal
        if (uiState.showReplayHub) {
            ReplayHubModal(
                replay = replay,
                historyCount = history.size,
                onDismiss = { viewModel.setShowReplayHub(false) },
            )
        }
    }
}

@Composable
private fun MusicHomeTab(
    topPicks: List<MusicTrack>,
    recentlyPlayed: List<ListeningHistoryEntry>,
    replay: ReplaySummary,
    isLoading: Boolean,
    error: String?,
    onOpenFavorites: () -> Unit,
    onOpenReplay: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    val colors = LocalPrismColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Top Picks for You (matching 01_home_feed.webp)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.music_top_picks),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.label,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Hero Favourite Songs Card
                    item {
                        Column(
                            modifier = Modifier
                                .width(236.dp)
                                .height(304.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1E1E21))
                                .appleTactilePress(pressedScale = 0.97f)
                                .clickable(onClick = onOpenFavorites)
                                .padding(14.dp)
                                .testTag("music_favorites_hero_card"),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(186.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF3F3F6)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = MusicRose,
                                    modifier = Modifier.size(92.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.music_made_by_you),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.72f),
                                )
                                Text(
                                    text = stringResource(R.string.music_favourite_songs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                )
                                Text(
                                    text = stringResource(R.string.music_favorites_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 2,
                                )
                            }
                        }
                    }

                    // Dynamic Top Picks from NewPipe Music Search
                    items(topPicks.take(8), key = { it.videoId }) { track ->
                        Box(
                            modifier = Modifier
                                .width(236.dp)
                                .height(304.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.groupedCard)
                                .appleTactilePress(pressedScale = 0.97f)
                                .clickable { onPlayTrack(track, topPicks) },
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                                        ),
                                    ),
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.music_made_for_you),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                )
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.76f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading / Error banner if catalog still fetching
        if (isLoading && topPicks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicRose)
                }
            }
        } else if (error != null && topPicks.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.groupedCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry), tint = MusicRose)
                    }
                }
            }
        }

        // Recently Played Section
        val recentTracks = if (recentlyPlayed.isNotEmpty()) {
            recentlyPlayed.distinctBy { it.videoId }.map { it.toMusicTrack() }
        } else {
            topPicks.drop(2).take(8)
        }
        if (recentTracks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.music_recently_played),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.label,
                        )
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.secondaryLabel)
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(recentTracks.take(10), key = { it.videoId }) { track ->
                            Column(
                                modifier = Modifier
                                    .width(152.dp)
                                    .appleTactilePress(pressedScale = 0.96f)
                                    .clickable { onPlayTrack(track, recentTracks) },
                            ) {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(152.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.secondaryFill),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Replay Annual Hub Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFA2D48), Color(0xFFFF6A3D), Color(0xFFFFB24A)),
                        ),
                    )
                    .appleTactilePress(pressedScale = 0.98f)
                    .clickable(onClick = onOpenReplay)
                    .padding(20.dp)
                    .testTag("music_replay_card"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.music_replay_badge),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.90f),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (replay.totalMinutes > 0) {
                                "${replay.totalMinutes} mins listened • ${replay.topArtist}"
                            } else {
                                stringResource(R.string.music_replay_cta)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun MusicNewTab(
    latestSongs: List<MusicTrack>,
    newReleases: List<MusicTrack>,
    favoriteIds: Set<String>,
    isLoading: Boolean,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
) {
    val colors = LocalPrismColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (isLoading && latestSongs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicRose)
                }
            }
        }

        if (latestSongs.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.music_latest_songs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.label,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            items(latestSongs.take(8), key = { it.videoId }) { track ->
                MusicTrackRow(
                    track = track,
                    isFavorite = track.videoId in favoriteIds,
                    onClick = { onPlayTrack(track, latestSongs) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                )
            }
        }

        if (newReleases.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.music_new_releases),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.label,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(newReleases, key = { it.videoId }) { track ->
                            Column(
                                modifier = Modifier
                                    .width(164.dp)
                                    .appleTactilePress()
                                    .clickable { onPlayTrack(track, newReleases) },
                            ) {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(164.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.secondaryFill),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(track.title, style = MaterialTheme.typography.titleSmall, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicRadioTab(
    stations: List<RadioStationCard>,
    onTuneStation: (RadioStationCard) -> Unit,
) {
    val colors = LocalPrismColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.music_live_stations),
                style = MaterialTheme.typography.titleMedium,
                color = colors.secondaryLabel,
            )
        }
        items(stations, key = { it.id }) { station ->
            val grad = PlaylistCoverGradients[station.gradientIndex % PlaylistCoverGradients.size]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(grad))
                    .appleTactilePress(pressedScale = 0.98f)
                    .clickable { onTuneStation(station) }
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.music_live_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(station.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(station.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.84f))
                    }
                    LiquidGlassIconButton(
                        onClick = { onTuneStation(station) },
                        forceDarkGlass = true,
                        size = 48.dp,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_station), tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicLibraryTab(
    playlists: List<MusicPlaylist>,
    favorites: List<MusicFavorite>,
    history: List<ListeningHistoryEntry>,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    val colors = LocalPrismColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Grouped iOS Library Rows
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.groupedCard),
            ) {
                LibraryMenuRow(
                    icon = Icons.Rounded.Star,
                    title = stringResource(R.string.music_favourite_songs),
                    count = favorites.size,
                    onClick = onOpenFavorites,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = colors.separator)
                LibraryMenuRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.music_playlists),
                    count = playlists.size,
                    onClick = onCreatePlaylist,
                )
            }
        }

        // Playlists 2-column grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.music_playlists), style = MaterialTheme.typography.headlineSmall, color = colors.label)
                Text(
                    text = stringResource(R.string.music_new_playlist),
                    style = MaterialTheme.typography.labelLarge,
                    color = MusicRose,
                    modifier = Modifier.clickable(onClick = onCreatePlaylist),
                )
            }
        }

        if (playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.groupedCard)
                        .clickable(onClick = onCreatePlaylist)
                        .padding(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.music_empty_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryLabel,
                    )
                }
            }
        } else {
            val chunked = playlists.chunked(2)
            items(chunked) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rowItems.forEach { pl ->
                        val grad = PlaylistCoverGradients[pl.coverStyle.toInt().coerceIn(0, PlaylistCoverGradients.lastIndex)]
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .appleTactilePress()
                                .clickable { onOpenPlaylist(pl.id) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(grad))
                                    .padding(14.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(pl.name, style = MaterialTheme.typography.titleSmall, color = colors.label, maxLines = 1)
                            Text("${pl.parsedTracks().size} songs", style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Recently Added / Played
        if (history.isNotEmpty()) {
            val recent = history.distinctBy { it.videoId }.take(6).map { it.toMusicTrack() }
            item {
                Text(stringResource(R.string.music_recently_played), style = MaterialTheme.typography.headlineSmall, color = colors.label)
            }
            items(recent, key = { it.videoId }) { track ->
                MusicTrackRow(
                    track = track,
                    isFavorite = favorites.any { it.videoId == track.videoId },
                    onClick = { onPlayTrack(track, recent) },
                    onToggleFavorite = {},
                    onAddToPlaylist = {},
                    showActions = false,
                )
            }
        }
    }
}

@Composable
private fun LibraryMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MusicRose, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.label, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.tertiaryLabel)
    }
}

@Composable
private fun MusicSearchTab(
    query: String,
    genres: List<MusicGenreCard>,
    results: List<MusicTrack>,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onGenreClick: (MusicGenreCard) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
) {
    val colors = LocalPrismColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.secondaryFill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.label),
                cursorBrush = SolidColor(MusicRose),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("music_search_input"),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.music_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.secondaryLabel,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search), tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MusicRose)
            }
        } else if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
            ) {
                items(results, key = { it.videoId }) { track ->
                    MusicTrackRow(
                        track = track,
                        isFavorite = track.videoId in favoriteIds,
                        onClick = { onPlayTrack(track, results) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(stringResource(R.string.music_browse_categories), style = MaterialTheme.typography.headlineSmall, color = colors.label)
                }
                items(genres.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { genre ->
                            val grad = PlaylistCoverGradients[genre.gradientIndex % PlaylistCoverGradients.size]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(grad))
                                    .appleTactilePress()
                                    .clickable { onGenreClick(genre) }
                                    .padding(14.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(genre.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicTrackRow(
    track: MusicTrack,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    showActions: Boolean = true,
) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryFill),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showActions) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(R.string.cd_favorite_song),
                    tint = if (isFavorite) MusicRose else colors.secondaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = stringResource(R.string.cd_add_to_playlist),
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MusicFloatingMiniPlayer(
    track: MusicTrack?,
    isPlaying: Boolean,
    isResolving: Boolean,
    onTapPill: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalPrismColors.current
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .appleTactilePress(pressedScale = 0.985f)
            .clickable(enabled = track != null, onClick = onTapPill)
            .testTag("music_mini_player_pill"),
        cornerRadius = 999.dp,
        elevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.secondaryFill),
                contentAlignment = Alignment.Center,
            ) {
                if (track != null && track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.secondaryLabel)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: stringResource(R.string.music_not_playing),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track != null) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isResolving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MusicRose)
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                IconButton(onClick = onTogglePlay, modifier = Modifier.testTag("music_mini_play_button")) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = colors.label,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            IconButton(onClick = onNext, enabled = track != null) {
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = stringResource(R.string.cd_next),
                    tint = if (track != null) colors.label else colors.tertiaryLabel,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun MusicFloatingTabBar(
    activeTab: MusicTab,
    onSelectTab: (MusicTab) -> Unit,
) {
    val colors = LocalPrismColors.current
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        cornerRadius = 999.dp,
        elevation = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val icon = when (tab) {
                    MusicTab.HOME -> Icons.Rounded.Home
                    MusicTab.NEW -> Icons.Rounded.GridView
                    MusicTab.RADIO -> Icons.Rounded.Radio
                    MusicTab.LIBRARY -> Icons.Rounded.LibraryMusic
                    MusicTab.SEARCH -> Icons.Rounded.Search
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) {
                                if (colors.isDark) Color.White.copy(alpha = 0.14f)
                                else Color.Black.copy(alpha = 0.08f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 6.dp)
                        .testTag("music_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (selected) MusicRose else colors.label,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MusicRose else colors.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
