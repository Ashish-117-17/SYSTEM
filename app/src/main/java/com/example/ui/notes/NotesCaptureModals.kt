package com.example.ui.notes

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.formatDuration
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.TabularNumberStyle
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

/**
 * Voice Recording modal matching 13_voice_recording_waveform.webp:
 * requests RECORD_AUDIO at runtime, records real M4A via MediaRecorder,
 * and renders a live red amplitude waveform with golden playhead.
 */
@Composable
fun VoiceRecorderModal(
    onDismiss: () -> Unit,
    onSaved: (filePath: String, durationSec: Long) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val colors = LocalPrismColors.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    val amplitudes = remember { mutableStateListOf<Float>() }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isRecording) {
                elapsedSec = (System.currentTimeMillis() - start) / 1000L
                val rawAmp = try {
                    recorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                val normalized = (rawAmp / 22000f).coerceIn(0.08f, 1.0f)
                amplitudes.add(normalized)
                if (amplitudes.size > 72) amplitudes.removeAt(0)
                delay(100L)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun startRecording() {
        val dir = File(context.filesDir, "prism_audio").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            outputFile = file
            amplitudes.clear()
            elapsedSec = 0L
            isRecording = true
        } catch (_: Exception) {
            isRecording = false
        }
    }

    fun stopAndSave() {
        isRecording = false
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        val saved = outputFile
        if (saved != null && saved.exists()) {
            onSaved(saved.absolutePath, elapsedSec.coerceAtLeast(1L))
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassIconButton(onClick = onDismiss, size = 42.dp) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel), tint = colors.label)
            }
            Text(stringResource(R.string.notes_record_audio), style = MaterialTheme.typography.titleLarge, color = colors.label)
            Spacer(modifier = Modifier.size(42.dp))
        }

        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = formatDuration(elapsedSec),
                style = TabularNumberStyle.copy(
                    fontSize = MaterialTheme.typography.displayMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.label,
            )

            // Live Red Amplitude Waveform Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.groupedCard)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2f
                    val barCount = 60
                    val spacing = size.width / barCount
                    for (i in 0 until barCount) {
                        val amp = amplitudes.getOrNull(amplitudes.size - barCount + i) ?: 0.08f
                        val barH = (amp * size.height * 0.85f).coerceAtLeast(6f)
                        val x = i * spacing + spacing / 2f
                        drawLine(
                            color = Color(0xFFFF3B30),
                            start = Offset(x, midY - barH / 2f),
                            end = Offset(x, midY + barH / 2f),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    // Golden playhead line
                    drawLine(
                        color = colors.notesGold,
                        start = Offset(size.width - 2.dp.toPx(), 0f),
                        end = Offset(size.width - 2.dp.toPx(), size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            if (!hasPermission) {
                LiquidGlassSurface(
                    modifier = Modifier.clickable { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    cornerRadius = 999.dp,
                    accentTint = colors.notesGold,
                ) {
                    Text(
                        text = stringResource(R.string.permission_grant_mic),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(3.dp, colors.label.copy(alpha = 0.25f), CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                        .appleTactilePress()
                        .clickable {
                            if (isRecording) stopAndSave() else startRecording()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRecording) {
                        Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.action_stop), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
    }
}

/**
 * Real CameraX Document Scanner modal matching 11_document_camera_scanner.webp.
 */
@Composable
fun DocumentScannerModal(
    onDismiss: () -> Unit,
    onCaptured: (filePath: String) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalPrismColors.current

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPerm) launcher.launch(Manifest.permission.CAMERA)
    }

    var flashEnabled by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPerm) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.permission_camera_needed), color = Color.White, style = MaterialTheme.typography.titleMedium)
                LiquidGlassSurface(
                    modifier = Modifier.clickable { launcher.launch(Manifest.permission.CAMERA) },
                    cornerRadius = 999.dp,
                    accentTint = colors.notesGold,
                ) {
                    Text(stringResource(R.string.permission_grant_camera), color = Color.White, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
                }
            }
        }

        // Top Liquid Glass Camera Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LiquidGlassIconButton(onClick = onDismiss, forceDarkGlass = true, size = 44.dp) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White)
            }
            LiquidGlassIconButton(
                onClick = {
                    flashEnabled = !flashEnabled
                    imageCapture.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                },
                forceDarkGlass = true,
                size = 44.dp,
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = "Flash",
                    tint = if (flashEnabled) colors.notesGold else Color.White,
                )
            }
        }

        // Bottom Shutter Trigger
        if (hasCameraPerm) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .appleTactilePress()
                    .clickable {
                        val dir = File(context.filesDir, "prism_scans").apply { mkdirs() }
                        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onCaptured(file.absolutePath)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onDismiss()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.notes_scan_documents), tint = Color.Black, modifier = Modifier.size(30.dp))
            }
        }
    }
}

private data class InkStroke(
    val points: List<Offset>,
    val color: Color,
)

/**
 * Interactive Signature & Freehand Drawing Pad matching 15_signature_drawing_pad.webp.
 */
@Composable
fun SignatureDrawingPadModal(
    onDismiss: () -> Unit,
    onSaved: (filePath: String) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val colors = LocalPrismColors.current

    val strokes = remember { mutableStateListOf<InkStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var inkColor by remember { mutableStateOf(Color(0xFF111111)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBFBFD))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(inkColor) {
                    detectDragGestures(
                        onDragStart = { start -> currentPoints = listOf(start) },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints = currentPoints + change.position
                        },
                        onDragEnd = {
                            if (currentPoints.size > 1) {
                                strokes.add(InkStroke(currentPoints, inkColor))
                            }
                            currentPoints = emptyList()
                        },
                    )
                },
        ) {
            fun drawStroke(pts: List<Offset>, col: Color) {
                if (pts.size < 2) return
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x, pts[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = col,
                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            strokes.forEach { drawStroke(it.points, it.color) }
            drawStroke(currentPoints, inkColor)
        }

        // Top Floating Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassIconButton(onClick = onDismiss, size = 42.dp) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.Black)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Color(0xFF111111), Color(0xFF007AFF), Color(0xFFFA2D48), Color(0xFFD99B00)).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .then(if (inkColor == swatch) Modifier.border(3.dp, Color.Gray, CircleShape) else Modifier)
                            .clickable { inkColor = swatch },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassIconButton(onClick = { strokes.clear() }, size = 42.dp) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear", tint = Color.Black)
                }
                LiquidGlassIconButton(
                    onClick = {
                        val file = exportDrawingToPng(context, strokes)
                        if (file != null) onSaved(file.absolutePath) else onDismiss()
                    },
                    size = 42.dp,
                    accentTint = colors.systemBlue,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_done), tint = Color.White)
                }
            }
        }
    }
}

private fun exportDrawingToPng(context: android.content.Context, strokes: List<InkStroke>): File? {
    if (strokes.isEmpty()) return null
    return try {
        val width = 1080
        val height = 760
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)

        val allPts = strokes.flatMap { it.points }
        val minX = allPts.minOf { it.x }
        val maxX = allPts.maxOf { it.x }.coerceAtLeast(minX + 1f)
        val minY = allPts.minOf { it.y }
        val maxY = allPts.maxOf { it.y }.coerceAtLeast(minY + 1f)
        val scale = minOf((width - 120f) / (maxX - minX), (height - 120f) / (maxY - minY)).coerceAtMost(1.4f)

        strokes.forEach { stroke ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val p = android.graphics.Path()
            stroke.points.forEachIndexed { idx, pt ->
                val x = 60f + (pt.x - minX) * scale
                val y = 60f + (pt.y - minY) * scale
                if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            canvas.drawPath(p, paint)
        }

        val dir = File(context.filesDir, "prism_drawings").apply { mkdirs() }
        val file = File(dir, "sketch_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 95, out) }
        bmp.recycle()
        file
    } catch (_: Exception) {
        null
    }
}
