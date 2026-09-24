package com.markel.flowstate.core.domain

import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>
    suspend fun getCategoryById(id: Int): Category?
    suspend fun upsertCategory(category: Category): Long
    suspend fun deleteCategory(id: Int)
    suspend fun updateCategoriesOrder(categories: List<Category>)
    suspend fun moveItemsToGeneral(categoryId: Int)
    suspend fun deleteItemsInCategory(categoryId: Int)

    /**
     * Creates a new user category appended after the existing ones
     * (position = max + 1). Rejects blank names and all shipped display names
     * of the built-in General category (case-insensitive). No-op otherwise.
     * This is the single source of truth for category creation
     * */
    suspend fun createCategory(name: String): Long

    /**
     * Persists a new order for the categories. The list is taken as the
     * source of truth and each item is re-positioned by its index.
     *
     * Single source of truth for reordering
     */
    suspend fun reorderCategories(categories: List<Category>)
}
