package com.markel.flowstate.components.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidBottomBarMotionTest {

    @Test
    fun `uses the official liquid bottom tabs geometry`() {
        assertEquals(64f, LiquidBottomBarMotion.BarHeightDp, 0f)
        assertEquals(56f, LiquidBottomBarMotion.DropletHeightDp, 0f)
        assertEquals(78f / 56f, LiquidBottomBarMotion.PressedScale, 0f)
        assertEquals(72f, LiquidBottomBarMotion.ContentClearanceDp, 0f)
    }

    @Test
    fun `tab width excludes four dp padding on both sides`() {
        assertEquals(
            78.4f,
            LiquidBottomBarMotion.tabWidth(containerWidth = 400f, tabsCount = 5),
            0.0001f,
        )
        assertEquals(0f, LiquidBottomBarMotion.tabWidth(400f, 0), 0f)
        assertEquals(
            211.2f,
            LiquidBottomBarMotion.tabWidth(
                containerWidth = 1080f,
                tabsCount = 5,
                horizontalContentPadding = 12f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `drag accumulates from the animated target and clamps to the available tabs`() {
        assertEquals(
            2.5f,
            LiquidBottomBarMotion.dragTarget(
                currentTarget = 2f,
                dragDelta = 50f,
                tabWidth = 100f,
                lastIndex = 4,
                isLtr = true,
            ),
            0.0001f,
        )
        assertEquals(
            1.5f,
            LiquidBottomBarMotion.dragTarget(2f, 50f, 100f, 4, isLtr = false),
            0.0001f,
        )
        assertEquals(4f, LiquidBottomBarMotion.dragTarget(3.8f, 80f, 100f, 4, true), 0f)
        assertEquals(0f, LiquidBottomBarMotion.dragTarget(0.1f, -80f, 100f, 4, true), 0f)
    }

    @Test
    fun `release snaps half way to the next tab`() {
        assertEquals(2, LiquidBottomBarMotion.snapIndex(1.50f, lastIndex = 4))
        assertEquals(1, LiquidBottomBarMotion.snapIndex(1.49f, lastIndex = 4))
        assertEquals(0, LiquidBottomBarMotion.snapIndex(-3f, lastIndex = 4))
        assertEquals(4, LiquidBottomBarMotion.snapIndex(9f, lastIndex = 4))
    }

    @Test
    fun `droplet translation mirrors across the complete track in rtl`() {
        assertEquals(
            0f,
            LiquidBottomBarMotion.dropletTranslation(0f, 80f, 5, isLtr = true),
            0f,
        )
        assertEquals(
            320f,
            LiquidBottomBarMotion.dropletTranslation(0f, 80f, 5, isLtr = false),
            0f,
        )
        assertEquals(
            0f,
            LiquidBottomBarMotion.dropletTranslation(4f, 80f, 5, isLtr = false),
            0f,
        )
        assertEquals(
            200f,
            LiquidBottomBarMotion.dropletTranslation(1.5f, 80f, 5, isLtr = false),
            0.0001f,
        )
    }

    @Test
    fun `droplet collapses only after position is strictly inside official threshold`() {
        assertTrue(LiquidBottomBarMotion.canCollapse(value = 1.901f, target = 2f, tabsCount = 5))
        assertFalse(LiquidBottomBarMotion.canCollapse(value = 1.9f, target = 2f, tabsCount = 5))
        assertTrue(LiquidBottomBarMotion.canCollapse(value = 0.951f, target = 1f, tabsCount = 3))
        assertFalse(LiquidBottomBarMotion.canCollapse(value = 0.95f, target = 1f, tabsCount = 3))
        assertTrue(LiquidBottomBarMotion.canCollapse(value = 0f, target = 0f, tabsCount = 1))
    }

    @Test
    fun `velocity deformation follows official twenty percent clamp formula`() {
        val positive = LiquidBottomBarMotion.velocityScale(
            baseScaleX = 1f,
            baseScaleY = 1f,
            normalizedVelocity = 100f,
        )
        assertEquals(1.25f, positive.scaleX, 0.0001f)
        assertEquals(0.8f, positive.scaleY, 0.0001f)

        val negative = LiquidBottomBarMotion.velocityScale(
            baseScaleX = 1f,
            baseScaleY = 1f,
            normalizedVelocity = -100f,
        )
        assertEquals(1f / 1.2f, negative.scaleX, 0.0001f)
        assertEquals(1.2f, negative.scaleY, 0.0001f)
    }

    @Test
    fun `selection remains anchored by stable key after reorder or hiding`() {
        val reordered = listOf("settings", "tasks", "habits")

        assertEquals(1, LiquidBottomBarMotion.selectedIndex(reordered, "tasks"))
        assertNull(LiquidBottomBarMotion.selectedIndex(reordered, "calendar"))
        assertNull(LiquidBottomBarMotion.selectedIndex(emptyList<String>(), "tasks"))
    }

    @Test
    fun `pointer down on any item enters press without committing`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits", "focus", "settings"),
            committedKey = "tasks",
            isLtr = true,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.press(index = 3)

        assertTrue(gate.isPressed)
        assertEquals(3, gate.targetIndex)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun `release commits once only after spring travels forty percent`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits", "focus", "settings"),
            committedKey = "tasks",
            isLtr = true,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.press(index = 3)
        val generation = gate.release()

        assertFalse(gate.isPressed)
        assertTrue(commits.isEmpty())

        gate.updatePosition(1.19f)
        assertTrue(commits.isEmpty())

        gate.updatePosition(1.21f)
        gate.updatePosition(2.4f)
        gate.updatePosition(3f)
        assertTrue(commits.isEmpty())

        gate.allowCommit(generation)

        assertEquals(listOf("focus"), commits)
        assertEquals("focus", gate.committedKey)
    }

    @Test
    fun `rapid redirect commits only the latest released target`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits", "focus", "settings"),
            committedKey = "tasks",
            isLtr = true,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.press(index = 2)
        val staleGeneration = gate.release()
        gate.updatePosition(0.7f)

        gate.press(index = 4)
        val latestGeneration = gate.release()
        gate.updatePosition(2.01f)
        assertTrue(commits.isEmpty())

        gate.updatePosition(2.03f)
        gate.updatePosition(4f)
        gate.allowCommit(staleGeneration)
        assertTrue(commits.isEmpty())

        gate.allowCommit(latestGeneration)

        assertEquals(listOf("settings"), commits)
        assertEquals("settings", gate.committedKey)
    }

    @Test
    fun `cancel settles back to committed item and never commits pending target`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits", "focus", "settings"),
            committedKey = "calendar",
            isLtr = true,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.press(index = 3)
        val cancelledGeneration = gate.release()
        gate.updatePosition(1.5f)
        gate.cancel()
        gate.updatePosition(3f)
        gate.allowCommit(cancelledGeneration)

        assertFalse(gate.isPressed)
        assertEquals("calendar", gate.committedKey)
        assertEquals(1, gate.targetIndex)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun `reverse travel used by rtl reaches the same forty percent commit gate`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits", "focus", "settings"),
            committedKey = "settings",
            isLtr = false,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.press(index = 0)
        val generation = gate.release()
        gate.updatePosition(2.41f)
        assertTrue(commits.isEmpty())

        gate.updatePosition(2.39f)
        assertTrue(commits.isEmpty())
        gate.allowCommit(generation)

        assertEquals(listOf("tasks"), commits)
        assertEquals("tasks", gate.committedKey)
    }

    @Test
    fun `dynamic tabs retain committed target by stable key`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits"),
            committedKey = "tasks",
            isLtr = true,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.updateItems(listOf("habits", "tasks", "calendar"))

        assertEquals("tasks", gate.committedKey)
        assertEquals(1, gate.committedIndex)
        assertEquals(1, gate.targetIndex)
        assertFalse(gate.isPressed)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun `new physical press invalidates an older release that already crossed forty percent`() {
        val commits = mutableListOf<String>()
        val gate = LiquidBottomBarSelectionGate(
            items = listOf("tasks", "calendar", "habits"),
            committedKey = "tasks",
            isLtr = true,
            onCommit = { key: String -> commits.add(key) },
        )

        gate.press(index = 1)
        gate.updatePosition(0.8f)
        val staleGeneration = gate.release()

        gate.press(index = 2)
        gate.updatePosition(1.3f)
        val latestGeneration = gate.release()

        gate.allowCommit(staleGeneration)
        assertTrue(commits.isEmpty())

        gate.allowCommit(latestGeneration)
        assertEquals(listOf("habits"), commits)
    }

    @Test
    fun `page transition follows the visual travel direction in ltr and rtl`() {
        assertEquals(1, LiquidBottomBarMotion.transitionDirection(0, 3, isLtr = true))
        assertEquals(-1, LiquidBottomBarMotion.transitionDirection(3, 1, isLtr = true))
        assertEquals(-1, LiquidBottomBarMotion.transitionDirection(0, 3, isLtr = false))
        assertEquals(1, LiquidBottomBarMotion.transitionDirection(3, 1, isLtr = false))
        assertEquals(0, LiquidBottomBarMotion.transitionDirection(2, 2, isLtr = true))
    }
}
