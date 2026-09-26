package com.markel.flowstate.navigation

import androidx.navigation3.runtime.get
import androidx.navigation3.ui.NavDisplay
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransitionMetadataTest {

    @Test
    fun `fullscreen transitions keep predictive back metadata`() {
        val vertical = fullScreenVerticalSlide()
        val sharedBounds = fullScreenSharedBounds()

        assertEquals(true, vertical[FullScreenMeta])
        assertEquals(true, sharedBounds[FullScreenMeta])
        assertNotNull(vertical[NavDisplay.PredictivePopTransitionKey])
        assertNotNull(sharedBounds[NavDisplay.PredictivePopTransitionKey])
    }

    @Test
    fun `top level transition has forward pop and predictive back paths`() {
        val metadata = topLevelTabTransition(
            visualDirection = { 1 },
            distancePx = 10,
        )

        assertNotNull(metadata[NavDisplay.TransitionKey])
        assertNotNull(metadata[NavDisplay.PopTransitionKey])
        assertNotNull(metadata[NavDisplay.PredictivePopTransitionKey])
    }

    @Test
    fun `top level transition reads direction lazily for every transition`() {
        var direction = 1
        var reads = 0
        val metadata = topLevelTabTransition(
            visualDirection = {
                reads++
                direction
            },
            distancePx = 10,
        )
        val transition = requireNotNull(metadata[NavDisplay.TransitionKey])

        assertEquals(0, reads)
        transition.invoke(mockk(relaxed = true))
        direction = -1
        transition.invoke(mockk(relaxed = true))

        assertEquals(2, reads)
    }
}
