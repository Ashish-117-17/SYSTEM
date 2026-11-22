package com.example.ui.youtube

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.YouTubeSavedVideo
import com.example.data.model.YouTubeSubscription
import com.example.data.model.formatCompactCount
import com.example.data.model.formatMillis
import com.example.ui.glass.AppleMotion
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassPillGroup
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.YouTubeRed
import com.example.ui.theme.YouTubeRedGlow
import com.example.viewmodel.YouTubeUiState
import com.example.viewmodel.YouTubeViewModel
import kotlinx.coroutines.delay

/**
 * Custom iOS 26 Liquid Glass YouTube Player inspired directly by the user's uploaded F1 player UI:
 * - Top-left: Video Title > and uppercase channel name
 * - Top-right: Share circle + grouped [Cast | HD | Speed/Settings] glass pill + Close circle
 * - Center: Prev + [10s Back] circle + large [Play/Pause] circle + [10s Forward] circle + Next
 * - Bottom-left: Grouped [Like | Dislike] pill + [Comments] circle + [Bookmark] circle
 * - Bottom-right: Grouped [Autoplay | Ambient | CC] pill + [Lock Screen] circle
 * - Scrubber capsule: Glowing YouTube Red progress inside glass capsule with tabular timestamps
 * - Peeking related-videos strip right below the scrubber capsule
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLiquidPlayerOverlay(
    viewModel: YouTubeViewModel,
    uiState: YouTubeUiState,
    subscriptions: List<YouTubeSubscription>,
    savedVideos: List<YouTubeSavedVideo>,
) {
    val context = LocalContext.current
    val playback by viewModel.mediaController.playback.collectAsStateWithLifecycle()
    val colors = LocalPrismColors.current

    var showControls by remember { mutableStateOf(true) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    BackHandler {
        if (uiState.isFullscreen) {
            viewModel.toggleFullscreen()
        } else {
            viewModel.closePlayer()
        }
    }

    LaunchedEffect(showControls, playback.isPlaying, uiState.isScreenLocked) {
        if (showControls && playback.isPlaying && !uiState.isScreenLocked) {
            delay(4500L)
            showControls = false
        }
    }

    val stream = uiState.activeStream
    val savedEntry = stream?.let { s -> savedVideos.firstOrNull { it.id == s.videoId } }
    val isLiked = savedEntry?.liked == true
    val isSaved = savedEntry?.saved == true
    val isDisliked = stream != null && stream.videoId in uiState.dislikedVideoIds
    val isSubscribed = stream != null && subscriptions.any {
        it.channelName.equals(stream.uploaderName, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Video + AGSL Liquid Glass Controls Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (uiState.isFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .height(340.dp)
                    },
                )
                .background(Color.Black),
        ) {
            LiquidBackdropHost(
                modifier = Modifier.fillMaxSize(),
                overlay = {
                    // Ambient glow edge shimmer if enabled
                    if (uiState.ambientGlowEnabled && !uiState.isFullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, YouTubeRed.copy(alpha = 0.22f)),
                                    ),
                                ),
                        )
                    }

                    // Tap canvas to reveal/hide controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showControls = !showControls },
                            ),
                    )

                    if (uiState.isExtractingStream) {
                        CircularProgressIndicator(
                            color = YouTubeRed,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls || uiState.isScreenLocked,
                        enter = fadeIn(AppleMotion.morphSpring()),
                        exit = fadeOut(AppleMotion.morphSpring()),
                    ) {
                        if (uiState.isScreenLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.BottomEnd,
                            ) {
                                LiquidGlassIconButton(
                                    onClick = viewModel::toggleScreenLock,
                                    forceDarkGlass = true,
                                    size = 46.dp,
                                    modifier = Modifier.testTag("yt_unlock_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = stringResource(R.string.cd_unlock_player),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.54f),
                                                Color.Black.copy(alpha = 0.16f),
                                                Color.Black.copy(alpha = 0.68f),
                                            ),
                                        ),
                                    )
                                    .then(if (uiState.isFullscreen) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // TOP ROW: Title/Channel (left) + Share / [Cast HD Tune] / Close (right)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 10.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stream?.title ?: stringResource(R.string.yt_loading_video),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Text(
                                            text = (stream?.uploaderName ?: "").uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.70f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        LiquidGlassIconButton(
                                            onClick = {
                                                val shareUrl = stream?.url ?: return@LiquidGlassIconButton
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "${stream.title}\n$shareUrl")
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share video"))
                                            },
                                            size = 38.dp,
                                            forceDarkGlass = true,
                                            modifier = Modifier.testTag("yt_share_button"),
                                        ) {
                                            Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.action_share), tint = Color.White, modifier = Modifier.size(17.dp))
                                        }

                                        LiquidGlassPillGroup(
                                            forceDarkGlass = true,
                                            horizontalPadding = 4.dp,
                                            verticalPadding = 2.dp,
                                        ) {
                                            IconButton(
                                                onClick = viewModel::toggleFullscreen,
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                                    contentDescription = stringResource(R.string.cd_toggle_fullscreen),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { showQualitySheet = true },
                                                modifier = Modifier.size(34.dp).testTag("yt_quality_button"),
                                            ) {
                                                Icon(Icons.Rounded.Hd, contentDescription = stringResource(R.string.cd_video_quality), tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { showQualitySheet = true },
                                                modifier = Modifier.size(34.dp).testTag("yt_settings_button"),
                                            ) {
                                                Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.cd_playback_settings), tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        LiquidGlassIconButton(
                                            onClick = viewModel::closePlayer,
                                            size = 38.dp,
                                            forceDarkGlass = true,
                                            modifier = Modifier.testTag("yt_close_player_button"),
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_player), tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                // CENTER TRANSPORT ROW: Prev | 10s Back | Play/Pause | 10s Forward | Next
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = viewModel::playPreviousVideo, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.cd_previous), tint = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                    LiquidGlassIconButton(
                                        onClick = { viewModel.mediaController.seekBy(-10_000L) },
                                        size = 48.dp,
                                        forceDarkGlass = true,
                                        modifier = Modifier.testTag("yt_rewind_10_button"),
                                    ) {
                                        Icon(Icons.Rounded.Replay10, contentDescription = stringResource(R.string.cd_rewind_10), tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    LiquidGlassIconButton(
                                        onClick = { viewModel.mediaController.togglePlayPause() },
                                        size = 66.dp,
                                        forceDarkGlass = true,
                                        modifier = Modifier.testTag("yt_play_pause_button"),
                                    ) {
                                        Icon(
                                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = stringResource(R.string.cd_play_pause),
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp),
                                        )
                                    }
                                    LiquidGlassIconButton(
                                        onClick = { viewModel.mediaController.seekBy(10_000L) },
                                        size = 48.dp,
                                        forceDarkGlass = true,
                                        modifier = Modifier.testTag("yt_forward_10_button"),
                                    ) {
                                        Icon(Icons.Rounded.Forward10, contentDescription = stringResource(R.string.cd_forward_10), tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    IconButton(onClick = viewModel::playNextRelated, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.cd_next), tint = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                }

                                // BOTTOM CLUSTER + LIQUID GLASS RED SCRUBBER CAPSULE
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        // Bottom-left: [Like | Dislike] + Comments + Bookmark
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            LiquidGlassPillGroup(forceDarkGlass = true, horizontalPadding = 4.dp, verticalPadding = 2.dp) {
                                                IconButton(
                                                    onClick = viewModel::toggleLikeActiveVideo,
                                                    modifier = Modifier.size(34.dp).testTag("yt_like_button"),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.ThumbUp,
                                                        contentDescription = stringResource(R.string.cd_like),
                                                        tint = if (isLiked) YouTubeRed else Color.White,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                }
                                                IconButton(
                                                    onClick = viewModel::toggleDislikeActiveVideo,
                                                    modifier = Modifier.size(34.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.ThumbDown,
                                                        contentDescription = stringResource(R.string.cd_dislike),
                                                        tint = if (isDisliked) YouTubeRed else Color.White,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                }
                                            }
                                            LiquidGlassIconButton(
                                                onClick = {
                                                    viewModel.loadComments()
                                                    showCommentsSheet = true
                                                },
                                                size = 38.dp,
                                                forceDarkGlass = true,
                                                modifier = Modifier.testTag("yt_comments_button"),
                                            ) {
                                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = stringResource(R.string.cd_comments), tint = Color.White, modifier = Modifier.size(17.dp))
                                            }
                                            LiquidGlassIconButton(
                                                onClick = viewModel::toggleSaveActiveVideo,
                                                size = 38.dp,
                                                forceDarkGlass = true,
                                                modifier = Modifier.testTag("yt_save_button"),
                                            ) {
                                                Icon(
                                                    imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                                    contentDescription = stringResource(R.string.cd_save_video),
                                                    tint = if (isSaved) colors.systemBlue else Color.White,
                                                    modifier = Modifier.size(17.dp),
                                                )
                                            }
                                        }

                                        // Bottom-right: [Autoplay | Ambient | CC] + Lock
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            LiquidGlassPillGroup(forceDarkGlass = true, horizontalPadding = 4.dp, verticalPadding = 2.dp) {
                                                IconButton(onClick = viewModel::toggleAutoplay, modifier = Modifier.size(34.dp)) {
                                                    Icon(
                                                        Icons.Rounded.PlayCircleOutline,
                                                        contentDescription = stringResource(R.string.cd_autoplay),
                                                        tint = if (uiState.autoplayEnabled) Color.White else Color.White.copy(alpha = 0.42f),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                IconButton(onClick = viewModel::toggleAmbientGlow, modifier = Modifier.size(34.dp)) {
                                                    Icon(
                                                        Icons.Rounded.AutoAwesome,
                                                        contentDescription = stringResource(R.string.cd_ambient_mode),
                                                        tint = if (uiState.ambientGlowEnabled) Color.White else Color.White.copy(alpha = 0.42f),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                IconButton(onClick = viewModel::toggleCaptions, modifier = Modifier.size(34.dp)) {
                                                    Icon(
                                                        imageVector = if (uiState.captionsEnabled) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                                                        contentDescription = stringResource(R.string.cd_captions),
                                                        tint = if (uiState.captionsEnabled) Color.White else Color.White.copy(alpha = 0.48f),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                            LiquidGlassIconButton(
                                                onClick = viewModel::toggleScreenLock,
                                                size = 38.dp,
                                                forceDarkGlass = true,
                                                modifier = Modifier.testTag("yt_lock_button"),
                                            ) {
                                                Icon(Icons.Rounded.LockOpen, contentDescription = stringResource(R.string.cd_lock_player), tint = Color.White, modifier = Modifier.size(17.dp))
                                            }
                                        }
                                    }

                                    // Glowing Red Scrubber Capsule
                                    YouTubeGlassScrubberCapsule(
                                        positionMs = playback.positionMs,
                                        durationMs = playback.durationMs.takeIf { it > 0 } ?: ((stream?.durationSec ?: 0L) * 1000L),
                                        onSeek = { ms -> viewModel.mediaController.seekTo(ms) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) {
                // Media3 Hardware Video Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = viewModel.mediaController.exoPlayer
                        }
                    },
                    update = { pv ->
                        if (pv.player !== viewModel.mediaController.exoPlayer) {
                            pv.player = viewModel.mediaController.exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Peeking Related Videos Strip & Channel Details (Portrait Mode)
        if (!uiState.isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.canvas)
                    .navigationBarsPadding(),
            ) {
                if (stream != null && stream.relatedVideos.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(stream.relatedVideos.take(8), key = { it.id }) { rel ->
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .appleTactilePress()
                                    .clickable { viewModel.openVideo(rel) },
                            ) {
                                AsyncImage(
                                    model = rel.thumbnailUrl,
                                    contentDescription = rel.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (stream != null) {
                        item {
                            Column {
                                Text(
                                    text = stream.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.label,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val stats = buildList {
                                    if (stream.viewCount > 0) add("${formatCompactCount(stream.viewCount)} views")
                                    if (stream.likeCount > 0) add("${formatCompactCount(stream.likeCount)} likes")
                                    uiState.activeQuality?.resolution?.let { add(it) }
                                }
                                Text(
                                    text = stats.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryLabel,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.groupedCard)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(colors.secondaryFill),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (stream.uploaderAvatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = stream.uploaderAvatarUrl,
                                                contentDescription = stream.uploaderName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Text(stream.uploaderName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.label)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stream.uploaderName, style = MaterialTheme.typography.titleMedium, color = colors.label)
                                        if (stream.subscriberCount > 0) {
                                            Text(
                                                text = "${formatCompactCount(stream.subscriberCount)} subscribers",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.secondaryLabel,
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSubscribed) colors.secondaryFill else colors.label)
                                            .appleTactilePress()
                                            .clickable { viewModel.toggleSubscribeActiveChannel() }
                                            .padding(horizontal = 16.dp, vertical = 9.dp)
                                            .testTag("yt_subscribe_button"),
                                    ) {
                                        Text(
                                            text = if (isSubscribed) stringResource(R.string.yt_subscribed) else stringResource(R.string.yt_subscribe),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSubscribed) colors.label else colors.canvas,
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = stringResource(R.string.yt_up_next),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.label,
                            )
                        }

                        items(stream.relatedVideos, key = { it.id }) { rel ->
                            YouTubeVideoCard(
                                video = rel,
                                onClick = { viewModel.openVideo(rel) },
                            )
                        }
                    } else if (!uiState.streamError.isNullOrBlank()) {
                        item {
                            Text(
                                text = uiState.streamError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = YouTubeRed,
                            )
                        }
                    }
                }
            }
        }
    }

    // Quality & Playback Speed Modal Sheet
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            containerColor = colors.groupedCard,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.yt_sheet_quality), style = MaterialTheme.typography.titleLarge, color = colors.label)
                Spacer(modifier = Modifier.height(10.dp))
                val qualities = stream?.videoQualities.orEmpty()
                if (qualities.isEmpty()) {
                    Text(stringResource(R.string.yt_quality_auto), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualities.take(5).forEach { q ->
                            val selected = q.resolution == uiState.activeQuality?.resolution
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) YouTubeRed else colors.secondaryFill)
                                    .clickable {
                                        viewModel.selectQuality(q)
                                        showQualitySheet = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = "${q.resolution} ${q.formatLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Color.White else colors.label,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(stringResource(R.string.yt_sheet_speed), style = MaterialTheme.typography.titleLarge, color = colors.label)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        val selected = playback.playbackSpeed == spd
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) colors.systemBlue else colors.secondaryFill)
                                .clickable {
                                    viewModel.mediaController.setSpeed(spd)
                                    showQualitySheet = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = "${spd}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else colors.label,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Comments Modal Sheet
    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentsSheet = false },
            containerColor = colors.groupedCard,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Text(stringResource(R.string.yt_comments_title), style = MaterialTheme.typography.titleLarge, color = colors.label)
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.isLoadingComments) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YouTubeRed)
                    }
                } else if (uiState.comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.yt_comments_empty), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(uiState.comments) { comment ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.secondaryFill),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(comment.author.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.label)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${comment.author}  ${comment.publishedTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryLabel,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.label,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeGlassScrubberCapsule(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        cornerRadius = 999.dp,
        forceDarkGlass = true,
        elevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = formatMillis(positionMs),
                style = TabularNumberStyle.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = 0.90f),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .onGloballyPositioned { barWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(safeDuration) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            onSeek((ratio * safeDuration).toLong())
                        }
                    }
                    .pointerInput(safeDuration) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            onSeek((ratio * safeDuration).toLong())
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.24f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtLeast(0.01f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(YouTubeRed, YouTubeRedGlow),
                            ),
                        ),
                )
            }

            Text(
                text = formatMillis(durationMs),
                style = TabularNumberStyle.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = 0.90f),
            )
        }
    }
}
