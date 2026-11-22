package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.MusicRose
import com.example.ui.theme.NotesGoldDark
import com.example.ui.theme.YouTubeRed
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Silently attempts Credential Manager auto-sign-in for returning users.
 */
suspend fun attemptAutoSignIn(context: Context): Boolean {
    if (Firebase.auth.currentUser != null) return true
    return try {
        val webClientId = context.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(context, request)
        handleGoogleCredentialResult(result.credential)
    } catch (_: NoCredentialException) {
        false
    } catch (_: GetCredentialCancellationException) {
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Auth", "Auto sign-in skipped: ${e.message}")
        false
    }
}

/**
 * Interactive Google Sign-In flow via Jetpack Credential Manager.
 */
suspend fun signInWithGoogle(context: Context): Result<Unit> {
    return try {
        val webClientId = context.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        val result = credentialManager.getCredential(context, request)
        val ok = handleGoogleCredentialResult(result.credential)
        if (ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Unable to verify Google credentials."))
        }
    } catch (e: GetCredentialCancellationException) {
        Log.w("Auth", "Google sign-in cancelled by user", e)
        Result.failure(e)
    } catch (e: GetCredentialException) {
        Log.e("Auth", "Credential Manager sign-in failed", e)
        Result.failure(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Auth", "Unexpected Google sign-in error", e)
        Result.failure(e)
    }
}

private suspend fun handleGoogleCredentialResult(credential: androidx.credentials.Credential): Boolean {
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return try {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            Firebase.auth.signInWithCredential(firebaseCred).await()
            true
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("Auth", "Invalid Google ID token", e)
            false
        }
    }
    return false
}

/**
 * Non-cancellable sign-out clearing Credential Manager state before signing out of Firebase Auth.
 */
suspend fun signOutPrism(context: Context) {
    withContext(NonCancellable) {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("Auth", "Failed to clear credential state", e)
        }
        Firebase.auth.signOut()
    }
}

@Composable
fun PrismAuthScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalPrismColors.current

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(context)
    }

    LiquidBackdropHost(
        modifier = modifier.fillMaxSize(),
        overlay = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp),
                    cornerRadius = 36.dp,
                    elevation = 18.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_youtube),
                                contentDescription = stringResource(R.string.app_youtube),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_music),
                                contentDescription = stringResource(R.string.app_music),
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                            )
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_notes),
                                contentDescription = stringResource(R.string.app_notes),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.label,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.auth_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.secondaryLabel,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        LiquidGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("google_sign_in_button")
                                .appleTactilePress(enabled = !isLoading)
                                .let { mod ->
                                    mod.background(Color.Transparent)
                                },
                            cornerRadius = 28.dp,
                            accentTint = colors.systemBlue,
                            elevation = 10.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        Modifier.padding(horizontal = 16.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Surface(
                                    onClick = {
                                        if (!isLoading) {
                                            isLoading = true
                                            errorMessage = null
                                            scope.launch {
                                                val res = signInWithGoogle(context)
                                                isLoading = false
                                                res.exceptionOrNull()?.let { ex ->
                                                    if (ex !is GetCredentialCancellationException) {
                                                        errorMessage = ex.localizedMessage
                                                            ?: context.getString(R.string.auth_error_generic)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.2.dp,
                                                color = Color.White,
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = stringResource(R.string.auth_signing_in),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.auth_continue_google),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MusicRose,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = stringResource(R.string.auth_sync_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.tertiaryLabel,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    ) {
        // Luminous prismatic backdrop so the Liquid Glass auth card refracts vivid caustics
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MusicRose.copy(alpha = if (colors.isDark) 0.42f else 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.18f, h * 0.22f),
                        radius = w * 0.72f,
                    ),
                    radius = w * 0.72f,
                    center = Offset(w * 0.18f, h * 0.22f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.systemBlue.copy(alpha = if (colors.isDark) 0.38f else 0.28f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.85f, h * 0.35f),
                        radius = w * 0.78f,
                    ),
                    radius = w * 0.78f,
                    center = Offset(w * 0.85f, h * 0.35f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NotesGoldDark.copy(alpha = if (colors.isDark) 0.30f else 0.26f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.30f, h * 0.78f),
                        radius = w * 0.70f,
                    ),
                    radius = w * 0.70f,
                    center = Offset(w * 0.30f, h * 0.78f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            YouTubeRed.copy(alpha = if (colors.isDark) 0.24f else 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.82f, h * 0.82f),
                        radius = w * 0.56f,
                    ),
                    radius = w * 0.56f,
                    center = Offset(w * 0.82f, h * 0.82f),
                )
            }
        }
    }
}
