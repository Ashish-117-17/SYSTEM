package com.example.ui.youtube

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.YouTubeSavedVideo
import com.example.data.model.YouTubeSubscription
import com.example.data.model.YouTubeVideoItem
import com.example.data.model.formatCompactCount
import com.example.data.model.formatDuration
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.shell.ProfileGlassOrbButton
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.YouTubeRed
import com.example.viewmodel.YouTubeTab
import com.example.viewmodel.YouTubeViewModel

@Composable
fun YouTubeScreen(
    viewModel: YouTubeViewModel,
    userInitials: String,
    userPhotoUrl: String?,
    onOpenProfileSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val savedVideos by viewModel.savedVideos.collectAsStateWithLifecycle()
    val colors = LocalPrismColors.current

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        LiquidBackdropHost(
            modifier = Modifier.fillMaxSize(),
            overlay = {
                if (!uiState.isPlayerOpen) {
                    YouTubeFloatingBottomBar(
                        activeTab = uiState.activeTab,
                        onSelectTab = viewModel::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_youtube),
                            contentDescription = stringResource(R.string.app_youtube),
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            text = when (uiState.activeTab) {
                                YouTubeTab.HOME -> stringResource(R.string.app_youtube)
                                YouTubeTab.SUBSCRIPTIONS -> stringResource(R.string.yt_tab_subscriptions)
                                YouTubeTab.LIBRARY -> stringResource(R.string.yt_tab_library)
                                YouTubeTab.SEARCH -> stringResource(R.string.yt_tab_search)
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.label,
                        )
                    }

                    ProfileGlassOrbButton(
                        userInitials = userInitials,
                        userPhotoUrl = userPhotoUrl,
                        onClick = onOpenProfileSwitcher,
                    )
                }

                when (uiState.activeTab) {
                    YouTubeTab.HOME -> YouTubeHomeContent(
                        categories = viewModel.categories,
                        selectedCategory = uiState.selectedCategory,
                        videos = uiState.feedVideos,
                        isLoading = uiState.isLoadingFeed,
                        error = uiState.feedError,
                        onSelectCategory = viewModel::loadCategory,
                        onRetry = { viewModel.loadCategory(uiState.selectedCategory) },
                        onVideoClick = viewModel::openVideo,
                    )

                    YouTubeTab.SUBSCRIPTIONS -> YouTubeSubscriptionsContent(
                        subscriptions = subscriptions,
                        onSearchChannel = { channel ->
                            viewModel.selectTab(YouTubeTab.SEARCH)
                            viewModel.executeSearch(channel.channelName)
                        },
                        onUnsubscribe = viewModel::unsubscribeChannel,
                    )

                    YouTubeTab.LIBRARY -> YouTubeLibraryContent(
                        savedVideos = savedVideos,
                        onVideoClick = { saved ->
                            viewModel.openVideo(
                                YouTubeVideoItem(
                                    id = saved.id,
                                    url = saved.videoUrl,
                                    title = saved.title,
                                    uploaderName = saved.uploaderName,
                                    thumbnailUrl = saved.thumbnailUrl,
                                    durationSec = saved.durationSec,
                                ),
                            )
                        },
                    )

                    YouTubeTab.SEARCH -> YouTubeSearchContent(
                        query = uiState.searchQuery,
                        results = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.executeSearch() },
                        onVideoClick = viewModel::openVideo,
                    )
                }
            }
        }

        // Fullscreen / Portrait Liquid Glass Video Player Overlay
        if (uiState.isPlayerOpen) {
            YouTubeLiquidPlayerOverlay(
                viewModel = viewModel,
                uiState = uiState,
                subscriptions = subscriptions,
                savedVideos = savedVideos,
            )
        }
    }
}

@Composable
private fun YouTubeHomeContent(
    categories: List<String>,
    selectedCategory: String,
    videos: List<YouTubeVideoItem>,
    isLoading: Boolean,
    error: String?,
    onSelectCategory: (String) -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (YouTubeVideoItem) -> Unit,
) {
    val colors = LocalPrismColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal Glass Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { cat ->
                val selected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) colors.label else colors.secondaryFill,
                        )
                        .appleTactilePress(pressedScale = 0.94f)
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("yt_category_${cat.lowercase().replace(" ", "_")}"),
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.canvas else colors.label,
                    )
                }
            }
        }

        when {
            isLoading && videos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YouTubeRed)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.yt_loading_feed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryLabel,
                        )
                    }
                }
            }

            error != null && videos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryLabel,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LiquidGlassSurface(
                            modifier = Modifier
                                .appleTactilePress()
                                .clickable(onClick = onRetry),
                            cornerRadius = 999.dp,
                            accentTint = YouTubeRed,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.action_retry), color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(videos, key = { it.id }) { video ->
                        YouTubeVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeVideoCard(
    video: YouTubeVideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPrismColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.groupedCard)
            .appleTactilePress(pressedScale = 0.98f)
            .clickable(onClick = onClick)
            .testTag("yt_video_card_${video.id}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.Black),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (video.durationSec > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.76f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = formatDuration(video.durationSec),
                        style = TabularNumberStyle.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryFill),
                contentAlignment = Alignment.Center,
            ) {
                if (video.uploaderAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.uploaderAvatarUrl,
                        contentDescription = video.uploaderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = video.uploaderName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.label,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                val metaParts = buildList {
                    add(video.uploaderName)
                    if (video.viewCount > 0) add("${formatCompactCount(video.viewCount)} views")
                    if (video.uploadDateText.isNotBlank()) add(video.uploadDateText)
                }
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun YouTubeSubscriptionsContent(
    subscriptions: List<YouTubeSubscription>,
    onSearchChannel: (YouTubeSubscription) -> Unit,
    onUnsubscribe: (YouTubeSubscription) -> Unit,
) {
    val colors = LocalPrismColors.current
    if (subscriptions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Subscriptions, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.yt_empty_subs_title), style = MaterialTheme.typography.titleMedium, color = colors.label)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.yt_empty_subs_body), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(subscriptions, key = { it.id }) { sub ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.groupedCard)
                    .clickable { onSearchChannel(sub) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryFill),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sub.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = sub.avatarUrl,
                            contentDescription = sub.channelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(sub.channelName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.label)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(sub.channelName, style = MaterialTheme.typography.titleMedium, color = colors.label)
                    if (sub.subscriberText.isNotBlank()) {
                        Text(sub.subscriberText, style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.secondaryFill)
                        .clickable { onUnsubscribe(sub) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.yt_subscribed), style = MaterialTheme.typography.labelMedium, color = colors.label)
                }
            }
        }
    }
}

@Composable
private fun YouTubeLibraryContent(
    savedVideos: List<YouTubeSavedVideo>,
    onVideoClick: (YouTubeSavedVideo) -> Unit,
) {
    val colors = LocalPrismColors.current
    if (savedVideos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.yt_empty_library_title), style = MaterialTheme.typography.titleMedium, color = colors.label)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.yt_empty_library_body), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(savedVideos, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.groupedCard)
                    .appleTactilePress(pressedScale = 0.98f)
                    .clickable { onVideoClick(item) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(126.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryLabel,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.liked) {
                            Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(14.dp))
                        }
                        if (item.saved) {
                            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = colors.systemBlue, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeSearchContent(
    query: String,
    results: List<YouTubeVideoItem>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVideoClick: (YouTubeVideoItem) -> Unit,
) {
    val colors = LocalPrismColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
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
                cursorBrush = SolidColor(YouTubeRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("yt_search_input"),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.yt_search_placeholder),
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
                CircularProgressIndicator(color = YouTubeRed)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.yt_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryLabel,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(results, key = { it.id }) { video ->
                    YouTubeVideoCard(video = video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
private fun YouTubeFloatingBottomBar(
    activeTab: YouTubeTab,
    onSelectTab: (YouTubeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPrismColors.current
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .height(64.dp),
        cornerRadius = 999.dp,
        elevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YouTubeTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val icon = when (tab) {
                    YouTubeTab.HOME -> Icons.Rounded.Home
                    YouTubeTab.SUBSCRIPTIONS -> Icons.Rounded.Subscriptions
                    YouTubeTab.LIBRARY -> Icons.Rounded.VideoLibrary
                    YouTubeTab.SEARCH -> Icons.Rounded.Search
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
                        .testTag("yt_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (selected) YouTubeRed else colors.label,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) YouTubeRed else colors.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
