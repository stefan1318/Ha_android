package io.homeassistant.companion.android.stevora

import android.content.Context
import android.media.MediaRecorder
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

    val sampleCount: Int
        get() = if (profileId == null) 0 else directory.listFiles()?.count { it.isFile && it.extension == "m4a" } ?: 0

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
        try {
            active.stop()
            valid = save && output != null && output.length() >= MIN_BYTES
        } catch (_: RuntimeException) {
            valid = false
        } finally {
            active.reset()
            active.release()
            if (!valid) output?.delete()
        }
        return valid
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
    }
}
