package com.markel.flowstate.completion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markel.flowstate.core.data.TaskCompletionPhotoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletionFileProviderTest {

    @Test
    fun cameraDraft_isExposedOnlyThroughTheApplicationFileProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = TaskCompletionPhotoStore(context)
        val draft = store.createCameraDraft()

        try {
            assertEquals("${context.packageName}.completion-files", draft.captureUri.authority)
            context.contentResolver.openFileDescriptor(draft.captureUri, "rw").use { descriptor ->
                assertNotNull(descriptor)
            }
        } finally {
            store.deleteDraft(draft)
        }

        assertFalse(draft.file.exists())
    }
}
