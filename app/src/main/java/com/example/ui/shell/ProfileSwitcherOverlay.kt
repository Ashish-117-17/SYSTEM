package com.example.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PrismApp
import com.example.ui.glass.AppleMotion
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSlider
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

/**
 * Circular Liquid Glass Profile Orb anchored in the top-right header of every mini-app.
 */
@Composable
fun ProfileGlassOrbButton(
    userInitials: String,
    userPhotoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPrismColors.current
    LiquidGlassIconButton(
        onClick = onClick,
        size = 42.dp,
        modifier = modifier.testTag("profile_switcher_button"),
    ) {
        if (!userPhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = userPhotoUrl,
                contentDescription = stringResource(R.string.cd_open_app_switcher),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.systemBlue.copy(alpha = 0.85f),
                                Color(0xFF5E5CE6),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userInitials.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * iOS 26 Liquid Glass morphing dropdown inspired by user's profile menu reference.
 * Switches between YouTube, Music, and Notes using the user's exact SVG icons,
 * plus live Light/Dark/Auto mode and real-time Frosted <-> Clear Liquid Glass Clarity Slider.
 */
@Composable
fun ProfileAppSwitcherOverlay(
    expanded: Boolean,
    activeApp: PrismApp,
    themeMode: ThemeMode,
    glassClarity: Float,
    userDisplayName: String,
    userEmail: String,
    onDismiss: () -> Unit,
    onSelectApp: (PrismApp) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onGlassClarityChange: (Float) -> Unit,
    onGlassClarityCommit: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPrismColors.current

    if (expanded) {
        BackHandler(onBack = onDismiss)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(AppleMotion.morphSpring()),
            exit = fadeOut(AppleMotion.morphSpring()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (colors.isDark) 0.42f else 0.16f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 60.dp, end = 16.dp, start = 16.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = scaleIn(
                    animationSpec = AppleMotion.morphSpring(),
                    initialScale = 0.72f,
                    transformOrigin = TransformOrigin(0.95f, 0.0f),
                ) + fadeIn(AppleMotion.morphSpring()),
                exit = scaleOut(
                    animationSpec = AppleMotion.morphSpring(),
                    targetScale = 0.80f,
                    transformOrigin = TransformOrigin(0.95f, 0.0f),
                ) + fadeOut(AppleMotion.morphSpring()),
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .width(292.dp)
                        .testTag("profile_switcher_dropdown"),
                    cornerRadius = 28.dp,
                    elevation = 20.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        // Mini-App Suite Switcher Section
                        Text(
                            text = stringResource(R.string.switcher_apps_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )

                        SwitcherAppRow(
                            app = PrismApp.YOUTUBE,
                            iconRes = R.drawable.ic_app_youtube,
                            selected = activeApp == PrismApp.YOUTUBE,
                            onClick = {
                                onSelectApp(PrismApp.YOUTUBE)
                                onDismiss()
                            },
                            testTag = "switch_app_youtube",
                        )

                        SwitcherAppRow(
                            app = PrismApp.MUSIC,
                            iconRes = R.drawable.ic_app_music,
                            selected = activeApp == PrismApp.MUSIC,
                            onClick = {
                                onSelectApp(PrismApp.MUSIC)
                                onDismiss()
                            },
                            testTag = "switch_app_music",
                        )

                        SwitcherAppRow(
                            app = PrismApp.NOTES,
                            iconRes = R.drawable.ic_app_notes,
                            selected = activeApp == PrismApp.NOTES,
                            onClick = {
                                onSelectApp(PrismApp.NOTES)
                                onDismiss()
                            },
                            testTag = "switch_app_notes",
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = colors.separator.copy(alpha = 0.55f),
                        )

                        // Appearance & Theme Mode
                        Text(
                            text = stringResource(R.string.switcher_appearance_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (colors.isDark) Color.White.copy(alpha = 0.08f)
                                    else Color.Black.copy(alpha = 0.06f),
                                )
                                .padding(3.dp)
                                .testTag("theme_mode_selector"),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val isSelected = themeMode == mode
                                val icon = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Rounded.Tune
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(
                                            if (isSelected) {
                                                if (colors.isDark) Color.White.copy(alpha = 0.18f)
                                                else Color.White.copy(alpha = 0.92f)
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .clickable { onSelectThemeMode(mode) }
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = mode.label,
                                        tint = if (isSelected) colors.label else colors.secondaryLabel,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) colors.label else colors.secondaryLabel,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        // Real-time Liquid Glass Clarity Slider (Frosted <-> Clear)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = colors.systemBlue,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.switcher_glass_clarity),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.label,
                                    )
                                }
                                Text(
                                    text = "${(glassClarity * 100f).roundToInt()}%",
                                    style = TabularNumberStyle.copy(
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = colors.secondaryLabel,
                                )
                            }

                            LiquidGlassSlider(
                                value = glassClarity,
                                onValueChange = onGlassClarityChange,
                                onValueChangeFinished = onGlassClarityCommit,
                                activeTrackColor = colors.systemBlue,
                                modifier = Modifier.testTag("glass_clarity_slider"),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.glass_frosted),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryLabel,
                                )
                                Text(
                                    text = stringResource(R.string.glass_clear),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryLabel,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = colors.separator.copy(alpha = 0.55f),
                        )

                        // Signed-in profile & Sign Out
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName.ifBlank { stringResource(R.string.account_default_name) },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (userEmail.isNotBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onDismiss()
                                        onSignOut()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("sign_out_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.action_sign_out),
                                    tint = colors.musicRose,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.action_sign_out),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.musicRose,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitcherAppRow(
    app: PrismApp,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    if (colors.isDark) Color.White.copy(alpha = 0.12f)
                    else Color.Black.copy(alpha = 0.06f)
                } else {
                    Color.Transparent
                },
            )
            .appleTactilePress(pressedScale = 0.97f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = app.title,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = colors.systemBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
