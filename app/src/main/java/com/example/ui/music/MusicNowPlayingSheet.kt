package com.example.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.ActiveMediaSource
import com.example.data.model.formatMillis
import com.example.ui.glass.AppleMotion
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.MusicRose
import com.example.ui.theme.TabularNumberStyle
import com.example.viewmodel.MusicUiState
import com.example.viewmodel.MusicViewModel

/**
 * Full-screen iOS 26 Music Now Playing & Interactive Synced Lyrics modal
 * modeled after 06_now_playing_lyrics.webp with blurred artwork aura,
 * tap-to-seek LRCLIB lyrics, and Liquid Glass Vocal/Sing popover.
 */
@Composable
fun MusicNowPlayingSheet(
    viewModel: MusicViewModel,
    uiState: MusicUiState,
    isFavorite: Boolean,
) {
    BackHandler(onBack = viewModel::closeNowPlaying)

    val playback by viewModel.mediaController.playback.collectAsStateWithLifecycle()
    val track = uiState.currentTrack ?: return
    val isPlaying = playback.source == ActiveMediaSource.MUSIC && playback.isPlaying
    val durationMs = playback.durationMs.takeIf { it > 0L } ?: (track.durationSec * 1000L).coerceAtLeast(1000L)
    val progress = (playback.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    var showVocalPopover by remember { mutableStateOf(false) }
    var showQueuePanel by remember { mutableStateOf(false) }

    val lyricLines = uiState.lyrics.syncedLines
    val activeLyricIndex by remember(lyricLines, playback.positionMs) {
        derivedStateOf {
            if (lyricLines.isEmpty()) -1
            else lyricLines.indexOfLast { it.timeMs <= playback.positionMs + 250L }
        }
    }
    val lyricsListState = rememberLazyListState()

    LaunchedEffect(activeLyricIndex, uiState.showLyricsView) {
        if (uiState.showLyricsView && activeLyricIndex >= 0) {
            lyricsListState.animateScrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
        }
    }

    LiquidBackdropHost(
        modifier = Modifier.fillMaxSize(),
        overlay = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Handle + Compact Artwork Header (matches Apple Music Lyrics screen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LiquidGlassIconButton(
                        onClick = viewModel::closeNowPlaying,
                        forceDarkGlass = true,
                        size = 38.dp,
                        modifier = Modifier.testTag("music_close_now_playing"),
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_minimize_player), tint = Color.White)
                    }

                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    LiquidGlassIconButton(
                        onClick = { viewModel.toggleFavorite(track) },
                        forceDarkGlass = true,
                        size = 38.dp,
                        modifier = Modifier.testTag("music_now_playing_favorite"),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(R.string.cd_favorite_song),
                            tint = if (isFavorite) MusicRose else Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    LiquidGlassIconButton(
                        onClick = { viewModel.requestAddToPlaylist(track) },
                        forceDarkGlass = true,
                        size = 38.dp,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.cd_add_to_playlist), tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }

                // Center Stage: Synced Lyrics, Queue, or Hero Artwork
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        showQueuePanel -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    Text(
                                        text = stringResource(R.string.music_playing_next),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                itemsIndexed(uiState.queue) { idx, qTrack ->
                                    val isCurrent = idx == uiState.queueIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isCurrent) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                            .clickable { viewModel.playTrack(qTrack, uiState.queue) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(
                                            model = qTrack.thumbnailUrl,
                                            contentDescription = qTrack.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(qTrack.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(qTrack.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        uiState.showLyricsView -> {
                            when {
                                uiState.isLoadingLyrics -> {
                                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.85f))
                                }

                                lyricLines.isNotEmpty() -> {
                                    LazyColumn(
                                        state = lyricsListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("music_synced_lyrics_list"),
                                        contentPadding = PaddingValues(vertical = 32.dp),
                                        verticalArrangement = Arrangement.spacedBy(22.dp),
                                    ) {
                                        itemsIndexed(lyricLines) { index, line ->
                                            val isActive = index == activeLyricIndex
                                            val scale by animateFloatAsState(
                                                targetValue = if (isActive) 1.04f else 0.96f,
                                                animationSpec = AppleMotion.morphSpring(),
                                                label = "lyric_scale",
                                            )
                                            val lineColor by animateColorAsState(
                                                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.36f),
                                                animationSpec = AppleMotion.morphSpring(),
                                                label = "lyric_color",
                                            )
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.headlineLarge,
                                                color = lineColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    }
                                                    .clickable { viewModel.mediaController.seekTo(line.timeMs) },
                                            )
                                        }
                                    }
                                }

                                !uiState.lyrics.plainLyrics.isNullOrBlank() -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 20.dp),
                                    ) {
                                        item {
                                            Text(
                                                text = uiState.lyrics.plainLyrics.orEmpty(),
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = Color.White.copy(alpha = 0.90f),
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth(0.80f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(24.dp)),
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = if (uiState.lyrics.instrumental) {
                                                stringResource(R.string.music_lyrics_instrumental)
                                            } else {
                                                stringResource(R.string.music_lyrics_unavailable)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.68f),
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.86f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(26.dp)),
                            )
                        }
                    }

                    // Vocal Level Liquid Glass Popover (inspired by Apple Music Sing popover)
                    this@Column.AnimatedVisibility(
                        visible = showVocalPopover,
                        enter = fadeIn(AppleMotion.morphSpring()),
                        exit = fadeOut(AppleMotion.morphSpring()),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        LiquidGlassSurface(
                            modifier = Modifier
                                .width(230.dp)
                                .padding(bottom = 8.dp),
                            cornerRadius = 22.dp,
                            forceDarkGlass = true,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(R.string.music_vocal_level),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                                LiquidGlassSlider(
                                    value = uiState.vocalAttenuation,
                                    onValueChange = viewModel::setVocalAttenuation,
                                    activeTrackColor = Color.White,
                                )
                            }
                        }
                    }
                }

                // Bottom Controls Cluster: Scrubber, Transport, Volume, Lyrics/Vocal/Queue Pills
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidGlassSlider(
                        value = progress,
                        onValueChange = { ratio ->
                            viewModel.mediaController.seekTo((ratio * durationMs).toLong())
                        },
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatMillis(playback.positionMs),
                            style = TabularNumberStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            text = "-${formatMillis((durationMs - playback.positionMs).coerceAtLeast(0L))}",
                            style = TabularNumberStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }

                    // Transport Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::toggleShuffle) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = stringResource(R.string.music_shuffle),
                                tint = if (uiState.shuffleEnabled) MusicRose else Color.White.copy(alpha = 0.65f),
                            )
                        }
                        IconButton(onClick = viewModel::skipPrevious, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.FastRewind, contentDescription = stringResource(R.string.cd_previous), tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        LiquidGlassIconButton(
                            onClick = { viewModel.mediaController.togglePlayPause() },
                            forceDarkGlass = true,
                            size = 64.dp,
                            modifier = Modifier.testTag("music_now_playing_play_pause"),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        IconButton(onClick = viewModel::skipNext, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.FastForward, contentDescription = stringResource(R.string.cd_next), tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = viewModel::toggleRepeatOne) {
                            Icon(
                                imageVector = if (uiState.repeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = stringResource(R.string.cd_repeat),
                                tint = if (uiState.repeatOne) MusicRose else Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }

                    // Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
                        LiquidGlassSlider(
                            value = playback.volume,
                            onValueChange = { viewModel.mediaController.setVolume(it) },
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
                    }

                    // Bottom Action Bar (Lyrics | Vocal Sing | Queue)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidGlassIconButton(
                            onClick = {
                                showQueuePanel = false
                                viewModel.toggleLyricsView()
                            },
                            forceDarkGlass = true,
                            accentTint = if (uiState.showLyricsView && !showQueuePanel) Color.White.copy(alpha = 0.35f) else null,
                            size = 44.dp,
                            modifier = Modifier.testTag("music_toggle_lyrics_button"),
                        ) {
                            Icon(Icons.Rounded.FormatQuote, contentDescription = stringResource(R.string.cd_toggle_lyrics), tint = Color.White)
                        }

                        LiquidGlassIconButton(
                            onClick = { showVocalPopover = !showVocalPopover },
                            forceDarkGlass = true,
                            accentTint = if (showVocalPopover) MusicRose else null,
                            size = 44.dp,
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.music_vocal_level), tint = Color.White)
                        }

                        LiquidGlassIconButton(
                            onClick = { showQueuePanel = !showQueuePanel },
                            forceDarkGlass = true,
                            accentTint = if (showQueuePanel) Color.White.copy(alpha = 0.35f) else null,
                            size = 44.dp,
                            modifier = Modifier.testTag("music_toggle_queue_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.cd_queue), tint = Color.White)
                        }
                    }
                }
            }
        },
    ) {
        // Deep Blurred Ambient Artwork Backdrop
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101014))) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.56f),
                                Color.Black.copy(alpha = 0.68f),
                                Color.Black.copy(alpha = 0.84f),
                            ),
                        ),
                    ),
            )
        }
    }
}
