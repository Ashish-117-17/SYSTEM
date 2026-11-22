package com.example.ui.notes

import android.content.Intent
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.NoteAttachmentItem
import com.example.data.model.formatDuration
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassPillGroup
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.theme.HighlightBlue
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.HighlightOrange
import com.example.ui.theme.HighlightPink
import com.example.ui.theme.HighlightPurple
import com.example.ui.theme.LocalPrismColors
import com.example.viewmodel.NotesViewModel
import java.io.File
import java.util.UUID

private val HighlightSwatches = listOf(
    "blue" to HighlightBlue,
    "mint" to HighlightMint,
    "orange" to HighlightOrange,
    "pink" to HighlightPink,
    "purple" to HighlightPurple,
)

private fun resolveHighlightColor(key: String): Color =
    HighlightSwatches.firstOrNull { it.first == key }?.second ?: HighlightBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NotesViewModel,
    notesLockPin: String,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.editorDraft ?: return
    val colors = LocalPrismColors.current

    var showFormatSheet by remember { mutableStateOf(false) }
    var showAttachmentsMenu by remember { mutableStateOf(false) }
    var showChecklistDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var showDocumentScanner by remember { mutableStateOf(false) }
    var showDrawingPad by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            viewModel.addAttachment(
                NoteAttachmentItem(
                    id = UUID.randomUUID().toString().take(12),
                    type = "photo",
                    title = "Photo",
                    localPath = uri.toString(),
                ),
            )
        }
    }

    BackHandler {
        viewModel.saveAndCloseEditor()
    }

    // Locked Note PIN Gate (matching 16_locked_secure_note.webp)
    if (draft.locked && !draft.unlockedInSession) {
        LockedNoteGate(
            expectedPin = notesLockPin,
            onBack = viewModel::saveAndCloseEditor,
            onUnlocked = viewModel::unlockDraftInSession,
        )
        return
    }

    LiquidBackdropHost(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
        overlay = {
            // Top Floating Liquid Glass Editor Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassIconButton(
                    onClick = viewModel::saveAndCloseEditor,
                    size = 42.dp,
                    modifier = Modifier.testTag("note_editor_back"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = colors.label)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiquidGlassPillGroup(horizontalPadding = 4.dp, verticalPadding = 2.dp) {
                        IconButton(
                            onClick = viewModel::undoBodyChange,
                            enabled = uiState.undoStack.isNotEmpty(),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.action_undo),
                                tint = if (uiState.undoStack.isNotEmpty()) colors.label else colors.tertiaryLabel,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleDraftPinned,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = stringResource(R.string.notes_pin),
                                tint = if (draft.pinned) colors.notesGold else colors.label,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleDraftLocked,
                            modifier = Modifier.size(36.dp).testTag("note_editor_lock_toggle"),
                        ) {
                            Icon(
                                imageVector = if (draft.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = stringResource(R.string.notes_lock_toggle),
                                tint = if (draft.locked) colors.notesGold else colors.label,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${draft.title}\n\n${draft.body}")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Note"))
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.action_share), tint = colors.label, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = viewModel::deleteActiveDraft,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = colors.musicRose, modifier = Modifier.size(19.dp))
                        }
                    }

                    LiquidGlassIconButton(
                        onClick = viewModel::saveAndCloseEditor,
                        size = 42.dp,
                        accentTint = colors.notesGold,
                        modifier = Modifier.testTag("note_editor_done"),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_done), tint = Color.White)
                    }
                }
            }

            // Bottom Floating Liquid Glass Toolbar (matching 07_note_reading_view.webp)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                LiquidGlassPillGroup(horizontalPadding = 12.dp, verticalPadding = 6.dp) {
                    IconButton(onClick = { showChecklistDialog = true }, modifier = Modifier.testTag("note_tool_checklist")) {
                        Icon(Icons.Rounded.CheckBox, contentDescription = stringResource(R.string.notes_add_checklist), tint = colors.label)
                    }
                    IconButton(onClick = { showFormatSheet = true }, modifier = Modifier.testTag("note_tool_format")) {
                        Icon(Icons.Rounded.FormatSize, contentDescription = stringResource(R.string.notes_format), tint = colors.label)
                    }
                    IconButton(onClick = { showAttachmentsMenu = true }, modifier = Modifier.testTag("note_tool_attachments")) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = stringResource(R.string.notes_attachments), tint = colors.label)
                    }
                    IconButton(onClick = { showDrawingPad = true }, modifier = Modifier.testTag("note_tool_draw")) {
                        Icon(Icons.Rounded.Draw, contentDescription = stringResource(R.string.notes_draw), tint = colors.label)
                    }
                    IconButton(onClick = { showVoiceRecorder = true }, modifier = Modifier.testTag("note_tool_audio")) {
                        Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.notes_record_audio), tint = colors.label)
                    }
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 74.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Note Title Input
            item {
                BasicTextField(
                    value = draft.title,
                    onValueChange = viewModel::updateDraftTitle,
                    textStyle = MaterialTheme.typography.headlineLarge.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.notesGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input"),
                    decorationBox = { inner ->
                        if (draft.title.isEmpty()) {
                            Text(stringResource(R.string.notes_title_placeholder), style = MaterialTheme.typography.headlineLarge, color = colors.tertiaryLabel)
                        }
                        inner()
                    },
                )
            }

            // Inline Smart Tags Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    draft.tags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.notesGold.copy(alpha = 0.16f))
                                .clickable { viewModel.removeTagFromDraft(tag) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("#$tag", style = MaterialTheme.typography.labelMedium, color = colors.notesGold, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(13.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.secondaryFill)
                            .clickable { showTagDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text("+ #Tag", style = MaterialTheme.typography.labelMedium, color = colors.secondaryLabel)
                    }
                }
            }

            // Note Rich Body Input
            item {
                BasicTextField(
                    value = draft.body,
                    onValueChange = viewModel::updateDraftBody,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.notesGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_body_input"),
                    decorationBox = { inner ->
                        if (draft.body.isEmpty()) {
                            Text(
                                text = stringResource(R.string.notes_body_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.tertiaryLabel,
                            )
                        }
                        inner()
                    },
                )
            }

            // Highlighted Callouts (5 Apple Notes Highlight Swatches)
            if (draft.highlights.isNotEmpty()) {
                itemsIndexed(draft.highlights) { idx, span ->
                    val swatch = resolveHighlightColor(span.colorKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(swatch.copy(alpha = 0.18f))
                            .border(1.dp, swatch.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = span.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.label,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeHighlight(idx) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = swatch, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Interactive Checklists
            if (draft.checklists.isNotEmpty()) {
                itemsIndexed(draft.checklists) { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.groupedCard)
                            .clickable { viewModel.toggleChecklistItem(idx) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (item.checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (item.checked) colors.notesGold else colors.secondaryLabel,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                            color = if (item.checked) colors.secondaryLabel else colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeChecklistItem(idx) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Inline Attachments (Scanned Docs, Photos, Drawings, Audio Player Cards)
            if (draft.attachments.isNotEmpty()) {
                itemsIndexed(draft.attachments) { _, att ->
                    InlineNoteAttachmentCard(
                        attachment = att,
                        onRemove = { viewModel.removeAttachment(att.id) },
                    )
                }
            }
        }
    }

    // Format Sheet (Title/Heading/Subheading/Body + 5 Apple Highlight Swatches)
    if (showFormatSheet) {
        NoteFormatSheet(
            onDismiss = { showFormatSheet = false },
            onInsertPrefix = { prefix ->
                val cur = draft.body
                val next = if (cur.isEmpty() || cur.endsWith("\n")) "$cur$prefix" else "$cur\n$prefix"
                viewModel.updateDraftBody(next)
                showFormatSheet = false
            },
            onAddHighlight = { text, colorKey ->
                viewModel.addHighlight(text, colorKey)
                showFormatSheet = false
            },
        )
    }

    // Attachments Action Menu Sheet (matching 10_attachments_action_menu.webp)
    if (showAttachmentsMenu) {
        ModalBottomSheet(onDismissRequest = { showAttachmentsMenu = false }, containerColor = colors.groupedCard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.notes_attachments), style = MaterialTheme.typography.titleLarge, color = colors.label)
                Spacer(modifier = Modifier.height(4.dp))
                AttachmentMenuRow(Icons.Rounded.CameraAlt, stringResource(R.string.notes_scan_documents)) {
                    showAttachmentsMenu = false
                    showDocumentScanner = true
                }
                AttachmentMenuRow(Icons.Rounded.PhotoLibrary, stringResource(R.string.notes_choose_photo)) {
                    showAttachmentsMenu = false
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                AttachmentMenuRow(Icons.Rounded.Mic, stringResource(R.string.notes_record_audio)) {
                    showAttachmentsMenu = false
                    showVoiceRecorder = true
                }
                AttachmentMenuRow(Icons.Rounded.Draw, stringResource(R.string.notes_signature_drawing)) {
                    showAttachmentsMenu = false
                    showDrawingPad = true
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showChecklistDialog) {
        SimpleInputDialog(
            title = stringResource(R.string.notes_add_checklist),
            placeholder = "Task or item...",
            onDismiss = { showChecklistDialog = false },
            onConfirm = {
                viewModel.addChecklistItem(it)
                showChecklistDialog = false
            },
        )
    }

    if (showTagDialog) {
        SimpleInputDialog(
            title = "Add Smart Tag",
            placeholder = "e.g. physics, design",
            onDismiss = { showTagDialog = false },
            onConfirm = {
                viewModel.addTagToDraft(it)
                showTagDialog = false
            },
        )
    }

    if (showVoiceRecorder) {
        VoiceRecorderModal(
            onDismiss = { showVoiceRecorder = false },
            onSaved = { filePath, durationSec ->
                viewModel.addAttachment(
                    NoteAttachmentItem(
                        id = UUID.randomUUID().toString().take(12),
                        type = "audio",
                        title = "Voice Memo",
                        localPath = filePath,
                        durationSec = durationSec,
                    ),
                )
                showVoiceRecorder = false
            },
        )
    }

    if (showDocumentScanner) {
        DocumentScannerModal(
            onDismiss = { showDocumentScanner = false },
            onCaptured = { filePath ->
                viewModel.addAttachment(
                    NoteAttachmentItem(
                        id = UUID.randomUUID().toString().take(12),
                        type = "scan",
                        title = "Scanned Document",
                        localPath = filePath,
                    ),
                )
                showDocumentScanner = false
            },
        )
    }

    if (showDrawingPad) {
        SignatureDrawingPadModal(
            onDismiss = { showDrawingPad = false },
            onSaved = { filePath ->
                viewModel.addAttachment(
                    NoteAttachmentItem(
                        id = UUID.randomUUID().toString().take(12),
                        type = "drawing",
                        title = "Sketch / Signature",
                        localPath = filePath,
                    ),
                )
                showDrawingPad = false
            },
        )
    }
}

@Composable
private fun InlineNoteAttachmentCard(
    attachment: NoteAttachmentItem,
    onRemove: () -> Unit,
) {
    val colors = LocalPrismColors.current
    if (attachment.type == "audio") {
        var isPlaying by remember { mutableStateOf(false) }
        var player by remember { mutableStateOf<MediaPlayer?>(null) }

        DisposableEffect(attachment.localPath) {
            onDispose {
                player?.release()
                player = null
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.groupedCard)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.notesGold)
                    .clickable {
                        if (isPlaying) {
                            player?.pause()
                            isPlaying = false
                        } else {
                            try {
                                val mp = player ?: MediaPlayer().apply {
                                    setDataSource(attachment.localPath)
                                    prepare()
                                    setOnCompletionListener { isPlaying = false }
                                }
                                player = mp
                                mp.start()
                                isPlaying = true
                            } catch (_: Exception) {
                                isPlaying = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.title.ifBlank { "Voice Memo" }, style = MaterialTheme.typography.titleSmall, color = colors.label)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(16.dp))
                    Text(formatDuration(attachment.durationSec), style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_delete), tint = colors.secondaryLabel)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.groupedCard)
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(attachment.title.ifBlank { attachment.type.uppercase() }, style = MaterialTheme.typography.labelLarge, color = colors.secondaryLabel)
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_delete), tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                }
            }
            if (attachment.localPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = if (attachment.localPath.startsWith("content://")) attachment.localPath else File(attachment.localPath),
                    contentDescription = attachment.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteFormatSheet(
    onDismiss: () -> Unit,
    onInsertPrefix: (String) -> Unit,
    onAddHighlight: (String, String) -> Unit,
) {
    val colors = LocalPrismColors.current
    var selectedSwatch by remember { mutableStateOf("blue") }
    var highlightText by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.groupedCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.notes_format), style = MaterialTheme.typography.titleLarge, color = colors.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Title" to "# ", "Heading" to "## ", "Bullet" to "• ", "Quote" to "> ").forEach { (label, prefix) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.secondaryFill)
                            .clickable { onInsertPrefix(prefix) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.label)
                    }
                }
            }

            Text(stringResource(R.string.notes_highlight_section), style = MaterialTheme.typography.titleSmall, color = colors.secondaryLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HighlightSwatches.forEach { (key, color) ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (key == selectedSwatch) Modifier.border(3.dp, colors.label, CircleShape)
                                else Modifier,
                            )
                            .clickable { selectedSwatch = key },
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = highlightText,
                    onValueChange = { highlightText = it },
                    placeholder = { Text("Highlighted callout text...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                LiquidGlassIconButton(
                    onClick = { if (highlightText.isNotBlank()) onAddHighlight(highlightText, selectedSwatch) },
                    size = 46.dp,
                    accentTint = resolveHighlightColor(selectedSwatch),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LockedNoteGate(
    expectedPin: String,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val colors = LocalPrismColors.current
    var pinInput by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        LiquidGlassIconButton(
            onClick = onBack,
            size = 42.dp,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = colors.label)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.notes_locked_title), style = MaterialTheme.typography.headlineSmall, color = colors.label)
            Text(stringResource(R.string.notes_locked_hint), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    pinInput = it
                    wrongPin = false
                },
                placeholder = { Text("Enter PIN (default 1234)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
            )
            if (wrongPin) {
                Text("Incorrect PIN", style = MaterialTheme.typography.bodySmall, color = colors.musicRose)
            }
            LiquidGlassSurface(
                modifier = Modifier
                    .appleTactilePress()
                    .clickable {
                        if (pinInput == expectedPin) onUnlocked() else wrongPin = true
                    },
                cornerRadius = 999.dp,
                accentTint = colors.notesGold,
            ) {
                Text(
                    text = stringResource(R.string.notes_view_note),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AttachmentMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.secondaryFill)
            .appleTactilePress(pressedScale = 0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = colors.label)
    }
}

@Composable
private fun SimpleInputDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
