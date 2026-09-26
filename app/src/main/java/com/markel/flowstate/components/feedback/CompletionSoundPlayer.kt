package com.markel.flowstate.components.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single-stream player for the generated completion chime. */
internal class CompletionSoundPlayer(
    context: Context,
) : Closeable {
    private val cacheDirectory = context.applicationContext.cacheDir
    private val stateLock = Any()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private var isLoaded = false
    private var isClosed = false
    private var permittedEventId: Long? = null
    private var soundId = 0
    private var loadAttempt: CompletableDeferred<Boolean>? = null

    init {
        soundPool.setOnLoadCompleteListener { _, loadedSoundId, status ->
            val (completedAttempt, loadedSuccessfully) = synchronized(stateLock) {
                val successful = !isClosed && status == 0
                if (successful) {
                    soundId = loadedSoundId
                    isLoaded = true
                }
                val attempt = loadAttempt
                loadAttempt = null
                attempt to successful
            }
            completedAttempt?.complete(loadedSuccessfully)
        }
    }

    suspend fun prepare(): Boolean {
        var ownsAttempt = false
        val attempt = synchronized(stateLock) {
            when {
                isClosed -> null
                isLoaded -> return true
                loadAttempt != null -> loadAttempt
                else -> CompletableDeferred<Boolean>().also {
                    loadAttempt = it
                    ownsAttempt = true
                }
            }
        }
        if (attempt == null) return false

        if (ownsAttempt) {
            val soundFile = try {
                withContext(Dispatchers.IO) {
                    runCatching { ensureGeneratedChime() }.getOrNull()
                }
            } catch (cancellation: CancellationException) {
                finishLoadAttempt(attempt, successful = false)
                throw cancellation
            }
            if (soundFile == null) {
                finishLoadAttempt(attempt, successful = false)
            } else {
                val loadedId = synchronized(stateLock) {
                    if (isClosed) 0
                    else runCatching { soundPool.load(soundFile.absolutePath, 1) }
                        .getOrDefault(0)
                }
                if (loadedId == 0) finishLoadAttempt(attempt, successful = false)
            }
        }
        return attempt.await()
    }

    suspend fun play(eventId: Long) {
        val permitted = synchronized(stateLock) {
            if (isClosed) false
            else true.also { permittedEventId = eventId }
        }
        if (!permitted || !prepare()) return
        val loadedSoundId = synchronized(stateLock) { soundId }
        playLoaded(loadedSoundId, eventId)
    }

    fun cancel(eventId: Long) {
        synchronized(stateLock) {
            if (permittedEventId == eventId) permittedEventId = null
        }
    }

    override fun close() {
        var pendingAttempt: CompletableDeferred<Boolean>? = null
        val shouldRelease = synchronized(stateLock) {
            if (isClosed) false
            else true.also {
                isClosed = true
                permittedEventId = null
                pendingAttempt = loadAttempt
                loadAttempt = null
            }
        }
        if (shouldRelease) {
            pendingAttempt?.complete(false)
            soundPool.setOnLoadCompleteListener(null)
            soundPool.release()
        }
    }

    private fun playLoaded(loadedSoundId: Int, eventId: Long) {
        synchronized(stateLock) {
            if (!isClosed && permittedEventId == eventId) {
                soundPool.play(
                    loadedSoundId,
                    0.72f,
                    0.72f,
                    1,
                    0,
                    1f,
                )
            }
        }
    }

    private fun finishLoadAttempt(
        attempt: CompletableDeferred<Boolean>,
        successful: Boolean,
    ) {
        synchronized(stateLock) {
            if (loadAttempt === attempt) loadAttempt = null
        }
        attempt.complete(successful)
    }

    private fun ensureGeneratedChime(): File {
        val file = File(cacheDirectory, "flowstate-completion-chime-v1.wav")
        if (!file.isFile || file.length() != CompletionChime.byteCount.toLong()) {
            file.outputStream().buffered().use { output ->
                output.write(CompletionChime.createWavBytes())
            }
        }
        return file
    }
}

@Composable
internal fun rememberCompletionSoundPlayer(): CompletionSoundPlayer {
    val applicationContext = LocalContext.current.applicationContext
    val player = remember(applicationContext) { CompletionSoundPlayer(applicationContext) }
    DisposableEffect(player) {
        onDispose(player::close)
    }
    return player
}
