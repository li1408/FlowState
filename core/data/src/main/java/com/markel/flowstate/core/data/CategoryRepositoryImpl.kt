package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.CategoryDao
import com.markel.flowstate.core.data.local.CategoryEntity
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getCategories(): Flow<List<Category>> =
        categoryDao.getCategories().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getCategoryById(id: Int): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    override suspend fun upsertCategory(category: Category): Long {
        val trimmedName = category.name.trim()
        if (trimmedName.isBlank() || Category.isReservedGeneralName(trimmedName)) return -1L
        return categoryDao.upsertCategory(category.copy(name = trimmedName).toEntity())
    }

    override suspend fun deleteCategory(id: Int) =
        categoryDao.deleteCategory(id)

    override suspend fun updateCategoriesOrder(categories: List<Category>) {
        categoryDao.updateCategories(categories.map { it.toEntity() })
    }

    override suspend fun moveItemsToGeneral(categoryId: Int) {
        categoryDao.moveTasksToGeneral(categoryId)
        categoryDao.moveIdeasToGeneral(categoryId)
        categoryDao.moveCheckListsToGeneral(categoryId)
    }

    override suspend fun deleteItemsInCategory(categoryId: Int) {
        categoryDao.deleteTasksInCategory(categoryId)
        categoryDao.deleteIdeasInCategory(categoryId)
        categoryDao.deleteCheckListsInCategory(categoryId)
    }

    override suspend fun createCategory(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return -1L
        if (Category.isReservedGeneralName(trimmed)) return -1L

        val currentList = getCategories().first()
        val maxPosition = currentList.maxOfOrNull { it.position } ?: -1
        return upsertCategory(Category(name = trimmed, position = maxPosition + 1))
    }

    override suspend fun reorderCategories(categories: List<Category>) {
        val reordered = categories.mapIndexed { index, category ->
            category.copy(position = index)
        }
        updateCategoriesOrder(reordered)
    }

    // Mappers
    private fun CategoryEntity.toDomain() = Category(id, name, position)
    private fun Category.toEntity() = CategoryEntity(id, name, position)
}
