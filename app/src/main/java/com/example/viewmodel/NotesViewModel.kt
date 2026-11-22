package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.PrismFirestoreRepository
import com.example.data.model.NoteAttachmentItem
import com.example.data.model.NoteChecklistItem
import com.example.data.model.NoteDocument
import com.example.data.model.NoteFolder
import com.example.data.model.NoteHighlightSpan
import com.example.data.model.NotesFilterOptions
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotesDestination {
    FOLDERS,
    NOTES_LIST,
    EDITOR,
}

data class NoteEditorDraft(
    val noteId: String = "",
    val folderId: String = "notes_default",
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val checklists: List<NoteChecklistItem> = emptyList(),
    val highlights: List<NoteHighlightSpan> = emptyList(),
    val attachments: List<NoteAttachmentItem> = emptyList(),
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val deleted: Boolean = false,
    val unlockedInSession: Boolean = false,
)

data class NotesUiState(
    val destination: NotesDestination = NotesDestination.FOLDERS,
    val selectedFolderId: String? = null, // null = All Notes, "recently_deleted" = Trash
    val selectedFolderTitle: String = "All Notes",
    val filterOptions: NotesFilterOptions = NotesFilterOptions(),
    val editorDraft: NoteEditorDraft? = null,
    val undoStack: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val showFiltersSheet: Boolean = false,
    val showNewFolderDialog: Boolean = false,
    val showAttachmentsHub: Boolean = false,
    val isEditingFolders: Boolean = false,
    val statusMessage: String? = null,
)

class NotesViewModel(
    private val firestore: PrismFirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<NoteFolder>> = firestore.observeFolders()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            _uiState.update { it.copy(statusMessage = "Unable to sync folders") }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<NoteDocument>> = firestore.observeNotes()
        .catch { e ->
            val firestoreError = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && e is CancellationException) throw e
            _uiState.update { it.copy(statusMessage = "Unable to sync notes") }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<String>> = allNotes
        .combine(MutableStateFlow(Unit)) { notes, _ ->
            notes.filter { !it.deleted }
                .flatMap { it.tags }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotes: StateFlow<List<NoteDocument>> = combine(allNotes, _uiState) { notes, state ->
        val f = state.filterOptions
        val base = when (state.selectedFolderId) {
            "recently_deleted" -> notes.filter { it.deleted }
            null -> notes.filter { !it.deleted }
            else -> notes.filter { !it.deleted && it.folderId == state.selectedFolderId }
        }
        base.filter { note ->
            val matchesQuery = f.query.isBlank() ||
                note.title.contains(f.query, ignoreCase = true) ||
                note.body.contains(f.query, ignoreCase = true) ||
                note.tags.any { it.contains(f.query.removePrefix("#"), ignoreCase = true) }
            val matchesTag = f.selectedTag == null ||
                note.tags.any { it.equals(f.selectedTag, ignoreCase = true) }
            val matchesPinned = !f.onlyPinned || note.pinned
            val matchesLocked = !f.onlyLocked || note.locked
            val matchesChecklists = !f.onlyWithChecklists || note.checklists.isNotEmpty()
            val attachments = note.parsedAttachments()
            val matchesDrawings = !f.onlyWithDrawings || attachments.any { it.type == "drawing" }
            val matchesScans = !f.onlyWithScans || attachments.any { it.type == "scan" }
            val matchesAttachments = !f.onlyWithAttachments || attachments.isNotEmpty()
            matchesQuery && matchesTag && matchesPinned && matchesLocked &&
                matchesChecklists && matchesDrawings && matchesScans && matchesAttachments
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            firestore.ensureDefaultFolders()
        }
    }

    fun openFolder(folderId: String?, title: String) {
        _uiState.update {
            it.copy(
                destination = NotesDestination.NOTES_LIST,
                selectedFolderId = folderId,
                selectedFolderTitle = title,
                filterOptions = NotesFilterOptions(),
            )
        }
    }

    fun openTagSmartView(tag: String) {
        _uiState.update {
            it.copy(
                destination = NotesDestination.NOTES_LIST,
                selectedFolderId = null,
                selectedFolderTitle = "#$tag",
                filterOptions = NotesFilterOptions(selectedTag = tag),
            )
        }
    }

    fun navigateBackToFolders() {
        _uiState.update {
            it.copy(
                destination = NotesDestination.FOLDERS,
                filterOptions = NotesFilterOptions(),
            )
        }
    }

    fun toggleEditFolders() {
        _uiState.update { it.copy(isEditingFolders = !it.isEditingFolders) }
    }

    fun setShowNewFolderDialog(show: Boolean) {
        _uiState.update { it.copy(showNewFolderDialog = show) }
    }

    fun createFolder(name: String, iconKey: String = "folder") {
        if (name.isBlank()) return
        viewModelScope.launch {
            firestore.createFolder(name, iconKey, sortOrder = (folders.value.size + 1).toLong())
                .onSuccess {
                    _uiState.update { it.copy(showNewFolderDialog = false, statusMessage = "Folder \"$name\" created") }
                }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            firestore.deleteFolder(folderId)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(filterOptions = it.filterOptions.copy(query = query)) }
    }

    fun updateFilterOptions(transform: (NotesFilterOptions) -> NotesFilterOptions) {
        _uiState.update { it.copy(filterOptions = transform(it.filterOptions)) }
    }

    fun clearFilters() {
        _uiState.update { it.copy(filterOptions = NotesFilterOptions()) }
    }

    fun setShowFiltersSheet(show: Boolean) {
        _uiState.update { it.copy(showFiltersSheet = show) }
    }

    fun setShowAttachmentsHub(show: Boolean) {
        _uiState.update { it.copy(showAttachmentsHub = show) }
    }

    fun openNoteForEditing(note: NoteDocument) {
        _uiState.update {
            it.copy(
                destination = NotesDestination.EDITOR,
                editorDraft = NoteEditorDraft(
                    noteId = note.id,
                    folderId = note.folderId,
                    title = note.title,
                    body = note.body,
                    tags = note.tags,
                    checklists = note.parsedChecklists(),
                    highlights = note.parsedHighlights(),
                    attachments = note.parsedAttachments(),
                    pinned = note.pinned,
                    locked = note.locked,
                    deleted = note.deleted,
                    unlockedInSession = !note.locked,
                ),
                undoStack = emptyList(),
            )
        }
    }

    fun startNewNote() {
        val targetFolder = _uiState.value.selectedFolderId
            ?.takeIf { it != "recently_deleted" }
            ?: "notes_default"
        val initialTag = _uiState.value.filterOptions.selectedTag
        _uiState.update {
            it.copy(
                destination = NotesDestination.EDITOR,
                editorDraft = NoteEditorDraft(
                    noteId = "",
                    folderId = targetFolder,
                    tags = if (initialTag != null) listOf(initialTag) else emptyList(),
                    unlockedInSession = true,
                ),
                undoStack = emptyList(),
            )
        }
    }

    fun updateDraftTitle(title: String) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(title = title))
        }
    }

    fun updateDraftBody(body: String) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            val nextUndo = (state.undoStack + draft.body).takeLast(25)
            state.copy(
                editorDraft = draft.copy(body = body),
                undoStack = nextUndo,
            )
        }
    }

    fun undoBodyChange() {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            val prev = state.undoStack.lastOrNull() ?: return@update state
            state.copy(
                editorDraft = draft.copy(body = prev),
                undoStack = state.undoStack.dropLast(1),
            )
        }
    }

    fun addTagToDraft(rawTag: String) {
        val clean = rawTag.trim().removePrefix("#").replace(" ", "_").take(28)
        if (clean.isBlank()) return
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            if (draft.tags.any { it.equals(clean, ignoreCase = true) }) return@update state
            state.copy(editorDraft = draft.copy(tags = draft.tags + clean))
        }
    }

    fun removeTagFromDraft(tag: String) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(tags = draft.tags - tag))
        }
    }

    fun addChecklistItem(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(checklists = draft.checklists + NoteChecklistItem(trimmed, false)))
        }
    }

    fun toggleChecklistItem(index: Int) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            val updated = draft.checklists.mapIndexed { i, item ->
                if (i == index) item.copy(checked = !item.checked) else item
            }
            state.copy(editorDraft = draft.copy(checklists = updated))
        }
    }

    fun removeChecklistItem(index: Int) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(checklists = draft.checklists.filterIndexed { i, _ -> i != index }))
        }
    }

    fun addHighlight(text: String, colorKey: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(highlights = draft.highlights + NoteHighlightSpan(trimmed, colorKey)))
        }
    }

    fun removeHighlight(index: Int) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(highlights = draft.highlights.filterIndexed { i, _ -> i != index }))
        }
    }

    fun addAttachment(attachment: NoteAttachmentItem) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(attachments = draft.attachments + attachment))
        }
    }

    fun removeAttachment(id: String) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(attachments = draft.attachments.filterNot { it.id == id }))
        }
    }

    fun toggleDraftPinned() {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(pinned = !draft.pinned))
        }
    }

    fun toggleDraftLocked() {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            val nextLocked = !draft.locked
            state.copy(
                editorDraft = draft.copy(
                    locked = nextLocked,
                    unlockedInSession = true,
                ),
            )
        }
    }

    fun unlockDraftInSession() {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = draft.copy(unlockedInSession = true))
        }
    }

    fun saveAndCloseEditor() {
        val draft = _uiState.value.editorDraft ?: run {
            _uiState.update { it.copy(destination = NotesDestination.NOTES_LIST) }
            return
        }
        val isEmptyNew = draft.noteId.isBlank() &&
            draft.title.isBlank() &&
            draft.body.isBlank() &&
            draft.checklists.isEmpty() &&
            draft.attachments.isEmpty() &&
            draft.highlights.isEmpty()

        if (isEmptyNew) {
            _uiState.update { it.copy(destination = NotesDestination.NOTES_LIST, editorDraft = null) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val extractedTags = Regex("#([a-zA-Z0-9_]{2,24})")
                .findAll("${draft.title} ${draft.body}")
                .map { it.groupValues[1] }
                .toList()
            val mergedTags = (draft.tags + extractedTags).distinct()
            val effectiveTitle = draft.title.trim().ifBlank {
                draft.body.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: "Untitled Note"
            }

            if (draft.noteId.isBlank()) {
                firestore.createNote(
                    folderId = draft.folderId,
                    title = effectiveTitle,
                    body = draft.body,
                    tags = mergedTags,
                    checklists = draft.checklists,
                    highlights = draft.highlights,
                    attachments = draft.attachments,
                    pinned = draft.pinned,
                    locked = draft.locked,
                )
            } else {
                firestore.updateNote(
                    noteId = draft.noteId,
                    folderId = draft.folderId,
                    title = effectiveTitle,
                    body = draft.body,
                    tags = mergedTags,
                    checklists = draft.checklists,
                    highlights = draft.highlights,
                    attachments = draft.attachments,
                    pinned = draft.pinned,
                    locked = draft.locked,
                    deleted = draft.deleted,
                )
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    destination = NotesDestination.NOTES_LIST,
                    editorDraft = null,
                )
            }
        }
    }

    fun deleteActiveDraft() {
        val draft = _uiState.value.editorDraft ?: return
        if (draft.noteId.isBlank()) {
            _uiState.update { it.copy(destination = NotesDestination.NOTES_LIST, editorDraft = null) }
            return
        }
        viewModelScope.launch {
            if (draft.deleted) {
                firestore.permanentlyDeleteNote(draft.noteId)
            } else {
                firestore.updateNoteFlags(draft.noteId, deleted = true, pinned = false)
            }
            _uiState.update { it.copy(destination = NotesDestination.NOTES_LIST, editorDraft = null) }
        }
    }

    fun togglePinNote(note: NoteDocument) {
        viewModelScope.launch {
            firestore.updateNoteFlags(note.id, pinned = !note.pinned)
        }
    }

    fun restoreDeletedNote(note: NoteDocument) {
        viewModelScope.launch {
            firestore.updateNoteFlags(note.id, deleted = false)
        }
    }

    fun permanentlyDeleteNote(note: NoteDocument) {
        viewModelScope.launch {
            firestore.permanentlyDeleteNote(note.id)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(private val firestore: PrismFirestoreRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(firestore) as T
    }
}
