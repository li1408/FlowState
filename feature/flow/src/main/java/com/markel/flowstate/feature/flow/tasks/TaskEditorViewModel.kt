package com.markel.flowstate.feature.flow.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.DeleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.ToggleTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel exclusive for TaskEditorScreen.
 *
 * Separating it from TaskViewModel (which lives in FlowScreen/Tasks) prevents crashes:
 *
 * Possible crash because TaskViewModel was obtained with hiltViewModel() in both FlowScreen
 * and TaskEditorScreen. By navigating quickly, the editor's backstack entry was destroyed
 * while FlowScreen was still trying to access the same ViewModel, causing conflicts.
 *
 * With a dedicated ViewModel per screen, each lives and dies with its NavBackStackEntry and solves that problem.
 */
data class TaskEditorState(
    val task: Task? = null,
    val priority: Priority = Priority.NOTHING,
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val isDone: Boolean = false,
    val categoryId: Int? = Category.GENERAL_ID
)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val toggleTaskUseCase: ToggleTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val reminderScheduler: ReminderScheduler,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _editor = MutableStateFlow(TaskEditorState())
    val editor: StateFlow<TaskEditorState> = _editor.asStateFlow()
    private var completionMutationJob: Job? = null

    /** User categories, exposed so the editor can populate the category selector. */
    val categories: StateFlow<List<Category>> = categoryRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether category tabs are enabled — the selector is only shown when true. */
    val categoriesEnabled: StateFlow<Boolean> = userPreferencesRepository.categoriesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val generalCategoryName: StateFlow<String?> = userPreferencesRepository.generalCategoryName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Loads the task from the repository by ID.
     * Uses .first() to get the current Flow value without staying subscribed.
     */
    fun loadTask(taskId: Int) {
        viewModelScope.launch {
            val task = repository.getTasks()
                .first()
                .firstOrNull { it.id == taskId }

            if (task != null) {
                _editor.value = TaskEditorState(
                    task = task,
                    priority = task.priority,
                    dueDate = task.dueDate,
                    reminderTime = task.reminderTime,
                    isDone = task.isDone,
                    categoryId = task.categoryId
                )
            }
        }
    }

    fun updateTask(
        originalTask: Task,
        newTitle: String,
        newDescription: String,
        newPriority: Priority,
        newDueDate: Long?,
        newReminderTime: Long?,
        newSubTasks: List<SubTask>
    ) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            completionMutationJob?.join()
            val authoritativeTask = repository.getTaskById(originalTask.id)
                ?: originalTask.takeIf { completionMutationJob == null }
                ?: return@launch
            val updatedTask = authoritativeTask.copy(
                title = newTitle,
                description = newDescription,
                priority = newPriority,
                dueDate = newDueDate,
                reminderTime = newReminderTime,
                subTasks = newSubTasks
            )
            repository.upsertTask(updatedTask)
            _editor.update { state ->
                if (state.task?.id == updatedTask.id) state.copy(task = updatedTask) else state
            }
            reconcileSubTaskAlarms(original = authoritativeTask, updated = updatedTask)
        }
    }

    fun updatePriority(value: Priority) = _editor.update { it.copy(priority = value) }
    fun updateDueDate(value: Long?) = _editor.update { it.copy(dueDate = value) }

    /**
     * Moves the task being edited to a different category.
     *
     * Pass [Category.GENERAL_ID] to move the task to the default (General)
     * category. The change is persisted immediately and the editor state is
     * updated so the selector reflects the new value.
     */
    fun updateCategory(categoryId: Int?) {
        val task = _editor.value.task ?: return
        _editor.update { it.copy(categoryId = categoryId) }
        viewModelScope.launch {
            completionMutationJob?.join()
            val authoritativeTask = repository.getTaskById(task.id)
                ?: task.takeIf { completionMutationJob == null }
                ?: return@launch
            val updated = authoritativeTask.copy(categoryId = categoryId ?: Category.GENERAL_ID)
            repository.upsertTask(updated)
            _editor.update { state ->
                if (state.task?.id == updated.id) state.copy(task = updated) else state
            }
        }
    }

    fun updateReminderTime(value: Long?) {
        val task = _editor.value.task ?: return
        val effectiveValue = if (value != null && value > System.currentTimeMillis()) value else null

        _editor.update { it.copy(reminderTime = effectiveValue) }

        viewModelScope.launch {
            completionMutationJob?.join()
            val authoritativeTask = repository.getTaskById(task.id)
                ?: task.takeIf { completionMutationJob == null }
                ?: return@launch
            // Cancel the old alarm regardless of whether we're setting a new one.
            reminderScheduler.cancel(task.id)

            val updated = authoritativeTask.copy(reminderTime = effectiveValue)
            repository.upsertTask(updated)
            _editor.update { state ->
                if (state.task?.id == updated.id) state.copy(task = updated) else state
            }

            if (effectiveValue != null) {
                reminderScheduler.schedule(task.id, task.title, task.description, effectiveValue)
            }
        }
    }

    fun toggleDone() {
        if (completionMutationJob?.isActive == true) return
        val current = _editor.value.task ?: return
        val newIsDone = !_editor.value.isDone
        // ToggleTaskUseCase decides between complete/reopen from Task.isDone.
        // The persisted Task snapshot can be stale if the post-commit read failed,
        // so derive that input from the editor's explicit target state instead.
        val toggleInput = current.copy(isDone = !newIsDone)
        _editor.update { it.copy(isDone = newIsDone) }
        completionMutationJob = viewModelScope.launch {
            try {
                toggleTaskUseCase(toggleInput)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _editor.update { state ->
                    if (state.task?.id == current.id) state.copy(isDone = !newIsDone) else state
                }
                return@launch
            }

            if (newIsDone) {
                try {
                    reminderScheduler.cancel(current.id)
                    current.subTasks.filter { it.reminderTime != null }.forEach { subTask ->
                        reminderScheduler.cancelSubTask(subTask.id)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Completion is already committed; alarm cleanup is best-effort.
                }
            }
            try {
                repository.getTaskById(current.id)?.let { persisted ->
                    _editor.update { state ->
                        if (state.task?.id == persisted.id) {
                            state.copy(
                                task = persisted,
                                priority = persisted.priority,
                                dueDate = persisted.dueDate,
                                reminderTime = persisted.reminderTime,
                                isDone = persisted.isDone,
                                categoryId = persisted.categoryId,
                            )
                        } else state
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the committed optimistic state. Future edits will require
                // an authoritative read before writing any full task entity.
            }
        }
    }
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            reminderScheduler.cancel(task.id)
            task.subTasks.forEach { reminderScheduler.cancelSubTask(it.id) }  // Cancel all subtask alarms
            deleteTaskUseCase(task)
        }
    }


    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Diffs old vs new subtask list to cancel removed alarms and schedule new ones.
     * Only touches alarms for subtasks whose reminderTime actually changed.
     */
    private fun reconcileSubTaskAlarms(original: Task, updated: Task) {
        val now = System.currentTimeMillis()
        val oldMap = original.subTasks.associateBy { it.id }
        val newMap = updated.subTasks.associateBy { it.id }

        // Canceled or removed subtasks
        oldMap.forEach { (id, old) ->
            if (old.reminderTime != null && newMap[id]?.reminderTime != old.reminderTime) {
                reminderScheduler.cancelSubTask(id)
            }
        }

        // New or updated reminders
        newMap.forEach { (id, new) ->
            val oldReminder = oldMap[id]?.reminderTime
            val newReminder = new.reminderTime
            if (newReminder != null &&
                newReminder != oldReminder &&
                newReminder > now
            ) {
                reminderScheduler.scheduleSubTask(id, new.title, newReminder)
            }
        }
    }

}
