package com.markel.flowstate.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Security and lifecycle contracts for private task-completion photos.
 *
 * The tests construct drafts directly so the core:data test APK does not need
 * its own FileProvider declaration. Camera FileProvider wiring is covered at
 * the app integration boundary.
 */
@RunWith(AndroidJUnit4::class)
class TaskCompletionPhotoStoreTest {

    private lateinit var context: Context
    private lateinit var draftDirectory: File
    private lateinit var photoDirectory: File
    private lateinit var store: TaskCompletionPhotoStore
    private val externalFilesToDelete = mutableListOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        draftDirectory = File(context.cacheDir, "task_completion_drafts")
        photoDirectory = File(context.filesDir, "task_completion_photos")
        resetPrivateDirectory(draftDirectory)
        resetPrivateDirectory(photoDirectory)
        externalFilesToDelete.clear()
        store = TaskCompletionPhotoStore(context)
    }

    @After
    fun tearDown() {
        draftDirectory.deleteRecursively()
        photoDirectory.deleteRecursively()
        externalFilesToDelete.forEach(File::delete)
    }

    @Test
    fun normalizeThenPromote_resolvesOnlyTheGeneratedUuidPhoto() = runBlocking {
        val photoId = "11111111-1111-4111-8111-111111111111.jpg"
        val draft = createBitmapDraft(photoId)

        val normalized = store.normalizeCameraDraft(draft)
        val promotedId = store.promote(normalized)

        assertEquals(photoId, promotedId)
        // Promotion deliberately keeps the retryable draft until the Room
        // transaction succeeds. The ViewModel deletes it only after commit.
        assertTrue(draft.file.exists())
        val resolved = requireNotNull(store.resolvePhoto(promotedId))
        assertTrue(resolved.isFile)
        assertTrue(resolved.length() > 0L)
        assertEquals(photoDirectory.canonicalFile, resolved.parentFile?.canonicalFile)

        File(photoDirectory, "not-a-uuid.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.resolvePhoto("not-a-uuid.jpg"))
        assertNull(store.resolvePhoto("../$photoId"))
        assertNull(store.resolvePhoto(photoId.uppercase()))
        assertTrue(draftDirectory.listFiles().orEmpty().none { it.name.endsWith(".part") })

        store.deleteDraft(draft)
        assertFalse(draft.file.exists())
    }

    @Test
    fun promote_rejectsValidLookingIdWhenDraftFileIsOutsidePrivateDraftDirectory() = runBlocking {
        val photoId = "22222222-2222-4222-8222-222222222222.jpg"
        val externalSentinel = createExternalSentinel(
            name = "outside-draft-sentinel.jpg",
            bytes = byteArrayOf(9, 8, 7),
        )
        val forgedDraft = CompletionPhotoDraft(
            id = photoId,
            file = externalSentinel,
            captureUri = Uri.EMPTY,
        )

        try {
            store.promote(forgedDraft)
            fail("A draft outside the private draft directory must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertTrue(externalSentinel.exists())
        assertEquals(byteArrayOf(9, 8, 7).toList(), externalSentinel.readBytes().toList())
        assertFalse(File(photoDirectory, photoId).exists())
        assertFalse(File(photoDirectory, "$photoId.part").exists())
    }

    @Test
    fun promote_collisionNeverDeletesAnExistingPermanentPhoto() = runBlocking {
        val photoId = "66666666-6666-4666-8666-666666666666.jpg"
        val draft = createBitmapDraft(photoId)
        val existing = File(photoDirectory, photoId).apply {
            writeBytes(byteArrayOf(7, 7, 7, 7))
        }

        try {
            store.promote(draft)
            fail("A generated id collision must not replace an existing photo")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(byteArrayOf(7, 7, 7, 7).toList(), existing.readBytes().toList())
        assertTrue(draft.file.exists())
    }

    @Test
    fun promote_sameCompletePhoto_isIdempotentAfterInterruptedCommit() = runBlocking {
        val photoId = "77777777-7777-4777-8777-777777777777.jpg"
        val draft = createBitmapDraft(photoId)
        val existing = File(photoDirectory, photoId).apply {
            writeBytes(draft.file.readBytes())
        }

        assertEquals(photoId, store.promote(draft))
        assertEquals(draft.file.readBytes().toList(), existing.readBytes().toList())
        assertTrue(draft.file.exists())
    }

    @Test
    fun restoreDrafts_acceptOnlyExistingValidatedPrivateFiles() {
        val photoId = "88888888-8888-4888-8888-888888888888.jpg"
        val photoDraft = createBitmapDraft(photoId)
        val emptyCameraId = "99999999-9999-4999-8999-999999999999.jpg"
        File(draftDirectory, emptyCameraId).createNewFile()

        val restoredPhoto = requireNotNull(store.restorePhotoDraft(photoId))
        val restoredCamera = requireNotNull(store.restoreCameraDraft(emptyCameraId))

        assertEquals(photoDraft.file.canonicalFile, restoredPhoto.file.canonicalFile)
        assertEquals(emptyCameraId, restoredCamera.id)
        assertEquals(0L, restoredCamera.file.length())
        assertNull(store.restorePhotoDraft(emptyCameraId))
        assertNull(store.restoreCameraDraft("../$photoId"))
        assertNull(store.restoreCameraDraft("not-a-uuid.jpg"))
        assertNull(store.restoreCameraDraft("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg"))
    }

    @Test
    fun deletePhoto_withTraversalOrFakeIds_doesNotDeleteExternalFiles() {
        val externalSentinel = createExternalSentinel(
            name = "outside-photo-sentinel.jpg",
            bytes = byteArrayOf(4, 5, 6),
        )

        store.deletePhoto("../outside-photo-sentinel.jpg")
        store.deletePhoto("not-a-uuid.jpg")
        store.deletePhoto("11111111-1111-4111-7111-111111111111.jpg")
        store.deletePhoto(externalSentinel.absolutePath)
        store.deletePhoto(null)

        assertTrue(externalSentinel.exists())
        assertEquals(byteArrayOf(4, 5, 6).toList(), externalSentinel.readBytes().toList())
    }

    @Test
    fun deletePhoto_deletesOnlyMatchingFileInsidePrivatePhotoDirectory() {
        val photoId = "33333333-3333-4333-8333-333333333333.jpg"
        val privatePhoto = File(photoDirectory, photoId)
            .apply { writeBytes(byteArrayOf(1, 3, 5)) }
        val sameNamedExternalFile = createExternalSentinel(
            name = photoId,
            bytes = byteArrayOf(2, 4, 6),
        )

        store.deletePhoto(photoId)

        assertFalse(privatePhoto.exists())
        assertTrue(sameNamedExternalFile.exists())
        assertEquals(byteArrayOf(2, 4, 6).toList(), sameNamedExternalFile.readBytes().toList())
    }

    @Test
    fun importFromUnreadableContentUri_leavesNoPartialOrTargetFile() = runBlocking {
        val unreadable = Uri.parse("content://${context.packageName}.missing/not-an-image")

        try {
            store.importFromUri(unreadable)
            fail("Importing an unreadable content URI must fail")
        } catch (_: Exception) {
            // Expected.
        }

        val leftovers = draftDirectory.listFiles().orEmpty()
        assertTrue(leftovers.none { it.name.endsWith(".part") })
        assertTrue(leftovers.none(File::isFile))
    }

    private fun createBitmapDraft(photoId: String): CompletionPhotoDraft {
        val file = File(draftDirectory, photoId)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(35, 190, 110))
        FileOutputStream(file).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.fd.sync()
        }
        bitmap.recycle()
        return CompletionPhotoDraft(
            id = photoId,
            file = file,
            captureUri = Uri.EMPTY,
        )
    }

    private fun resetPrivateDirectory(directory: File) {
        directory.deleteRecursively()
        assertTrue(directory.mkdirs() || directory.isDirectory)
    }

    private fun createExternalSentinel(name: String, bytes: ByteArray): File =
        File(context.filesDir, name)
            .also(externalFilesToDelete::add)
            .apply { writeBytes(bytes) }
}
