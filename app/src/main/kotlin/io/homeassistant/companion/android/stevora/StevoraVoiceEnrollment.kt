package io.homeassistant.companion.android.stevora

import android.content.Context
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.os.Build
import java.io.File

/**
 * Explicit, on-device voice enrollment sample capture.
 *
 * This is a data collection prototype, NOT a speaker-verification model or
 * automatic learning engine. Files stay in app-private storage.
 */
internal class StevoraVoiceEnrollment(private val context: Context) {
    var profileId: String? = null
        set(value) {
            check(!isRecording) { "Stop recording before switching profiles" }
            field = value
        }
    private val directory: File
        get() {
            val id = requireNotNull(profileId) { "Select a speaker profile before recording" }
            require(Regex("[a-f0-9-]{36}").matches(id)) { "Invalid profile ID" }
            return File(File(context.filesDir, "stevora_voice_enrollment"), id)
        }

    private var recorder: MediaRecorder? = null
    private var pendingFile: File? = null
    private var recordingStartedAt: Long = 0L

    val sampleCount: Int
        get() = if (profileId == null) 0 else samples().size

    val isRecording: Boolean
        get() = recorder != null

    fun start() {
        check(recorder == null) { "Recording already in progress" }
        check(sampleCount < MAX_SAMPLES) { "Maximum enrollment samples reached" }
        check(directory.exists() || directory.mkdirs()) { "Unable to create private voice directory" }
        val output = File(directory, "sample_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(16000)
            next.setAudioEncodingBitRate(64000)
            next.setOutputFile(output.absolutePath)
            next.prepare()
            next.start()
            recordingStartedAt = SystemClock.elapsedRealtime()
            pendingFile = output
            recorder = next
        } catch (error: Exception) {
            next.reset()
            next.release()
            output.delete()
            throw error
        }
    }

    fun stop(save: Boolean = true): Boolean {
        val active = recorder ?: return false
        val output = pendingFile
        recorder = null
        pendingFile = null
        var valid = false
        val elapsedMs = SystemClock.elapsedRealtime() - recordingStartedAt
        try {
            active.stop()
            valid = save && output != null && elapsedMs >= MIN_DURATION_MS &&
                output.length() >= MIN_BYTES && hasDecodableAudio(output)
        } catch (_: RuntimeException) {
            valid = false
        } finally {
            active.reset()
            active.release()
            if (!valid) output?.delete()
        }
        return valid
    }

    private fun hasDecodableAudio(file: File): Boolean {
        val metadata = MediaMetadataRetriever()
        return try {
            metadata.setDataSource(file.absolutePath)
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasAudio = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            hasAudio && duration >= MIN_DURATION_MS
        } catch (_: Exception) {
            false
        } finally {
            metadata.release()
        }
    }

    /** Only completed and metadata-verified samples can be played. */
    fun samples(): List<File> =
        if (profileId == null) emptyList() else directory.listFiles()
            ?.filter { it.isFile && it.extension == "m4a" && hasDecodableAudio(it) }
            ?.sortedBy { it.name } ?: emptyList()

    fun playLatest(onComplete: () -> Unit = {}): MediaPlayer? = playSample(samples().lastOrNull()?.name ?: return null, onComplete)

    fun playSample(fileName: String, onComplete: () -> Unit = {}): MediaPlayer? {
        check(!isRecording) { "Stop recording before playback" }
        val latest = samples().firstOrNull { it.name == fileName } ?: return null
        val player = MediaPlayer()
        try {
            player.setDataSource(latest.absolutePath)
            player.setOnCompletionListener {
                it.release()
                onComplete()
            }
            player.prepare()
            player.start()
            return player
        } catch (error: Exception) {
            player.release()
            throw error
        }
    }

    fun deleteSample(fileName: String): Boolean {
        check(!isRecording) { "Stop recording before deleting a sample" }
        val sample = samples().firstOrNull { it.name == fileName } ?: return false
        return sample.delete()
    }

    fun deleteAll() {
        stop(save = false)
        if (profileId == null) return
        directory.listFiles()?.filter { it.isFile && it.extension == "m4a" }?.forEach { it.delete() }
        directory.delete()
    }

    companion object {
        const val MAX_SAMPLES = 20
        private const val MIN_BYTES = 2048L
        private const val MIN_DURATION_MS = 1000L
    }
}
