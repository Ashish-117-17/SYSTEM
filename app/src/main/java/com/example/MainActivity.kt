package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.PrismFirestoreRepository
import com.example.data.media.ActiveMediaSource
import com.example.data.media.LrclibRepository
import com.example.data.media.NewPipeRepository
import com.example.data.media.PrismMediaController
import com.example.data.model.PrismApp
import com.example.data.prefs.PrismPreferences
import com.example.ui.auth.PrismAuthScreen
import com.example.ui.auth.signOutPrism
import com.example.ui.glass.AppleMotion
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassStyle
import com.example.ui.glass.LocalLiquidGlassStyle
import com.example.ui.music.MusicScreen
import com.example.ui.notes.NotesScreen
import com.example.ui.shell.ProfileAppSwitcherOverlay
import com.example.ui.theme.PrismTheme
import com.example.ui.youtube.YouTubeScreen
import com.example.viewmodel.AppShellViewModel
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.NotesViewModel
import com.example.viewmodel.YouTubeViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var mediaController: PrismMediaController
    private lateinit var newPipeRepository: NewPipeRepository
    private lateinit var lrclibRepository: LrclibRepository
    private lateinit var firestoreRepository: PrismFirestoreRepository
    private lateinit var preferences: PrismPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mediaController = PrismMediaController(applicationContext)
        newPipeRepository = NewPipeRepository()
        lrclibRepository = LrclibRepository(newPipeRepository.httpClient)
        firestoreRepository = PrismFirestoreRepository(applicationContext)
        preferences = PrismPreferences(applicationContext)

        setContent {
            val shellViewModel: AppShellViewModel = viewModel(
                factory = AppShellViewModel.Factory(preferences),
            )
            val appearance by shellViewModel.state.collectAsStateWithLifecycle()

            var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    currentUser = auth.currentUser
                }
                Firebase.auth.addAuthStateListener(listener)
                onDispose {
                    Firebase.auth.removeAuthStateListener(listener)
                }
            }

            PrismTheme(themeMode = appearance.themeMode) {
                CompositionLocalProvider(
                    LocalLiquidGlassStyle provides LiquidGlassStyle(clarity = appearance.glassClarity),
                ) {
                    val user = currentUser
                    if (user == null) {
                        PrismAuthScreen()
                    } else {
                        key(user.uid) {
                            AuthenticatedPrismSuite(
                                user = user,
                                shellViewModel = shellViewModel,
                                newPipeRepository = newPipeRepository,
                                lrclibRepository = lrclibRepository,
                                firestoreRepository = firestoreRepository,
                                mediaController = mediaController,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::mediaController.isInitialized) {
            mediaController.release()
        }
        super.onDestroy()
    }
}

@Composable
private fun AuthenticatedPrismSuite(
    user: FirebaseUser,
    shellViewModel: AppShellViewModel,
    newPipeRepository: NewPipeRepository,
    lrclibRepository: LrclibRepository,
    firestoreRepository: PrismFirestoreRepository,
    mediaController: PrismMediaController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearance by shellViewModel.state.collectAsStateWithLifecycle()

    val youtubeViewModel: YouTubeViewModel = viewModel(
        key = "${user.uid}_youtube",
        factory = YouTubeViewModel.Factory(newPipeRepository, firestoreRepository, mediaController),
    )
    val musicViewModel: MusicViewModel = viewModel(
        key = "${user.uid}_music",
        factory = MusicViewModel.Factory(newPipeRepository, lrclibRepository, firestoreRepository, mediaController),
    )
    val notesViewModel: NotesViewModel = viewModel(
        key = "${user.uid}_notes",
        factory = NotesViewModel.Factory(firestoreRepository),
    )

    LaunchedEffect(youtubeViewModel, musicViewModel) {
        mediaController.onTrackEnded = { source ->
            when (source) {
                ActiveMediaSource.YOUTUBE -> youtubeViewModel.handleTrackEnded()
                ActiveMediaSource.MUSIC -> musicViewModel.handleTrackEnded()
                ActiveMediaSource.NONE -> Unit
            }
        }
    }

    var switcherExpanded by remember { mutableStateOf(false) }

    val displayName = user.displayName.orEmpty().ifBlank { user.email?.substringBefore("@").orEmpty() }
    val userInitials = remember(displayName) {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
            parts.isNotEmpty() -> parts[0].take(2)
            else -> "PR"
        }
    }
    val userPhotoUrl = user.photoUrl?.toString()

    // Outer LiquidBackdropHost lets the Profile App Switcher dropdown refract live pixels of the active mini-app
    LiquidBackdropHost(
        modifier = Modifier.fillMaxSize(),
        overlay = {
            ProfileAppSwitcherOverlay(
                expanded = switcherExpanded,
                activeApp = appearance.activeApp,
                themeMode = appearance.themeMode,
                glassClarity = appearance.glassClarity,
                userDisplayName = displayName,
                userEmail = user.email.orEmpty(),
                onDismiss = { switcherExpanded = false },
                onSelectApp = { nextApp -> shellViewModel.selectApp(nextApp) },
                onSelectThemeMode = { mode -> shellViewModel.selectThemeMode(mode) },
                onGlassClarityChange = { clarity -> shellViewModel.previewGlassClarity(clarity) },
                onGlassClarityCommit = { shellViewModel.commitGlassClarity() },
                onSignOut = {
                    mediaController.pause()
                    scope.launch { signOutPrism(context) }
                },
            )
        },
    ) {
        AnimatedContent(
            targetState = appearance.activeApp,
            transitionSpec = {
                (fadeIn(AppleMotion.appSwitchSpring()) + scaleIn(AppleMotion.appSwitchSpring(), initialScale = 0.96f)) togetherWith
                    (fadeOut(AppleMotion.appSwitchSpring()) + scaleOut(AppleMotion.appSwitchSpring(), targetScale = 1.02f))
            },
            label = "prism_app_switcher_transition",
        ) { activeApp ->
            when (activeApp) {
                PrismApp.YOUTUBE -> YouTubeScreen(
                    viewModel = youtubeViewModel,
                    userInitials = userInitials,
                    userPhotoUrl = userPhotoUrl,
                    onOpenProfileSwitcher = { switcherExpanded = true },
                )

                PrismApp.MUSIC -> MusicScreen(
                    viewModel = musicViewModel,
                    userInitials = userInitials,
                    userPhotoUrl = userPhotoUrl,
                    onOpenProfileSwitcher = { switcherExpanded = true },
                )

                PrismApp.NOTES -> NotesScreen(
                    viewModel = notesViewModel,
                    notesLockPin = appearance.notesLockPin,
                    userInitials = userInitials,
                    userPhotoUrl = userPhotoUrl,
                    onOpenProfileSwitcher = { switcherExpanded = true },
                )
            }
        }
    }
}
