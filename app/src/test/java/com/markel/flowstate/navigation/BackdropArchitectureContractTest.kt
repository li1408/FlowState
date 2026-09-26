package com.markel.flowstate.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prevents a captured render subtree from containing a glass node that samples
 * the very same [GraphicsLayer]. Android's RenderThread treats that as a
 * RenderNode cycle and recurses until the native process crashes.
 */
class BackdropArchitectureContractTest {

    @Test
    fun `screen recorder wraps scene content only and never its sampling bottom bar`() {
        val navDisplay = source("navigation/FlowStateNavDisplay.kt")
        val sceneDecorator = source("navigation/FlowStateSceneDecoratorStrategy.kt")

        assertFalse(
            "NavDisplay contains the bottom bar, so recording NavDisplay would create a RenderNode cycle",
            navDisplay.contains(".screenBackdrop(backdrop)"),
        )
        assertTrue(
            "The active scene content must remain the backdrop source",
            sceneDecorator.contains(".screenBackdrop(backdrop)"),
        )
        assertFalse(
            "A runtime draw guard cannot remove an already-recorded RenderNode reference",
            sceneDecorator.contains("excludeFromRecording"),
        )

        val recorder = sceneDecorator.indexOf(".screenBackdrop(backdrop)")
        val sceneContent = sceneDecorator.indexOf("scene.content()")
        val sampler = sceneDecorator.indexOf("bottomBarContent(backdrop)")
        val celebrationHost = sceneDecorator.indexOf("celebrationContent(backdrop)")
        assertTrue("Recorder must be attached to the scene-content branch", recorder in 0..<sceneContent)
        assertTrue("Bottom bar must stay a later sibling outside the recorded branch", sampler > sceneContent)
        assertTrue(
            "Completion glass must stay a later sibling outside the recorded branch",
            celebrationHost > sceneContent,
        )
        assertTrue(
            "Completion overlay must retain animation state while moving between tab scenes",
            sceneDecorator.contains("val movableCelebration = remember"),
        )
    }

    @Test
    fun `celebration owner survives configuration and tab scene changes`() {
        val activity = source("MainActivity.kt")
        val mainViewModel = source("MainViewModel.kt")
        val sceneDecorator = source("navigation/FlowStateSceneDecoratorStrategy.kt")

        assertTrue(
            "The Activity ViewModel must retain one host across configuration changes",
            mainViewModel.contains(
                "val completionCelebrationHostState = CompletionCelebrationHostState()",
            ),
        )
        assertTrue(
            "MainActivity must provide the ViewModel-owned host",
            activity.contains("mainViewModel.completionCelebrationHostState"),
        )
        assertFalse(
            "A remember-only host would replay feedback after rotation",
            activity.contains("rememberCompletionCelebrationHostState"),
        )
        assertTrue(
            "Tab ownership changes must move, rather than recreate, the overlay",
            sceneDecorator.contains("movableContentOf<Backdrop> { source ->") &&
                sceneDecorator.contains("celebrationContent(backdrop)"),
        )
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/markel/flowstate/$relativePath").readText()
}
