package com.markel.flowstate.core.data.backup

import com.markel.flowstate.core.data.local.CategoryDao
import com.markel.flowstate.core.data.local.CategoryEntity
import com.markel.flowstate.core.data.local.CheckListDao
import com.markel.flowstate.core.data.local.CheckListEntity
import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.IdeaDao
import com.markel.flowstate.core.data.local.IdeaEntity
import com.markel.flowstate.core.data.local.TaskCompletionRecordEntity
import com.markel.flowstate.core.data.local.TaskDao
import com.markel.flowstate.core.data.local.TaskEntity
import com.markel.flowstate.core.domain.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val ideaDao: IdeaDao,
    private val checkListDao: CheckListDao,
    private val habitDao: HabitDao,
    private val categoryDao: CategoryDao,
    private val transactionRunner: BackupTransactionRunner,
) : BackupRepository {

    /** Keeps the existing DAO-only unit tests lightweight. */
    internal constructor(
        taskDao: TaskDao,
        ideaDao: IdeaDao,
        checkListDao: CheckListDao,
        habitDao: HabitDao,
        categoryDao: CategoryDao,
    ) : this(
        taskDao,
        ideaDao,
        checkListDao,
        habitDao,
        categoryDao,
        DirectBackupTransactionRunner,
    )

    private val lenientJson = Json { ignoreUnknownKeys = true }
    private val exportJson = Json { prettyPrint = true }

    override suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val export = transactionRunner.run {
            val tasksWithSubTasks = taskDao.getAllTasksOnce()
            val allListsWithItems = checkListDao.getAllListsOnce()

            FlowStateExport(
                tasks = tasksWithSubTasks.map { it.task.toSchema() },
                subTasks = tasksWithSubTasks.flatMap { task ->
                    task.subTasks.map { it.toSchema() }
                },
                ideas = ideaDao.getAllIdeasOnce().map { it.toSchema() },
                checkLists = allListsWithItems.map { it.list.toSchema() },
                checkListItems = allListsWithItems.flatMap { list ->
                    list.items.map { it.toSchema() }
                },
                habits = habitDao.getAllHabitsOnce().map { it.habit.toSchema() },
                habitEntries = habitDao.getAllEntriesOnce().map { it.toSchema() },
                habitNumericEntries = habitDao.getAllNumericEntriesOnce().map { it.toSchema() },
                categories = categoryDao.getAllCategoriesOnce().map { it.toSchema() },
                completionRecords = taskDao.getAllCompletionRecordsOnce().map { record ->
                    TaskCompletionRecordSchema(
                        taskId = record.taskId,
                        note = record.note,
                        // JSON does not contain the private image bytes.
                        photoId = null,
                        completedAt = record.completedAt,
                    )
                },
            )
        }

        exportJson.encodeToString(FlowStateExport.serializer(), export)
    }

    override suspend fun restoreFromJson(json: String): RestoreResult =
        withContext(Dispatchers.IO) {
            try {
                val data = lenientJson.decodeFromString<FlowStateExport>(json)
                if (data.schemaVersion > FlowStateExport.CURRENT_SCHEMA_VERSION) {
                    return@withContext RestoreResult.Error(RestoreErrorType.SCHEMA_MISMATCH)
                }

                data.validateForRestore()
                transactionRunner.run { restoreInTransaction(data) }
                RestoreResult.Success
            } catch (_: IllegalArgumentException) {
                RestoreResult.Error(RestoreErrorType.INVALID_FILE)
            } catch (_: Exception) {
                RestoreResult.Error(RestoreErrorType.UNKNOWN)
            }
        }

    private suspend fun restoreInTransaction(data: FlowStateExport) {
        val existingCategories = categoryDao.getAllCategoriesOnce()
        val existingByName = existingCategories
            .associateBy { it.name.lowercase() }
            .toMutableMap()

        val categoryIdRemap = mutableMapOf<Int?, Int?>(null to Category.GENERAL_ID)
        data.categories.forEach { schema ->
            val normalizedName = schema.name.lowercase()
            val targetId = existingByName[normalizedName]?.id
                ?: categoryDao.upsertCategory(
                    CategoryEntity(id = 0, name = schema.name, position = schema.position),
                ).toInt().also { newId ->
                    existingByName[normalizedName] = CategoryEntity(
                        id = newId,
                        name = schema.name,
                        position = schema.position,
                    )
                }
            categoryIdRemap[schema.id] = targetId
        }

        // Restore is additive: only rows represented by the backup are upserted.
        data.tasks.map { it.toEntity().withRemappedCategory(categoryIdRemap) }
            .forEach { taskDao.upsertTaskEntity(it) }

        val importedTasks = data.tasks.associateBy { it.id }
        data.completionRecords.forEach { record ->
            val parent = importedTasks[record.taskId]
                ?: taskDao.getTaskEntity(record.taskId)?.toSchema()
                ?: throw IllegalArgumentException("Completion record has no parent task")
            record.validateAgainst(parent)
            upsertCompletionRecordPreservingLocalPhoto(record)
        }

        val explicitCompletionTaskIds = data.completionRecords
            .mapTo(mutableSetOf()) { it.taskId }
        // Backups from before v21 need a one-to-one completion row synthesized.
        // Very old completed tasks can lack a timestamp; migration 20 -> 21
        // represents that unknown instant with LEGACY_COMPLETION_TIME_UNKNOWN.
        data.tasks.asSequence()
            .filter { it.id !in explicitCompletionTaskIds && it.isDone }
            .forEach { task ->
                upsertCompletionRecordPreservingLocalPhoto(
                    TaskCompletionRecordSchema(
                        taskId = task.id,
                        note = null,
                        photoId = null,
                        completedAt = task.completedAt ?: LEGACY_COMPLETION_TIME_UNKNOWN,
                    ),
                )
            }

        if (data.subTasks.isNotEmpty()) {
            taskDao.insertSubTasks(data.subTasks.map { it.toEntity() })
        }
        data.ideas.map { it.toEntity().withRemappedCategory(categoryIdRemap) }
            .forEach { ideaDao.upsertIdea(it) }
        data.checkLists.map { it.toEntity().withRemappedCategory(categoryIdRemap) }
            .forEach { checkListDao.upsertListEntity(it) }
        if (data.checkListItems.isNotEmpty()) {
            checkListDao.insertListItems(data.checkListItems.map { it.toEntity() })
        }
        data.habits.map { it.toEntity() }.forEach { habitDao.insertHabit(it) }
        data.habitEntries.map { it.toEntity() }.forEach { habitDao.insertEntry(it) }
        data.habitNumericEntries.map { it.toEntity() }
            .forEach { habitDao.upsertNumericEntry(it) }
    }

    private suspend fun upsertCompletionRecordPreservingLocalPhoto(
        record: TaskCompletionRecordSchema,
    ) {
        val existing = taskDao.getCompletionRecord(record.taskId)
        val localPhotoId = existing?.photoId?.takeIf {
            record.photoId == null && existing.completedAt == record.completedAt
        }
        taskDao.upsertCompletionRecord(
            TaskCompletionRecordEntity(
                taskId = record.taskId,
                note = record.note,
                // Never trust a file id from portable JSON. Retain only an
                // already-known local file for this exact completion event.
                photoId = localPhotoId,
                completedAt = record.completedAt,
            ),
        )
    }
}

private fun FlowStateExport.validateForRestore() {
    require(tasks.map { it.id }.toSet().size == tasks.size) { "Duplicate task id" }
    require(completionRecords.map { it.taskId }.toSet().size == completionRecords.size) {
        "Duplicate completion task id"
    }

    tasks.forEach { task ->
        require(task.id > 0) { "Invalid task id" }
        // A pending task must never advertise a completion instant. Completed
        // tasks normally have a non-negative one, but legacy data may have
        // isDone=true/null and is represented by the sentinel record later.
        val hasValidCompletionTime = if (task.isDone) {
            task.completedAt == null || task.completedAt >= 0L
        } else {
            task.completedAt == null
        }
        require(hasValidCompletionTime) {
            "Incoherent task completion time"
        }
    }

    val importedTasks = tasks.associateBy { it.id }
    completionRecords.forEach { record ->
        require(record.taskId > 0) { "Invalid completion task id" }
        require(record.completedAt >= 0L) { "Invalid completion timestamp" }
        importedTasks[record.taskId]?.let(record::validateAgainst)
    }
}

private fun TaskCompletionRecordSchema.validateAgainst(task: TaskSchema) {
    // Pending tasks may retain a dormant record after a soft reopen. Completed
    // tasks must reference exactly the same completion event as their parent.
    if (task.isDone) {
        val expectedCompletedAt = task.completedAt ?: LEGACY_COMPLETION_TIME_UNKNOWN
        require(expectedCompletedAt == completedAt) {
            "Completion timestamp does not match task"
        }
    }
}

private const val LEGACY_COMPLETION_TIME_UNKNOWN = 0L

private fun TaskEntity.withRemappedCategory(remap: Map<Int?, Int?>): TaskEntity =
    copy(categoryId = remap[categoryId] ?: Category.GENERAL_ID)

private fun IdeaEntity.withRemappedCategory(remap: Map<Int?, Int?>): IdeaEntity =
    copy(categoryId = remap[categoryId] ?: Category.GENERAL_ID)

private fun CheckListEntity.withRemappedCategory(remap: Map<Int?, Int?>): CheckListEntity =
    copy(categoryId = remap[categoryId] ?: Category.GENERAL_ID)
