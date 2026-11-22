package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core iOS 26 Light Surfaces
val IosLightBackground = Color(0xFFFFFFFF)
val IosLightGroupedBackground = Color(0xFFF2F2F7)
val IosLightCard = Color(0xFFFFFFFF)
val IosLightSecondaryFill = Color(0xFFE9E9EE)
val IosLightSeparator = Color(0xFFE5E5EA)
val IosLightLabel = Color(0xFF000000)
val IosLightSecondaryLabel = Color(0xFF6C6C70)
val IosLightTertiaryLabel = Color(0xFFAEAEB2)

// Core iOS 26 OLED Dark Surfaces
val IosDarkBackground = Color(0xFF000000)
val IosDarkGroupedBackground = Color(0xFF0A0A0C)
val IosDarkCard = Color(0xFF1C1C1E)
val IosDarkSecondaryFill = Color(0xFF2C2C2E)
val IosDarkSeparator = Color(0xFF38383A)
val IosDarkLabel = Color(0xFFFFFFFF)
val IosDarkSecondaryLabel = Color(0xFF98989F)
val IosDarkTertiaryLabel = Color(0xFF636366)

// Signature App Accents
val YouTubeRed = Color(0xFFFF0033)
val YouTubeRedGlow = Color(0xFFFF2D55)

val MusicRose = Color(0xFFFA2D48)
val MusicRoseCoral = Color(0xFFFD5163)

val NotesGoldLight = Color(0xFFD99B00)
val NotesGoldDark = Color(0xFFFFD60A)
val NotesAmberBadge = Color(0xFFE0A81C)

// Apple Notes Highlight Swatches (from user reference)
val HighlightBlue = Color(0xFF3478F6)
val HighlightMint = Color(0xFF4AC1A6)
val HighlightOrange = Color(0xFFEF8E38)
val HighlightPink = Color(0xFFE94662)
val HighlightPurple = Color(0xFFA839D9)

// iOS System Blue (Drawing / Selection / Dropdown Checkmark)
val IosSystemBlue = Color(0xFF007AFF)
val IosSystemBlueDark = Color(0xFF0A84FF)

// Playlist Generative Cover Gradients (modeled after Apple Music Create Playlist modal)
val PlaylistCoverGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF0C1B4D), Color(0xFF23408C), Color(0xFF7CA4D9)), // Midnight Sun blue
    listOf(Color(0xFFE7D3B5), Color(0xFF241B16), Color(0xFFDC9C6B)), // Fall metallic sweep
    listOf(Color(0xFFFA2D48), Color(0xFFFF7A59), Color(0xFFFFC168)), // Coral Sunset
    listOf(Color(0xFF1A1A1D), Color(0xFF3A362E), Color(0xFFE8E2D0)), // Obsidian Beam
    listOf(Color(0xFF103B34), Color(0xFF2FA889), Color(0xFF8BE2CC)), // Emerald Mist
    listOf(Color(0xFF3C165E), Color(0xFFA43CE0), Color(0xFFFF74B7)), // Neon Violet
    listOf(Color(0xFF101824), Color(0xFF385373), Color(0xFFC5D5EA)), // Steel Sky
)
