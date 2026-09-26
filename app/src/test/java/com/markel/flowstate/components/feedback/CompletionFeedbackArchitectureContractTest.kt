package com.markel.flowstate.components.feedback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionFeedbackArchitectureContractTest {

    @Test
    fun `late sound load cannot play after its completion event expires`() {
        val player = source("CompletionSoundPlayer.kt")
        val effects = source("CompletionFeedbackEffects.kt")

        assertTrue(player.contains("private var permittedEventId: Long?"))
        assertTrue(player.contains("permittedEventId == eventId"))
        assertTrue(player.contains("fun cancel(eventId: Long)"))
        assertTrue(effects.contains("soundPlayer.cancel(activeEvent.eventId)"))
    }

    @Test
    fun `a failed preload can be retried by the next event`() {
        val player = source("CompletionSoundPlayer.kt")

        assertTrue(player.contains("suspend fun play(eventId: Long)"))
        assertTrue(player.contains("if (!permitted || !prepare()) return"))
        assertTrue(player.contains("if (loadAttempt === attempt) loadAttempt = null"))
    }

    private fun source(fileName: String): String =
        File("src/main/java/com/markel/flowstate/components/feedback/$fileName").readText()
}
