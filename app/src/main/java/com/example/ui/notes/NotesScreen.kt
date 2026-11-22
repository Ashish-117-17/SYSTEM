package com.example.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.NoteDocument
import com.example.data.model.NoteFolder
import com.example.data.model.NotesFilterOptions
import com.example.ui.glass.LiquidBackdropHost
import com.example.ui.glass.LiquidGlassIconButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.appleTactilePress
import com.example.ui.shell.ProfileGlassOrbButton
import com.example.ui.theme.LocalPrismColors
import com.example.viewmodel.NotesDestination
import com.example.viewmodel.NotesUiState
import com.example.viewmodel.NotesViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    notesLockPin: String,
    userInitials: String,
    userPhotoUrl: String?,
    onOpenProfileSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val filteredNotes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val colors = LocalPrismColors.current

    Box(modifier = modifier.fillMaxSize().background(colors.groupedCanvas)) {
        when (uiState.destination) {
            NotesDestination.FOLDERS -> {
                NotesFoldersHome(
                    folders = folders,
                    allNotes = allNotes,
                    allTags = allTags,
                    isEditingFolders = uiState.isEditingFolders,
                    userInitials = userInitials,
                    userPhotoUrl = userPhotoUrl,
                    onOpenProfileSwitcher = onOpenProfileSwitcher,
                    onToggleEdit = viewModel::toggleEditFolders,
                    onOpenFolder = viewModel::openFolder,
                    onOpenTag = viewModel::openTagSmartView,
                    onDeleteFolder = viewModel::deleteFolder,
                    onNewFolderClick = { viewModel.setShowNewFolderDialog(true) },
                    onStartNewNote = viewModel::startNewNote,
                    onOpenAttachmentsHub = { viewModel.setShowAttachmentsHub(true) },
                )
            }

            NotesDestination.NOTES_LIST -> {
                BackHandler(onBack = viewModel::navigateBackToFolders)
                NotesListView(
                    uiState = uiState,
                    notes = filteredNotes,
                    allTags = allTags,
                    userInitials = userInitials,
                    userPhotoUrl = userPhotoUrl,
                    onOpenProfileSwitcher = onOpenProfileSwitcher,
                    onBack = viewModel::navigateBackToFolders,
                    onSearchChange = viewModel::updateSearchQuery,
                    onSelectTag = { tag ->
                        viewModel.updateFilterOptions { f ->
                            f.copy(selectedTag = if (f.selectedTag == tag) null else tag)
                        }
                    },
                    onOpenFilters = { viewModel.setShowFiltersSheet(true) },
                    onClearFilters = viewModel::clearFilters,
                    onOpenNote = viewModel::openNoteForEditing,
                    onStartNewNote = viewModel::startNewNote,
                    onTogglePin = viewModel::togglePinNote,
                    onRestoreNote = viewModel::restoreDeletedNote,
                    onDeletePermanently = viewModel::permanentlyDeleteNote,
                )
            }

            NotesDestination.EDITOR -> {
                NoteEditorScreen(
                    viewModel = viewModel,
                    notesLockPin = notesLockPin,
                )
            }
        }

        if (uiState.showNewFolderDialog) {
            NewFolderDialog(
                onDismiss = { viewModel.setShowNewFolderDialog(false) },
                onCreate = { name -> viewModel.createFolder(name) },
            )
        }

        if (uiState.showFiltersSheet) {
            NotesFiltersSheet(
                options = uiState.filterOptions,
                onDismiss = { viewModel.setShowFiltersSheet(false) },
                onUpdate = viewModel::updateFilterOptions,
                onReset = viewModel::clearFilters,
            )
        }

        if (uiState.showAttachmentsHub) {
            NotesAttachmentsMediaHubSheet(
                notes = allNotes.filter { !it.deleted },
                onDismiss = { viewModel.setShowAttachmentsHub(false) },
                onOpenNote = { note ->
                    viewModel.setShowAttachmentsHub(false)
                    viewModel.openNoteForEditing(note)
                },
            )
        }
    }
}

@Composable
private fun NotesFoldersHome(
    folders: List<NoteFolder>,
    allNotes: List<NoteDocument>,
    allTags: List<String>,
    isEditingFolders: Boolean,
    userInitials: String,
    userPhotoUrl: String?,
    onOpenProfileSwitcher: () -> Unit,
    onToggleEdit: () -> Unit,
    onOpenFolder: (String?, String) -> Unit,
    onOpenTag: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onNewFolderClick: () -> Unit,
    onStartNewNote: () -> Unit,
    onOpenAttachmentsHub: () -> Unit,
) {
    val colors = LocalPrismColors.current
    val activeNotes = allNotes.filter { !it.deleted }
    val deletedNotes = allNotes.filter { it.deleted }

    LiquidBackdropHost(
        modifier = Modifier.fillMaxSize(),
        overlay = {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassIconButton(
                    onClick = onNewFolderClick,
                    size = 48.dp,
                    modifier = Modifier.testTag("notes_new_folder_button"),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = stringResource(R.string.notes_new_folder), tint = colors.notesGold)
                }

                LiquidGlassSurface(
                    modifier = Modifier
                        .appleTactilePress()
                        .clickable(onClick = onOpenAttachmentsHub),
                    cornerRadius = 999.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.notes_attachments_hub), style = MaterialTheme.typography.labelLarge, color = colors.label)
                    }
                }

                LiquidGlassIconButton(
                    onClick = onStartNewNote,
                    size = 48.dp,
                    modifier = Modifier.testTag("notes_compose_button"),
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.notes_new_note), tint = colors.notesGold, modifier = Modifier.size(24.dp))
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.notes_folders_title),
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.label,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LiquidGlassSurface(
                            modifier = Modifier
                                .appleTactilePress()
                                .clickable(onClick = onToggleEdit),
                            cornerRadius = 999.dp,
                        ) {
                            Text(
                                text = if (isEditingFolders) stringResource(R.string.action_done) else stringResource(R.string.action_edit),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.notesGold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                        ProfileGlassOrbButton(
                            userInitials = userInitials,
                            userPhotoUrl = userPhotoUrl,
                            onClick = onOpenProfileSwitcher,
                        )
                    }
                }
            }

            // Primary Cloud Folders Grouped Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.notes_section_cloud),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.groupedCard),
                    ) {
                        FolderRow(
                            icon = Icons.Rounded.FolderSpecial,
                            title = stringResource(R.string.notes_all_notes),
                            count = activeNotes.size,
                            canDelete = false,
                            onDelete = {},
                            onClick = { onOpenFolder(null, "All Notes") },
                            testTag = "notes_folder_all",
                        )

                        folders.forEach { folder ->
                            HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = colors.separator)
                            val count = activeNotes.count { it.folderId == folder.id }
                            FolderRow(
                                icon = Icons.Rounded.Folder,
                                title = folder.name,
                                count = count,
                                canDelete = isEditingFolders && !folder.systemFolder,
                                onDelete = { onDeleteFolder(folder.id) },
                                onClick = { onOpenFolder(folder.id, folder.name) },
                                testTag = "notes_folder_${folder.id}",
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = colors.separator)
                        FolderRow(
                            icon = Icons.Rounded.DeleteOutline,
                            title = stringResource(R.string.notes_recently_deleted),
                            count = deletedNotes.size,
                            canDelete = false,
                            onDelete = {},
                            onClick = { onOpenFolder("recently_deleted", "Recently Deleted") },
                            testTag = "notes_folder_deleted",
                        )
                    }
                }
            }

            // Smart Tags Section (matching 01_folders_home.webp)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.notes_tags_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    if (allTags.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.groupedCard)
                                .padding(18.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.notes_tags_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryLabel,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            allTags.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.groupedCard)
                                        .appleTactilePress()
                                        .clickable { onOpenTag(tag) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Rounded.Tag, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(16.dp))
                                    Text(tag, style = MaterialTheme.typography.labelLarge, color = colors.label)
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
private fun FolderRow(
    icon: ImageVector,
    title: String,
    count: Int,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.label,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryLabel,
        )
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = colors.musicRose)
            }
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.tertiaryLabel)
        }
    }
}

@Composable
private fun NotesListView(
    uiState: NotesUiState,
    notes: List<NoteDocument>,
    allTags: List<String>,
    userInitials: String,
    userPhotoUrl: String?,
    onOpenProfileSwitcher: () -> Unit,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectTag: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenNote: (NoteDocument) -> Unit,
    onStartNewNote: () -> Unit,
    onTogglePin: (NoteDocument) -> Unit,
    onRestoreNote: (NoteDocument) -> Unit,
    onDeletePermanently: (NoteDocument) -> Unit,
) {
    val colors = LocalPrismColors.current
    val isTrash = uiState.selectedFolderId == "recently_deleted"
    val pinnedNotes = notes.filter { it.pinned }
    val regularNotes = notes.filter { !it.pinned }

    LiquidBackdropHost(
        modifier = Modifier.fillMaxSize(),
        overlay = {
            // Floating bottom Liquid Glass search capsule + Compose button (matching 02_notes_list.webp)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    cornerRadius = 999.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = uiState.filterOptions.query,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.label),
                            cursorBrush = SolidColor(colors.notesGold),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("notes_search_input"),
                            decorationBox = { inner ->
                                if (uiState.filterOptions.query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.notes_search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.secondaryLabel,
                                    )
                                }
                                inner()
                            },
                        )
                        if (uiState.filterOptions.query.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search), tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (!isTrash) {
                    LiquidGlassIconButton(
                        onClick = onStartNewNote,
                        size = 50.dp,
                        modifier = Modifier.testTag("notes_list_compose_button"),
                    ) {
                        Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.notes_new_note), tint = colors.notesGold, modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LiquidGlassIconButton(onClick = onBack, size = 40.dp) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = colors.notesGold)
                        }
                        Column {
                            Text(uiState.selectedFolderTitle, style = MaterialTheme.typography.headlineMedium, color = colors.label)
                            Text("${notes.size} notes", style = MaterialTheme.typography.labelMedium, color = colors.secondaryLabel)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LiquidGlassIconButton(
                            onClick = onOpenFilters,
                            size = 40.dp,
                            accentTint = if (uiState.filterOptions.hasActiveFilters) colors.notesGold else null,
                            modifier = Modifier.testTag("notes_filters_button"),
                        ) {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = stringResource(R.string.notes_filters_title),
                                tint = if (uiState.filterOptions.hasActiveFilters) Color.White else colors.notesGold,
                            )
                        }
                        ProfileGlassOrbButton(
                            userInitials = userInitials,
                            userPhotoUrl = userPhotoUrl,
                            onClick = onOpenProfileSwitcher,
                        )
                    }
                }
            }

            // Smart Tag Filter Pills
            if (allTags.isNotEmpty() || uiState.filterOptions.hasActiveFilters) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.filterOptions.hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.notesGold)
                                    .clickable(onClick = onClearFilters)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(stringResource(R.string.notes_clear_filters), style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                        allTags.forEach { tag ->
                            val selected = uiState.filterOptions.selectedTag.equals(tag, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) colors.notesGold else colors.groupedCard)
                                    .clickable { onSelectTag(tag) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("#$tag", style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else colors.label)
                            }
                        }
                    }
                }
            }

            if (notes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.notes_empty_list_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.label,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.notes_empty_list_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryLabel,
                            )
                        }
                    }
                }
            }

            if (pinnedNotes.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.notes_section_pinned), style = MaterialTheme.typography.titleSmall, color = colors.secondaryLabel)
                }
                items(pinnedNotes, key = { it.id }) { note ->
                    NoteListCard(
                        note = note,
                        isTrash = isTrash,
                        onClick = { onOpenNote(note) },
                        onTogglePin = { onTogglePin(note) },
                        onRestore = { onRestoreNote(note) },
                        onDeletePermanently = { onDeletePermanently(note) },
                    )
                }
            }

            if (regularNotes.isNotEmpty()) {
                if (pinnedNotes.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.notes_section_notes), style = MaterialTheme.typography.titleSmall, color = colors.secondaryLabel)
                    }
                }
                items(regularNotes, key = { it.id }) { note ->
                    NoteListCard(
                        note = note,
                        isTrash = isTrash,
                        onClick = { onOpenNote(note) },
                        onTogglePin = { onTogglePin(note) },
                        onRestore = { onRestoreNote(note) },
                        onDeletePermanently = { onDeletePermanently(note) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteListCard(
    note: NoteDocument,
    isTrash: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    val colors = LocalPrismColors.current
    val dateText = remember(note.updatedAt) {
        val millis = note.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.groupedCard)
            .appleTactilePress(pressedScale = 0.985f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("note_card_${note.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (note.locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.notes_locked), tint = colors.notesGold, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(dateText, style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
                Text(
                    text = if (note.locked) stringResource(R.string.notes_locked_preview) else note.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.tags.isNotEmpty() && !note.locked) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.tags.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.notesGold,
                        )
                    }
                }
            }
        }

        if (isTrash) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.notes_restore), tint = colors.notesGold)
            }
            IconButton(onClick = onDeletePermanently) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = colors.musicRose)
            }
        } else {
            IconButton(onClick = onTogglePin) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.notes_pin),
                    tint = if (note.pinned) colors.notesGold else colors.tertiaryLabel,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_new_folder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.notes_folder_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.testTag("notes_new_folder_input"),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesFiltersSheet(
    options: NotesFilterOptions,
    onDismiss: () -> Unit,
    onUpdate: ((NotesFilterOptions) -> NotesFilterOptions) -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalPrismColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.groupedCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.notes_filters_title), style = MaterialTheme.typography.titleLarge, color = colors.label)
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.notes_clear_filters), color = colors.notesGold)
                }
            }

            FilterToggleRow(Icons.Rounded.PushPin, "Pinned Notes", options.onlyPinned) { v -> onUpdate { it.copy(onlyPinned = v) } }
            FilterToggleRow(Icons.Rounded.Lock, "Locked Notes", options.onlyLocked) { v -> onUpdate { it.copy(onlyLocked = v) } }
            FilterToggleRow(Icons.Rounded.CheckBox, "Checklists", options.onlyWithChecklists) { v -> onUpdate { it.copy(onlyWithChecklists = v) } }
            FilterToggleRow(Icons.Rounded.Draw, "Drawings & Sketches", options.onlyWithDrawings) { v -> onUpdate { it.copy(onlyWithDrawings = v) } }
            FilterToggleRow(Icons.Rounded.Scanner, "Scanned Documents", options.onlyWithScans) { v -> onUpdate { it.copy(onlyWithScans = v) } }
            FilterToggleRow(Icons.Rounded.AttachFile, "With Attachments", options.onlyWithAttachments) { v -> onUpdate { it.copy(onlyWithAttachments = v) } }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FilterToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.notesGold),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesAttachmentsMediaHubSheet(
    notes: List<NoteDocument>,
    onDismiss: () -> Unit,
    onOpenNote: (NoteDocument) -> Unit,
) {
    val colors = LocalPrismColors.current
    val allItems = notes.flatMap { note ->
        note.parsedAttachments().map { att -> note to att }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.groupedCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(stringResource(R.string.notes_attachments_hub), style = MaterialTheme.typography.titleLarge, color = colors.label)
            Spacer(modifier = Modifier.height(12.dp))
            if (allItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notes_attachments_empty), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryLabel)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(allItems) { (note, att) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.secondaryFill)
                                .clickable { onOpenNote(note) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if ((att.type == "photo" || att.type == "scan" || att.type == "drawing") && att.localPath.isNotBlank()) {
                                AsyncImage(
                                    model = if (att.localPath.startsWith("content://")) att.localPath else File(att.localPath),
                                    contentDescription = att.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            } else {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = colors.notesGold, modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(att.title.ifBlank { att.type.uppercase() }, style = MaterialTheme.typography.titleSmall, color = colors.label)
                                Text("In ${note.title.ifBlank { "Untitled Note" }}", style = MaterialTheme.typography.bodySmall, color = colors.secondaryLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}
