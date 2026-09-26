package com.markel.flowstate.feature.flow

import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.data.CompletionPhotoDraft
import com.markel.flowstate.core.data.TaskCompletionPhotoStore
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.CheckListRepository
import com.markel.flowstate.core.domain.CompletionCommitStatus
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.IdeaRepository
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskCompletion
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.CompleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.DeleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.ReopenTaskUseCase
import com.markel.flowstate.feature.flow.completion.TaskCompletionError
import com.markel.flowstate.feature.flow.completion.TaskCompletionUiState
import com.markel.flowstate.core.notifications.ReminderScheduler
import com.markel.flowstate.core.notifications.buildAlarmItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * FlowScreen coordinator.
 *
 * Responsibilities:
 *  - Control cross-screen UI state (isGridView)
 *  - Combine Tasks, Ideas and CheckLists into a sectioned UiState
 *  - Optimistic reordering per section with DB persistence on drag end
 *
 * Future extensibility note:
 *  Cross-section reordering (e.g. Task → Idea conversion) can be added by
 *  introducing a shared reorder method that mutates multiple local state flows
 *  and calls the corresponding repository operations in a single transaction.
*/
@HiltViewModel
class FlowViewModel @Inject constructor(
    // Repositories required for the unified grid
    private val taskRepository: TaskRepository,
    private val ideaRepository: IdeaRepository,
    private val checkListRepository: CheckListRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val reopenTaskUseCase: ReopenTaskUseCase,
    private val completionPhotoStore: TaskCompletionPhotoStore,
    private val applicationScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,

) : ViewModel(), DefaultLifecycleObserver {

    // ── Optimistic local state ─────────────────────────────────
    private val _uiState = MutableStateFlow<FlowUiState>(FlowUiState.Loading)
    val uiState: StateFlow<FlowUiState> = _uiState

    private val _completionUiState = MutableStateFlow<TaskCompletionUiState>(TaskCompletionUiState.Idle)
    val completionUiState: StateFlow<TaskCompletionUiState> = _completionUiState

    /** Keeps the just-completed row visible while the success feedback runs. */
    private val _retainedCompletionTask = MutableStateFlow<Task?>(null)
    private var completionEventId = 0L
    private val completionDraftRestoreJob = viewModelScope.launch {
        restoreCompletionDraft()
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    // Backed by a MutableStateFlow so we can force a re-evaluation on resume without waiting for a DB emission
    private val _bannerVisible = MutableStateFlow(false)
    val showReminderBanner: StateFlow<Boolean> = _bannerVisible

    // Tracks whether the permission was missing the last time we checked
    // When it flips from false → true (user just granted it), we reschedule
    private var wasPermissionMissing = false

    // ── Deferred deletion / Undo ──────────────────────────────────────────────

    /** Tasks that have been swiped-to-dismiss but not yet permanently deleted. */
    private val _pendingUndoTasks = MutableStateFlow<Map<Int, Task>>(emptyMap())

    /** Whether the undo FAB should be visible – true while any deletion is pending. */
    val showUndoButton: StateFlow<Boolean> = _pendingUndoTasks
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Per-task monotonic counter incremented each time the user swipes-to-delete.
     * Used as part of the `LazyColumn` item key so that, after an undo, Compose
     * creates a **fresh** composable with a fresh `SwipeToDismissBoxState` instead
     * of restoring the stale `EndToStart` saved-state that would immediately
     * trigger `onDelete()` again.
     */
    private val _taskDeleteVersions = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val taskDeleteVersions: StateFlow<Map<Int, Int>> = _taskDeleteVersions

    /**
     * One [Job] per pending task, representing the 3.5 s countdown to permanent
     * deletion.  When the job completes the task is removed from the DB; when the
     * user taps undo the job is canceled.
     */
    private val deleteJobs = mutableMapOf<Int, Job>()

    private val _selectedCategoryId = MutableStateFlow<Int?>(Category.GENERAL_ID)
    val generalCategoryName: StateFlow<String?> = userPreferencesRepository.generalCategoryName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Tracks whether we have already applied the persisted "last visited
     * category" from DataStore. We only want to apply it ONCE at startup —
     * after that, the user's in-session selections take over, and we just
     * persist them as they happen.
     */
    private var lastCategoryRestored = false

    // ── Init: combine repos + pending-filter ──────────────────────────────────
    init {
        // Restore the last visited categoom DataStory frre once at startup.
        viewModelScope.launch {
            userPreferencesRepository.lastCategoryId.collect { saved ->
                if (!lastCategoryRestored && saved != null) {
                    _selectedCategoryId.value = saved
                    lastCategoryRestored = true
                }
            }
        }

        viewModelScope.launch {
            val coreDataFlow = combine(
                taskRepository.getTasks(),
                ideaRepository.getIdeas(),
                checkListRepository.getLists(),
                categoryRepository.getCategories()
            ) { tasks, ideas, lists, categories ->
                // Combine these into a temporary data class
                CoreData(tasks, ideas, lists, categories)
            }
            // Combine like this to not lose type-safe navigation
            combine(
                coreDataFlow,
                userPreferencesRepository.categoriesEnabled,
                _pendingUndoTasks,
                _selectedCategoryId,
                _retainedCompletionTask,
            ) { coreData, categoriesEnabled, pendingMap, selectedCategoryId, retainedTask ->

                val pendingIds = pendingMap.keys

                // If the persisted/selected category no longer exists, fall back to General
                // and persist the correction so we don't keep pointing at a phantom id.
                val validSelectedId = if (categoriesEnabled && selectedCategoryId != null) {
                    val exists = coreData.categories.any { it.id == selectedCategoryId }
                    if (!exists) {
                        Category.GENERAL_ID.also {
                            viewModelScope.launch {
                                userPreferencesRepository.saveLastCategoryId(it)
                            }
                        }
                    } else {
                        selectedCategoryId
                    }
                } else {
                    selectedCategoryId
                }
                // Keep the local StateFlow in sync with the correction so the
                // tab row indicator lands on General too.
                if (validSelectedId != selectedCategoryId) {
                    _selectedCategoryId.value = validSelectedId
                }

                // Filter tasks: exclude done + pending deletions, then filter by category if enabled
                val filteredTasks = coreData.tasks
                    .filter { (!it.isDone || it.id == retainedTask?.id) && it.id !in pendingIds }
                    .map { task -> if (task.id == retainedTask?.id) retainedTask else task }
                    .let { tasks ->
                        if (retainedTask != null &&
                            retainedTask.id !in pendingIds &&
                            tasks.none { it.id == retainedTask.id }
                        ) {
                            (tasks + retainedTask).sortedBy(Task::position)
                        } else {
                            tasks
                        }
                    }
                    .let { filtered ->
                        if (categoriesEnabled) {
                            filtered.filter { it.categoryId == validSelectedId }
                        } else filtered
                    }

                val filteredCompletedTasks = coreData.tasks
                    .asSequence()
                    .filter { it.isDone && it.id != retainedTask?.id && it.id !in pendingIds }
                    .let { tasks ->
                        if (categoriesEnabled) tasks.filter { it.categoryId == validSelectedId }
                        else tasks
                    }
                    .sortedByDescending { it.completedAt ?: Long.MIN_VALUE }
                    .toList()

                // Filter ideas by category if enabled
                val filteredIdeas = coreData.ideas.let { list ->
                    if (categoriesEnabled) {
                        list.filter { it.categoryId == validSelectedId }
                    } else list
                }

                // Filter checklists by category if enabled
                val filteredLists = coreData.lists.let { list ->
                    if (categoriesEnabled) {
                        list.filter { it.categoryId == selectedCategoryId }
                    } else list
                }

                // When categories are enabled but no category is selected, default to General (GENERAL_ID).
                // When categories are disabled, the selectedCategoryId is irrelevant (no tab row is shown),
                // but we still report GENERAL_ID so any consumer that reads it gets a valid id.
                val effectiveSelectedId = if (categoriesEnabled) validSelectedId else Category.GENERAL_ID

                // Per-category pending task counts for the tab badges.
                val pendingTaskCounts = coreData.tasks
                    .asSequence()
                    .filter { !it.isDone && it.id !in pendingIds}
                    .groupingBy { it.categoryId }
                    .eachCount()

                val completedIds = coreData.tasks.asSequence()
                    .filter(Task::isDone)
                    .mapTo(mutableSetOf(), Task::id)
                retainedTask?.takeIf(Task::isDone)?.let { completedIds += it.id }
                val totalCount = coreData.tasks.size
                val completedCount = completedIds.size.coerceAtMost(totalCount)

                FlowUiState.Success(
                    tasks = filteredTasks,
                    completedTasks = filteredCompletedTasks,
                    ideas = filteredIdeas,
                    checkLists = filteredLists,
                    categories = coreData.categories,
                    selectedCategoryId = effectiveSelectedId,
                    categoriesEnabled = categoriesEnabled,
                    pendingTaskCounts = pendingTaskCounts,
                    completedCount = completedCount,
                    remainingCount = (totalCount - completedCount).coerceAtLeast(0),
                    totalCount = totalCount,
                    retainedTaskId = retainedTask?.id,
                )
            }.collect { state ->
                _uiState.value = state
                refreshBanner()
            }
        }
    }

    // ── Completion check-in ──────────────────────────────────────────────────

    fun requestCompletion(task: Task) {
        if (task.isDone || _completionUiState.value !is TaskCompletionUiState.Idle) return
        publishCompletionState(TaskCompletionUiState.Draft(task = task))
    }

    fun updateCompletionNote(note: String) {
        val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return
        if (state.isSubmitting) return
        publishCompletionState(state.copy(note = note, error = null))
    }

    fun prepareCameraCapture(): Uri? {
        val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return null
        if (state.isSubmitting || state.isImporting) return null
        return runCatching { completionPhotoStore.createCameraDraft() }
            .onSuccess { draft ->
                publishCompletionState(state.copy(
                    pendingCamera = draft,
                    isImporting = true,
                    error = null,
                ))
            }
            .onFailure {
                publishCompletionState(state.copy(error = TaskCompletionError.PhotoImportFailed))
            }
            .getOrNull()
            ?.captureUri
    }

    fun onCameraCaptureResult(succeeded: Boolean) {
        viewModelScope.launch {
            completionDraftRestoreJob.join()
            val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return@launch
            val cameraDraft = state.pendingCamera ?: return@launch
            if (!succeeded) {
                discardCompletionDraft(cameraDraft)
                publishCompletionState(
                    state.copy(pendingCamera = null, isImporting = false),
                )
                return@launch
            }
            runCatching { completionPhotoStore.normalizeCameraDraft(cameraDraft) }
                .onSuccess { normalized ->
                    val latest = _completionUiState.value as? TaskCompletionUiState.Draft
                    if (latest?.pendingCamera?.id == normalized.id) {
                        discardCompletionDraft(latest.photo)
                        publishCompletionState(latest.copy(
                            photo = normalized,
                            pendingCamera = null,
                            isImporting = false,
                            error = null,
                        ))
                    } else {
                        completionPhotoStore.deleteDraft(normalized)
                    }
                }
                .onFailure {
                    discardCompletionDraft(cameraDraft)
                    val latest = _completionUiState.value as? TaskCompletionUiState.Draft
                    if (latest?.pendingCamera?.id == cameraDraft.id) {
                        publishCompletionState(latest.copy(
                            pendingCamera = null,
                            isImporting = false,
                            error = TaskCompletionError.PhotoImportFailed,
                        ))
                    }
                }
        }
    }

    fun importCompletionPhoto(uri: Uri) {
        viewModelScope.launch {
            completionDraftRestoreJob.join()
            val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return@launch
            if (state.isSubmitting || state.isImporting) return@launch
            publishCompletionState(state.copy(isImporting = true, error = null))
            runCatching { completionPhotoStore.importFromUri(uri) }
                .onSuccess { imported ->
                    val latest = _completionUiState.value as? TaskCompletionUiState.Draft
                    if (latest != null && latest.task.id == state.task.id && latest.isImporting) {
                        discardCompletionDraft(latest.photo)
                        publishCompletionState(latest.copy(
                            photo = imported,
                            isImporting = false,
                            error = null,
                        ))
                    } else {
                        completionPhotoStore.deleteDraft(imported)
                    }
                }
                .onFailure {
                    val latest = _completionUiState.value as? TaskCompletionUiState.Draft
                    if (latest != null && latest.task.id == state.task.id) {
                        publishCompletionState(latest.copy(
                            isImporting = false,
                            error = TaskCompletionError.PhotoImportFailed,
                        ))
                    }
                }
        }
    }

    fun removeCompletionPhoto() {
        val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return
        if (state.isSubmitting || state.isImporting) return
        discardCompletionDraft(state.photo)
        publishCompletionState(state.copy(photo = null, error = null))
    }

    fun dismissCompletion() {
        val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return
        if (state.isSubmitting) return
        discardCompletionDraft(state.photo)
        discardCompletionDraft(state.pendingCamera)
        publishCompletionState(TaskCompletionUiState.Idle)
    }

    fun submitCompletion(includeRecord: Boolean = true) {
        val state = _completionUiState.value as? TaskCompletionUiState.Draft ?: return
        if (state.isSubmitting || state.isImporting) return
        val progressBeforeCommit = _uiState.value as? FlowUiState.Success
        publishCompletionState(state.copy(isSubmitting = true, error = null))

        viewModelScope.launch {
            var promotedPhotoId: String? = null
            var optimisticCompletion: TaskCompletion? = null
            val commitStatus = try {
                val includedPhoto = state.photo.takeIf { includeRecord }
                promotedPhotoId = includedPhoto?.let { completionPhotoStore.promote(it) }
                val note = state.note.trim().takeIf { includeRecord && it.isNotEmpty() }

                optimisticCompletion = TaskCompletion(
                    taskId = state.task.id,
                    note = note?.trim()?.takeIf(String::isNotEmpty),
                    photoId = promotedPhotoId,
                    completedAt = System.currentTimeMillis(),
                )
                // Reserve the row before Room emits so it cannot disappear between the
                // transaction commit and the success animation. Keep it visually pending
                // until persistence succeeds.
                _retainedCompletionTask.value = state.task

                completeTaskUseCase(
                    taskId = state.task.id,
                    note = note,
                    photoId = promotedPhotoId,
                )
            } catch (cancelled: CancellationException) {
                val shouldDeletePromotedPhoto = promotedPhotoId?.let { photoId ->
                    try {
                        withContext(NonCancellable) {
                            taskRepository.getTaskById(state.task.id)?.completion?.photoId != photoId
                        }
                    } catch (_: Exception) {
                        // If reconciliation itself fails, preserving a possible orphan is
                        // safer than deleting a file an already-committed row may reference.
                        false
                    }
                } ?: false
                if (shouldDeletePromotedPhoto) {
                    completionPhotoStore.deletePhoto(promotedPhotoId)
                }
                _retainedCompletionTask.value = null
                throw cancelled
            } catch (_: Exception) {
                completionPhotoStore.deletePhoto(promotedPhotoId)
                _retainedCompletionTask.value = null
                publishCompletionState(state.copy(
                    isSubmitting = false,
                    error = TaskCompletionError.SaveFailed,
                ))
                return@launch
            }

            when (commitStatus) {
                CompletionCommitStatus.Completed -> {
                    // From this point on the database owns promotedPhotoId. Ancillary
                    // work must never turn a committed completion into a false failure
                    // or delete the file referenced by its completion row.
                    if (promotedPhotoId == null) {
                        // A restored draft can already have an orphaned promoted copy if
                        // the previous process died between file promotion and Room commit.
                        completionPhotoStore.deletePhoto(state.photo?.id)
                    }
                    completionPhotoStore.deleteDraft(state.photo)
                    completionPhotoStore.deleteDraft(state.pendingCamera)
                    clearCompletionDraftSnapshot()
                    try {
                        reminderScheduler.cancel(state.task.id)
                        state.task.subTasks.forEach { reminderScheduler.cancelSubTask(it.id) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Reminder cleanup is best-effort after the atomic commit.
                    }

                    val persisted = try {
                        taskRepository.getTaskById(state.task.id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    val completedTask = persisted ?: requireNotNull(optimisticCompletion).let { completion ->
                        state.task.copy(
                            isDone = true,
                            completedAt = completion.completedAt,
                            reminderTime = null,
                            subTasks = state.task.subTasks.map { it.copy(reminderTime = null) },
                            completion = completion,
                        )
                    }
                    _retainedCompletionTask.value = completedTask
                    val total = progressBeforeCommit?.totalCount?.coerceAtLeast(1) ?: 1
                    val completed = ((progressBeforeCommit?.completedCount ?: 0) + 1)
                        .coerceIn(1, total)
                    val eventId = ++completionEventId
                    publishCompletionState(TaskCompletionUiState.Celebrating(
                        eventId = eventId,
                        task = completedTask,
                        completedCount = completed,
                        totalCount = total,
                    ))
                    delay(COMPLETION_CELEBRATION_MILLIS)
                    val latest = _completionUiState.value
                    if (latest is TaskCompletionUiState.Celebrating && latest.eventId == eventId) {
                        _retainedCompletionTask.value = null
                        publishCompletionState(TaskCompletionUiState.Idle)
                    }
                }

                CompletionCommitStatus.AlreadyCompleted -> {
                    completionPhotoStore.deletePhoto(promotedPhotoId)
                    _retainedCompletionTask.value = null
                    publishCompletionState(state.copy(
                        isSubmitting = false,
                        error = TaskCompletionError.AlreadyCompleted,
                    ))
                }

                CompletionCommitStatus.NotFound -> {
                    completionPhotoStore.deletePhoto(promotedPhotoId)
                    _retainedCompletionTask.value = null
                    publishCompletionState(state.copy(
                        isSubmitting = false,
                        error = TaskCompletionError.TaskUnavailable,
                    ))
                }
            }
        }
    }

    fun reopenTask(task: Task) {
        if (!task.isDone || _completionUiState.value !is TaskCompletionUiState.Idle) return
        viewModelScope.launch { reopenTaskUseCase(task.id) }
    }

    fun resolveCompletionPhoto(photoId: String?) = completionPhotoStore.resolvePhoto(photoId)

    private suspend fun restoreCompletionDraft() {
        val taskId = savedStateHandle.get<Int>(COMPLETION_TASK_ID_KEY) ?: return
        val note = savedStateHandle.get<String>(COMPLETION_NOTE_KEY).orEmpty()
        val photoId = savedStateHandle.get<String>(COMPLETION_PHOTO_DRAFT_ID_KEY)
        val pendingCameraId = savedStateHandle.get<String>(COMPLETION_PENDING_CAMERA_ID_KEY)
        val task = try {
            taskRepository.getTaskById(taskId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the snapshot for a later recreation if Room is temporarily unavailable.
            return
        }

        if (task == null) {
            completionPhotoStore.deleteDraft(photoId?.let(completionPhotoStore::restorePhotoDraft))
            completionPhotoStore.deleteDraft(
                pendingCameraId?.let(completionPhotoStore::restoreCameraDraft),
            )
            completionPhotoStore.deletePhoto(photoId)
            completionPhotoStore.deletePhoto(pendingCameraId)
            clearCompletionDraftSnapshot()
            return
        }

        if (task.isDone) {
            completionPhotoStore.deleteDraft(photoId?.let(completionPhotoStore::restorePhotoDraft))
            completionPhotoStore.deleteDraft(
                pendingCameraId?.let(completionPhotoStore::restoreCameraDraft),
            )
            clearCompletionDraftSnapshot()
            return
        }

        var restoredPhoto = photoId?.let(completionPhotoStore::restorePhotoDraft)
        var restoredCamera = pendingCameraId?.let(completionPhotoStore::restoreCameraDraft)
        var restoreError = if (photoId != null && restoredPhoto == null) {
            completionPhotoStore.deletePhoto(photoId)
            TaskCompletionError.PhotoImportFailed
        } else {
            null
        }

        val cameraDraft = restoredCamera
        if (pendingCameraId != null && cameraDraft == null) {
            completionPhotoStore.deletePhoto(pendingCameraId)
            restoreError = TaskCompletionError.PhotoImportFailed
        } else if (cameraDraft != null && cameraDraft.file.length() > 0L) {
            restoredCamera = try {
                val normalized = completionPhotoStore.normalizeCameraDraft(cameraDraft)
                discardCompletionDraft(restoredPhoto)
                restoredPhoto = normalized
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                discardCompletionDraft(cameraDraft)
                restoreError = TaskCompletionError.PhotoImportFailed
                null
            }
        }

        // A new interaction may have replaced this snapshot while Room was read.
        if (savedStateHandle.get<Int>(COMPLETION_TASK_ID_KEY) != taskId ||
            _completionUiState.value !is TaskCompletionUiState.Idle
        ) {
            return
        }

        publishCompletionState(
            TaskCompletionUiState.Draft(
                task = task,
                note = note,
                photo = restoredPhoto,
                pendingCamera = restoredCamera,
                isImporting = restoredCamera != null,
                error = restoreError,
            ),
        )
    }

    private fun publishCompletionState(state: TaskCompletionUiState) {
        _completionUiState.value = state
        if (state is TaskCompletionUiState.Draft) {
            savedStateHandle[COMPLETION_TASK_ID_KEY] = state.task.id
            savedStateHandle[COMPLETION_NOTE_KEY] = state.note
            if (state.photo == null) {
                savedStateHandle.remove<String>(COMPLETION_PHOTO_DRAFT_ID_KEY)
            } else {
                savedStateHandle[COMPLETION_PHOTO_DRAFT_ID_KEY] = state.photo.id
            }
            if (state.pendingCamera == null) {
                savedStateHandle.remove<String>(COMPLETION_PENDING_CAMERA_ID_KEY)
            } else {
                savedStateHandle[COMPLETION_PENDING_CAMERA_ID_KEY] = state.pendingCamera.id
            }
        } else {
            clearCompletionDraftSnapshot()
        }
    }

    private fun clearCompletionDraftSnapshot() {
        savedStateHandle.remove<Int>(COMPLETION_TASK_ID_KEY)
        savedStateHandle.remove<String>(COMPLETION_NOTE_KEY)
        savedStateHandle.remove<String>(COMPLETION_PHOTO_DRAFT_ID_KEY)
        savedStateHandle.remove<String>(COMPLETION_PENDING_CAMERA_ID_KEY)
    }

    /** Deletes both retryable cache data and any uncommitted promoted copy. */
    private fun discardCompletionDraft(draft: CompletionPhotoDraft?) {
        if (draft == null) return
        completionPhotoStore.deleteDraft(draft)
        completionPhotoStore.deletePhoto(draft.id)
    }

    // ── Category actions ──────────────────────────────────────────────────────

    /**
     * Creates a new user category from the FlowScreen "+ New category" tab.
     * Delegates to [CategoryRepository.createCategory], which is the single
     * source of truth for the "max position + 1" logic and the reserved-name
     * guard. The new category is NOT selected automatically.
     */
    fun createCategory(name: String) {
        viewModelScope.launch {
            categoryRepository.createCategory(name)
        }
    }

    fun selectCategory(id: Int?) {
        // null is treated as General — normalize so we never persist null.
        val effective = id ?: Category.GENERAL_ID
        _selectedCategoryId.value = effective
        viewModelScope.launch {
            userPreferencesRepository.saveLastCategoryId(effective)
        }
    }

    /**
     * Persists a new order for the user categories. Mirrors the logic in
     * settings:
     * Persists a new order for the categories.
     *
     * Delegates to [CategoryRepository.reorderCategories], which is the single
     * source of truth for the `mapIndexed` re-positioning logic.
     *
     * Called from the FlowScreen "Reorder categories" sheet.
     */
    fun reorderCategories(categories: List<Category>) {
        viewModelScope.launch {
            categoryRepository.reorderCategories(categories)
        }
    }

    // ── Deferred deletion API ─────────────────────────────────────────────────

    /**
     * Called when the user swipes a task to dismiss it.
     *
     * The task is **not** deleted from the DB immediately. Instead, it is added
     * to [_pendingUndoTasks] which causes the `combine` above to filter it out
     * of the UI state optimistically.  A per-task countdown of 3500ms
     * starts; if it expires the task is permanently deleted.  The user may cancel
     * the countdown by pressing the undo FAB ([undoPendingDeletions]).
     */
    fun onTaskSwiped(task: Task) {
        // Add to the pending map – the combine will filter it out of uiState
        _pendingUndoTasks.update { it + (task.id to task) }

        // Bump the delete version so the LazyColumn key changes.
        // This prevents rememberSaveable from restoring the old
        // SwipeToDismissBoxState (EndToStart) when the task is
        // un-filtered after an undo.
        _taskDeleteVersions.update { it + (task.id to (it[task.id] ?: 0) + 1) }

        // Cancel any previous countdown for this same task (defensive)
        deleteJobs[task.id]?.cancel()

        // Start the countdown to permanent deletion
        deleteJobs[task.id] = applicationScope.launch {
            delay(3500L)
            // Time's up – permanently delete from DB
            reminderScheduler.cancel(task.id)
            deleteTaskUseCase(task)
            // Clean up pending map; task is already gone from DB at this point
            _pendingUndoTasks.update { it - task.id }
            deleteJobs.remove(task.id)
        }
    }

    /**
     * Called when the user taps the undo FAB.
     *
     * Cancels **all** pending deletion countdowns and restores every task
     * that was waiting to be deleted.  The tasks are still in the DB, so
     * simply removing them from [_pendingUndoTasks] makes them reappear in
     * the UI state automatically.
     */
    fun undoPendingDeletions() {
        // Cancel every pending countdown
        deleteJobs.values.forEach { it.cancel() }
        deleteJobs.clear()
        // Clear the pending map – tasks reappear in uiState via the combine
        _pendingUndoTasks.update { emptyMap() }
    }

    // ── Lifecycle: called every time the app comes to the foreground ──────────

    override fun onResume(owner: LifecycleOwner) {
        val permissionNowGranted = reminderScheduler.canScheduleExactAlarms()

        if (wasPermissionMissing && permissionNowGranted) {
            // Permission was just granted — reschedule everything that was pending
            viewModelScope.launch {
                val items = buildAlarmItems(taskRepository)
                reminderScheduler.rescheduleAll(items)

            }
        }
        refreshBanner()
    }

    // ── Reorder ────────────────────────────────────────────────────────

    fun onTaskReorder(fromIndex: Int, toIndex: Int) {
        val current = (_uiState.value as? FlowUiState.Success)?.tasks?.toMutableList() ?: return

        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)

        val updated = current.mapIndexed { i, t -> t.copy(position = i) }

        _uiState.value = (_uiState.value as FlowUiState.Success).copy(tasks = updated)

        viewModelScope.launch {
            taskRepository.updateTasksOrder(updated)
        }
    }

    fun onIdeaReorder(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value as? FlowUiState.Success ?: return
        val list = currentState.ideas.toMutableList()

        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)

        val updated = list.mapIndexed { i, idea -> idea.copy(position = i) }

        _uiState.value = currentState.copy(ideas = updated)

        viewModelScope.launch {
            ideaRepository.updateIdeasOrder(updated)
        }
    }

    fun onCheckListReorder(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value as? FlowUiState.Success ?: return
        val list = currentState.checkLists.toMutableList()

        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)

        val updated = list.mapIndexed { i, cl -> cl.copy(position = i) }

        _uiState.value = currentState.copy(checkLists = updated)

        viewModelScope.launch {
            checkListRepository.updateCheckListsOrder(updated)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun refreshBanner() {
        val canSchedule = reminderScheduler.canScheduleExactAlarms()
        wasPermissionMissing = !canSchedule

        viewModelScope.launch {
            val tasks = (uiState.value as? FlowUiState.Success)?.tasks
                ?: return@launch  // still loading — init block will call refreshBanner again via first DB emit

            val now = System.currentTimeMillis()
            val hasAnyFutureReminder = tasks.any { task ->
                (task.reminderTime?.let { it > now } == true) ||
                        task.subTasks.any { sub -> sub.reminderTime?.let { it > now } == true }
            }
            _bannerVisible.value = hasAnyFutureReminder && !canSchedule
        }
    }
}

data class CoreData(
    val tasks: List<Task>,
    val ideas: List<Idea>,
    val lists: List<CheckList>,
    val categories: List<Category>
)

private const val COMPLETION_CELEBRATION_MILLIS = 1_400L
private const val COMPLETION_TASK_ID_KEY = "completion.taskId"
private const val COMPLETION_NOTE_KEY = "completion.note"
private const val COMPLETION_PHOTO_DRAFT_ID_KEY = "completion.photoDraftId"
private const val COMPLETION_PENDING_CAMERA_ID_KEY = "completion.pendingCameraDraftId"
