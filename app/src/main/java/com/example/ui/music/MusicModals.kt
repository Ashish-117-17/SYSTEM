package com.example.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import com.example.data.model.ReplaySummary
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.MusicRose
import com.example.ui.theme.PlaylistCoverGradients

@Composable
fun MusicCollectionDetailScreen(
    title: String,
    subtitle: String,
    gradientIndex: Int,
    tracks: List<MusicTrack>,
    favoriteIds: Set<String>,
    onBack: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onDeletePlaylist: (() -> Unit)?,
) {
    BackHandler(onBack = onBack)
    val colors = LocalPrismColors.current
    val grad = PlaylistCoverGradients[gradientIndex.coerceIn(0, PlaylistCoverGradients.lastIndex)]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
        contentPadding = PaddingValues(bottom = 180.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                grad.first().copy(alpha = 0.85f),
                                grad.last().copy(alpha = 0.40f),
                                colors.canvas,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidGlassIconButton(onClick = onBack, size = 40.dp) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = colors.label)
                        }
                        if (onDeletePlaylist != null) {
                            LiquidGlassIconButton(onClick = onDeletePlaylist, size = 40.dp) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = MusicRose)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(grad)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.label)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.secondaryFill)
                                .appleTactilePress()
                                .clickable(enabled = tracks.isNotEmpty()) {
                                    tracks.firstOrNull()?.let { onPlayTrack(it, tracks) }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MusicRose)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.music_play), style = MaterialTheme.typography.titleSmall, color = MusicRose)
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.secondaryFill)
                                .appleTactilePress()
                                .clickable(enabled = tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = MusicRose)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.music_shuffle), style = MaterialTheme.typography.titleSmall, color = MusicRose)
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.music_collection_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(tracks, key = { it.videoId }) { track ->
                MusicTrackRow(
                    track = track,
                    isFavorite = track.videoId in favoriteIds,
                    onClick = { onPlayTrack(track, tracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long) -> Unit,
) {
    val colors = LocalPrismColors.current
    var name by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.groupedCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassIconButton(onClick = onDismiss, size = 38.dp) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel), tint = colors.label)
                }
                Text(stringResource(R.string.music_new_playlist), style = MaterialTheme.typography.titleMedium, color = colors.label)
                LiquidGlassIconButton(
                    onClick = { if (name.isNotBlank()) onCreate(name, selectedStyle.toLong()) },
                    size = 38.dp,
                    accentTint = if (name.isNotBlank()) MusicRose else null,
                    modifier = Modifier.testTag("music_confirm_create_playlist"),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_done), tint = if (name.isNotBlank()) Color.White else colors.tertiaryLabel)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Generative Cover Artwork Preview
            val previewGrad = PlaylistCoverGradients[selectedStyle]
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(previewGrad)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = name.ifBlank { stringResource(R.string.music_playlist_title_placeholder) },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(PlaylistCoverGradients) { idx, grad ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(grad))
                            .then(
                                if (idx == selectedStyle) Modifier.border(3.dp, MusicRose, CircleShape)
                                else Modifier,
                            )
                            .clickable { selectedStyle = idx },
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.music_playlist_title_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_playlist_name_input"),
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: MusicTrack,
    playlists: List<MusicPlaylist>,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onSelectPlaylist: (MusicPlaylist) -> Unit,
) {
    val colors = LocalPrismColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.groupedCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.music_add_to_playlist), style = MaterialTheme.typography.titleLarge, color = colors.label)
            Text(track.title, style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.secondaryFill)
                    .clickable(onClick = onCreateNew)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MusicRose)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.music_new_playlist), style = MaterialTheme.typography.titleSmall, color = MusicRose)
            }

            Spacer(modifier = Modifier.height(10.dp))

            playlists.forEach { pl ->
                val grad = PlaylistCoverGradients[pl.coverStyle.toInt().coerceIn(0, PlaylistCoverGradients.lastIndex)]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPlaylist(pl) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(grad)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(pl.name, style = MaterialTheme.typography.titleSmall, color = colors.label)
                        Text("${pl.parsedTracks().size} songs", style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ReplayHubModal(
    replay: ReplaySummary,
    historyCount: Int,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1F0812), Color(0xFF8C142B), Color(0xFF0A0A0C)),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White)
                    Text(stringResource(R.string.music_replay_badge), style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                LiquidGlassIconButton(onClick = onDismiss, forceDarkGlass = true, size = 40.dp) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${replay.totalMinutes}",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.music_replay_minutes_label, historyCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.80f),
                )

                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    forceDarkGlass = true,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ReplayStatLine(stringResource(R.string.music_replay_top_artist), replay.topArtist)
                        ReplayStatLine(stringResource(R.string.music_replay_top_song), replay.topSong)
                        ReplayStatLine(stringResource(R.string.music_replay_top_album), replay.topAlbum)
                    }
                }

                if (replay.topCovers.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(replay.topCovers) { coverUrl ->
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReplayStatLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}
