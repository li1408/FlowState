package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.CategoryDao
import com.markel.flowstate.core.data.local.CategoryEntity
import com.markel.flowstate.core.data.CategoryRepositoryImpl
import com.markel.flowstate.core.domain.Category
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for the createCategory / reorderCategories methods that were
 * moved from the ViewModels into [CategoryRepositoryImpl] to eliminate
 * the duplication between FlowViewModel and CategoriesViewModel.
 */
class CategoryRepositoryImplTest {

    private val categoryDao: CategoryDao = mockk(relaxed = true)
    private val repository = CategoryRepositoryImpl(categoryDao)

    @Test
    fun createCategory_withValidName_appendsAtEndWithMaxPositionPlusOne() = runTest {
        // GIVEN — two existing categories at positions 0 and 1
        coEvery { categoryDao.getCategories() } returns flowOf(
            listOf(
                CategoryEntity(id = 1, name = "Work", position = 0),
                CategoryEntity(id = 2, name = "Personal", position = 1)
            )
        )
        coEvery { categoryDao.upsertCategory(any()) } returns 3L

        // WHEN
        repository.createCategory("New list")

        // THEN — upserted with position = max(existing) + 1 = 2
        coVerify {
            categoryDao.upsertCategory(match { cat ->
                cat.name == "New list" && cat.position == 2
            })
        }
    }

    @Test
    fun createCategory_withNoExistingCategories_startsAtPositionZero() = runTest {
        coEvery { categoryDao.getCategories() } returns flowOf(emptyList())
        coEvery { categoryDao.upsertCategory(any()) } returns 1L

        repository.createCategory("First")

        coVerify {
            categoryDao.upsertCategory(match { cat ->
                cat.name == "First" && cat.position == 0
            })
        }
    }

    @Test
    fun createCategory_withBlankName_doesNotCallDao() = runTest {
        repository.createCategory("   ")

        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
    }

    @Test
    fun createCategory_withReservedNameGeneral_doesNotCallDao() = runTest {
        repository.createCategory("General")
        repository.createCategory("GENERAL")
        repository.createCategory("general")

        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
    }

    @Test
    fun createCategory_withSimplifiedChineseGeneralName_doesNotCallDao() = runTest {
        repository.createCategory("常规")

        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
    }

    @Test
    fun upsertCategory_withSimplifiedChineseGeneralName_doesNotCallDao() = runTest {
        repository.upsertCategory(Category(id = 2, name = "常规", position = 1))

        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
    }

    @Test
    fun reorderCategories_persistsNewPositionsIndexedFromZero() = runTest {
        val stored = listOf(
            Category(id = 1, name = "A", position = 5),
            Category(id = 2, name = "B", position = 7),
            Category(id = 3, name = "C", position = 9)
        )

        // The user reorders to [B, C, A]
        val reordered = listOf(stored[1], stored[2], stored[0])
        repository.reorderCategories(reordered)

        // THEN — each category is re-positioned by its index in the new list
        coVerify {
            categoryDao.updateCategories(match { list ->
                list.size == 3 &&
                        list[0].id == 2 && list[0].position == 0 &&
                        list[1].id == 3 && list[1].position == 1 &&
                        list[2].id == 1 && list[2].position == 2
            })
        }
    }
}
