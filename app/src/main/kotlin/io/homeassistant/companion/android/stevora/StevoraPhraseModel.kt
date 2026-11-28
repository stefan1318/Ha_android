package io.homeassistant.companion.android.stevora

import android.annotation.SuppressLint
import android.media.*
import android.util.AtomicFile
import java.io.*
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local speaker-dependent acoustic templates. Samples and features stay in app storage. */
internal object StevoraPhraseModel {
    private const val rate = 16000
    private const val window = 320
    private const val bands = 12
    private const val frames = 24
    private val coefficients = DoubleArray(bands) { 2 * cos(2 * PI * 240 * 1.26.pow(it) / rate) }

    fun samples(dir: File) = dir.listFiles().orEmpty().filter { it.isFile && it.extension == "m4a" && it.name.startsWith("sample_") }.sortedBy { it.name }
    private fun model(dir: File) = File(dir, "phrase-v1.bin")
    private fun fingerprint(files: List<File>) = files.joinToString("|") { "${it.name}/${it.length()}/${it.lastModified()}" }
    private data class Profile(val vectors: List<FloatArray>)

    private fun decode(file: File): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val format = extractor.getTrackFormat(0)
            extractor.selectTrack(0)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var hz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val pcm = ArrayList<Short>()
            val info = MediaCodec.BufferInfo()
            var sentEnd = false
            val deadline = android.os.SystemClock.elapsedRealtime() + 12000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (!sentEnd) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val input = requireNotNull(decoder.getInputBuffer(index))
                        val size = extractor.readSampleData(input, 0)
                        sentEnd = size < 0
                        decoder.queueInputBuffer(index, 0, max(0, size), max(0L, extractor.sampleTime),
                            if (sentEnd) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!sentEnd) extractor.advance()
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    hz = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                if (index >= 0) {
                    val buf = requireNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
                    buf.position(info.offset)
                    repeat(info.size / (2 * channels)) {
                        pcm.add(buf.short)
                        repeat(channels - 1) { buf.short }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    require(pcm.size <= hz * 12)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return ShortArray((pcm.size.toLong() * rate / hz).toInt()) { pcm[(it.toLong() * hz / rate).toInt()] }
                    }
                }
            }
            error("Decode timeout")
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    private fun rms(data: ShortArray, start: Int) = sqrt((0 until window).sumOf { data[start + it].toDouble().pow(2) } / window)
    private fun features(data: ShortArray): FloatArray {
        val levels = DoubleArray(data.size / window) { rms(data, it * window) }
        val gate = max(65.0, (levels.maxOrNull() ?: 0.0) * 0.09)
        val first = levels.indexOfFirst { it > gate }
        val last = levels.indexOfLast { it > gate }
        require(first >= 0 && last - first in 12..175)
        return FloatArray(frames * bands).also { result ->
            for (t in 0 until frames) {
                val start = (first + t * (last - first) / (frames - 1)) * window
                val spectrum = DoubleArray(bands) { band ->
                    var prev = 0.0
                    var prev2 = 0.0
                    for (j in 0 until window) {
                        val next = data[start + j] + coefficients[band] * prev - prev2
                        prev2 = prev
                        prev = next
                    }
                    ln(1 + max(0.0, prev * prev + prev2 * prev2 - coefficients[band] * prev * prev2))
                }
                val mean = spectrum.average()
                val norm = sqrt(spectrum.sumOf { (it - mean).pow(2) }).coerceAtLeast(1.0)
                for (b in 0 until bands) result[t * bands + b] = ((spectrum[b] - mean) / norm).toFloat()
            }
        }
    }

    private fun distance(a: FloatArray, b: FloatArray): Double {
        // Allow a small offset in the phrase boundary without accepting
        // arbitrary rearrangements of its acoustic frames.
        return (-2..2).minOf { shift ->
            val start = max(0, -shift)
            val end = min(frames, frames - shift)
            var sum = 0.0
            for (frame in start until end) {
                for (band in 0 until bands) {
                    val delta = a[frame * bands + band] - b[(frame + shift) * bands + band]
                    sum += delta.toDouble() * delta
                }
            }
            sum / (end - start) + abs(shift) * 0.025
        }
    }

    private fun score(vector: FloatArray, templates: List<FloatArray>): Double =
        templates.map { distance(vector, it) }.sorted().take(2).average()

    private fun threshold(vectors: List<FloatArray>): Double {
        val heldOutScores = vectors.mapIndexed { index, vector ->
            score(vector, vectors.filterIndexed { candidate, _ -> index != candidate })
        }.sorted()
        return (heldOutScores[heldOutScores.size / 2] * 1.20).coerceIn(0.16, 0.66)
    }
    fun train(dir: File) {
        val files = samples(dir)
        require(files.size in 3..20)
        val signature = fingerprint(files)
        val vectors = files.map { features(decode(it)) }
        val threshold = threshold(vectors)
        require(fingerprint(samples(dir)) == signature)
        val atomic = AtomicFile(model(dir))
        val stream = atomic.startWrite()
        try {
            val out = DataOutputStream(stream)
            out.writeUTF(signature)
            out.writeDouble(threshold)
            out.writeInt(vectors.size)
            vectors.forEach { vector -> vector.forEach { out.writeFloat(it) } }
            out.flush()
            atomic.finishWrite(stream)
        } catch (e: Throwable) { atomic.failWrite(stream); throw e }
    }

    private fun read(dir: File): Profile = DataInputStream(AtomicFile(model(dir)).openRead()).use { input ->
        require(input.readUTF() == fingerprint(samples(dir)))
        input.readDouble()
        val count = input.readInt()
        require(count in 3..20)
        Profile(List(count) { FloatArray(frames * bands) { input.readFloat() } })
    }
    fun ready(dir: File) = runCatching { read(dir) }.isSuccess

    @SuppressLint("MissingPermission")
    suspend fun listen(dir: File, onRecording: suspend () -> Unit = {}) {
        val profile = read(dir)
        // Recalibrate legacy profiles against the matcher used for live speech.
        val cutoff = threshold(profile.vectors)
        val bufferSize = max(4096, AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
        // Use the same capture source as the enrollment samples.
        val mic = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        try {
            check(mic.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
            mic.startRecording()
            check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start recording" }
            android.util.Log.i("StevoraWake", "Microphone recording started")
            onRecording()
            val frame = ShortArray(window)
            val phrase = ShortArray(rate * 4)
            val preRoll = ShortArray(window * 5)
            var preRollFrames = 0
            var preRollCursor = 0
            var used = 0
            var quiet = 0
            var ambient = 70.0
            while (true) {
                currentCoroutineContext().ensureActive()
                var filled = 0
                while (filled < window) {
                    val n = mic.read(frame, filled, window - filled)
                    require(n > 0)
                    filled += n
                }
                val level = rms(frame, 0)
                val speech = level > max(120.0, ambient * 2.6)
                if (!speech && used == 0) {
                    ambient = 0.95 * ambient + 0.05 * level
                    frame.copyInto(preRoll, preRollCursor * window)
                    preRollCursor = (preRollCursor + 1) % 5
                    preRollFrames = min(preRollFrames + 1, 5)
                    continue
                }
                if (used == 0) {
                    repeat(preRollFrames) { index ->
                        val offset = ((preRollCursor - preRollFrames + index + 5) % 5) * window
                        preRoll.copyInto(phrase, used, offset, offset + window)
                        used += window
                    }
                    preRollFrames = 0
                }
                if (speech || used > 0) {
                    frame.copyInto(phrase, used)
                    used += window
                    quiet = if (speech) 0 else quiet + 1
                    if (quiet >= 18 || used == phrase.size) {
                        val vector = runCatching { features(phrase.copyOf(used)) }.getOrNull()
                        if (vector != null) {
                            val candidate = score(vector, profile.vectors)
                            android.util.Log.i("StevoraWake", "Phrase candidate: score=${(candidate * 1000).toInt()}, cutoff=${(cutoff * 1000).toInt()}")
                            if (candidate <= cutoff) {
                                android.util.Log.i("StevoraWake", "Local phrase matched")
                                return
                            }
                        } else {
                            android.util.Log.i("StevoraWake", "Phrase candidate did not meet acoustic segment requirements")
                        }
                        used = 0
                        quiet = 0
                    }
                }
            }
        } finally {
            runCatching { mic.stop() }
            mic.release()
        }
    }
}
