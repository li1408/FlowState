package com.markel.flowstate.feature.flow

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.designsystem.components.AnimatedUndoFab
import com.markel.flowstate.core.designsystem.feedback.CompletionCelebrationEvent
import com.markel.flowstate.core.designsystem.feedback.LocalCompletionCelebrationHostState
import com.markel.flowstate.core.designsystem.ui.LocalBottomNavigationInset
import com.markel.flowstate.core.designsystem.ui.rememberFabVisibilityState
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.flow.components.CategoryTabRow
import com.markel.flowstate.feature.flow.components.CreateCategoryDialog
import com.markel.flowstate.feature.flow.components.DynamicHeader
import com.markel.flowstate.feature.flow.components.ExpandableFabMenu
import com.markel.flowstate.feature.flow.components.ReorderCategoriesSheet
import com.markel.flowstate.feature.flow.tasks.TaskViewModel
import com.markel.flowstate.feature.flow.tasks.components.TaskCreationSheetContent
import com.markel.flowstate.feature.flow.tasks.util.HandleSystemBars
import com.markel.flowstate.feature.flow.components.SectionedFlowView
import com.markel.flowstate.feature.flow.completion.TaskCompletionDetailsSheet
import com.markel.flowstate.feature.flow.completion.TaskCompletionSheet
import com.markel.flowstate.feature.flow.completion.TaskCompletionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowScreen(
    flowViewModel: FlowViewModel,
    taskViewModel: TaskViewModel = hiltViewModel(),
    // Nvigation Callbacks to detail screens (edition)
    onNavigateToTaskEditor: (taskId: Int) -> Unit,
    onNavigateToIdeaEditor: (ideaId: Int) -> Unit,
    onNavigateToNewIdea: (categoryId: Int?) -> Unit,
    onNavigateToCheckListEditor: (checkListId: Int?, categoryId: Int?) -> Unit
) {
    val flowUiState by flowViewModel.uiState.collectAsStateWithLifecycle()
    val showPermissionBanner by flowViewModel.showReminderBanner.collectAsStateWithLifecycle()
    val showUndoButton by flowViewModel.showUndoButton.collectAsStateWithLifecycle()
    val completionUiState by flowViewModel.completionUiState.collectAsStateWithLifecycle()
    val celebrationHostState = LocalCompletionCelebrationHostState.current
    val bottomNavigationInset = LocalBottomNavigationInset.current
    val taskDeleteVersions by flowViewModel.taskDeleteVersions.collectAsStateWithLifecycle()
    var isFabExpanded by remember { mutableStateOf(false) }
    var showCreationSheet by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showReorderCategoriesSheet by remember { mutableStateOf(false) }
    var selectedCompletedTask by remember { mutableStateOf<Task?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) flowViewModel.importCompletionPhoto(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { succeeded ->
        flowViewModel.onCameraCaptureResult(succeeded)
    }

    val celebrating = completionUiState as? TaskCompletionUiState.Celebrating
    LaunchedEffect(celebrating?.eventId) {
        celebrating?.let { event ->
            celebrationHostState.show(
                CompletionCelebrationEvent(
                    eventId = event.eventId,
                    taskTitle = event.task.title,
                    completedCount = event.completedCount,
                    totalCount = event.totalCount,
                ),
            )
        }
    }

    // Only source of truth for the header
    var isHeaderMinimized by rememberSaveable { mutableStateOf(false) }

    val allEmpty = (flowUiState as? FlowUiState.Success)?.let {
        it.totalCount == 0 && it.checkLists.isEmpty() && it.ideas.isEmpty()
    } ?: true

    val draft by taskViewModel.draft.collectAsStateWithLifecycle()  // State with all the info for the new task

    // Extract category info from state
    val categoriesEnabled = (flowUiState as? FlowUiState.Success)?.categoriesEnabled == true
    val categories = (flowUiState as? FlowUiState.Success)?.categories ?: emptyList()
    val selectedCategoryId = (flowUiState as? FlowUiState.Success)?.selectedCategoryId
    val generalCategoryName by flowViewModel.generalCategoryName.collectAsStateWithLifecycle()
    val pendingTaskCounts = (flowUiState as? FlowUiState.Success)?.pendingTaskCounts ?: emptyMap()

    // ── Tab state ──────────────────────────────────────────────────
    // Saveable state independent for each tabId.
    // Everything inside SaveableStateProvider(currentTabId) — including the
    // list state is preserved per tab and restored when the user comes back
    val saveableStateHolder = rememberSaveableStateHolder()
    val currentTabId = selectedCategoryId ?: Category.GENERAL_ID

    // Registry of each tab's LazyListState so the FAB can read the active list state from a single variable
    val tabListStates = remember { mutableStateMapOf<Int, LazyListState>() }

    // ── FAB ──────────
    val activeListState = tabListStates[currentTabId] ?: rememberLazyListState()
    val fabVisible by rememberFabVisibilityState(
        lazyListState = activeListState,
        forceVisible = allEmpty
    )

    LaunchedEffect(fabVisible) {
        if (!fabVisible && isFabExpanded) isFabExpanded = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),  // To avoid big gaps of surface at the top & bottom
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                DynamicHeader(isMinimized = isHeaderMinimized)

                // ── Category tabs (only when enabled) ───────────────
                if (categoriesEnabled && categories.isNotEmpty()) {
                    CategoryTabRow(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { id -> flowViewModel.selectCategory(id) },
                        onAddCategoryClick = { showCreateCategoryDialog = true },
                        onCategoryLongPress = { showReorderCategoriesSheet = true },
                        pendingTaskCounts = pendingTaskCounts,
                        generalTabName = generalCategoryName
                    )
                }

                saveableStateHolder.SaveableStateProvider(currentTabId) {
                    val tabListState = rememberLazyListState()

                    // Publish this tab's LazyListState to the shared registry
                    // so the FAB (outside SaveableStateProvider) can read it.
                    SideEffect { tabListStates[currentTabId] = tabListState }

                    SectionedFlowView(
                        uiState = flowUiState,
                        onScrolled = { isHeaderMinimized = true },
                        onTaskClick = { onNavigateToTaskEditor(it.id) },
                        onTaskDelete = { task -> flowViewModel.onTaskSwiped(task) },
                        onTaskToggle = flowViewModel::requestCompletion,
                        onTaskReopen = flowViewModel::reopenTask,
                        onCompletedTaskClick = { selectedCompletedTask = it },
                        onTaskReorder = { from, to -> flowViewModel.onTaskReorder(from, to) },
                        onIdeaClick = { onNavigateToIdeaEditor(it.id) },
                        onIdeaReorder = { from, to -> flowViewModel.onIdeaReorder(from, to) },
                        onCheckListClick = { onNavigateToCheckListEditor(it.id, it.categoryId) },
                        onCheckListReorder = { from, to -> flowViewModel.onCheckListReorder(from, to) },
                        showPermissionBanner = showPermissionBanner,
                        taskDeleteVersions = taskDeleteVersions,
                        categoriesEnabled = categoriesEnabled,
                        outerListState = tabListState,
                    )
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────
        ExpandableFabMenu(
            expanded = isFabExpanded,
            onToggle = { isFabExpanded = !isFabExpanded },
            onTaskClick = { isFabExpanded = false; showCreationSheet = true },
            onIdeaClick = { isFabExpanded = false; onNavigateToNewIdea(selectedCategoryId ?: Category.GENERAL_ID) },
            onCheckListClick = { isFabExpanded = false; onNavigateToCheckListEditor(null, selectedCategoryId ?: Category.GENERAL_ID) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomNavigationInset)
                .zIndex(1f),
            visible = fabVisible,
        )
        if (showCreationSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCreationSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                HandleSystemBars(isLandscape)

                TaskCreationSheetContent(
                    title = draft.title,
                    onTitleChange = { taskViewModel.updateDraftTitle(it) },
                    description = draft.description,
                    onDescriptionChange = { taskViewModel.updateDraftDescription(it) },
                    priority = draft.priority,
                    onPriorityChange = { taskViewModel.updateDraftPriority(it) },
                    dueDate = draft.dueDate,
                    onDueDateChange = { taskViewModel.updateDraftDueDate(it) },
                    reminderTime = draft.reminderTime,
                    onReminderTimeChange = { taskViewModel.updateDraftReminderTime(it) },
                    onSave = { _, _, _, _, _ ->
                        taskViewModel.submitDraft(categoryId = if (categoriesEnabled) (selectedCategoryId ?: Category.GENERAL_ID) else Category.GENERAL_ID)
                        showCreationSheet = false
                    }
                )
            }
        }

        (completionUiState as? TaskCompletionUiState.Draft)?.let { completionDraft ->
            TaskCompletionSheet(
                state = completionDraft,
                onNoteChange = flowViewModel::updateCompletionNote,
                onTakePhoto = {
                    flowViewModel.prepareCameraCapture()?.let { uri ->
                        runCatching { cameraLauncher.launch(uri) }
                            .onFailure { flowViewModel.onCameraCaptureResult(false) }
                    }
                },
                onChoosePhoto = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemovePhoto = flowViewModel::removeCompletionPhoto,
                onSubmit = { flowViewModel.submitCompletion(includeRecord = true) },
                onSkip = { flowViewModel.submitCompletion(includeRecord = false) },
                onDismiss = flowViewModel::dismissCompletion,
            )
        }

        selectedCompletedTask?.let { completedTask ->
            TaskCompletionDetailsSheet(
                task = completedTask,
                photoFile = flowViewModel.resolveCompletionPhoto(completedTask.completion?.photoId),
                onReopen = {
                    flowViewModel.reopenTask(completedTask)
                    selectedCompletedTask = null
                },
                onDismiss = { selectedCompletedTask = null },
            )
        }
        AnimatedUndoFab(
            visible = showUndoButton,
            onUndoClick = { flowViewModel.undoPendingDeletions() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomNavigationInset)
        )

        // ── Create category dialog (opened from the trailing "+ New category" tab) ──
        if (showCreateCategoryDialog) {
            CreateCategoryDialog(
                onDismiss = { showCreateCategoryDialog = false },
                onConfirm = { name ->
                    flowViewModel.createCategory(name)
                    showCreateCategoryDialog = false
                }
            )
        }

        // ── Reorder / switch category sheet (opened by long-pressing a tab) ──
        if (showReorderCategoriesSheet) {
            ReorderCategoriesSheet(
                categories = categories,
                onReorder = { flowViewModel.reorderCategories(it) },
                onCategorySelected = { id ->
                    flowViewModel.selectCategory(id)
                    showReorderCategoriesSheet = false
                },
                onDismiss = { showReorderCategoriesSheet = false },
                generalTabName = generalCategoryName
            )
        }
    }
}
