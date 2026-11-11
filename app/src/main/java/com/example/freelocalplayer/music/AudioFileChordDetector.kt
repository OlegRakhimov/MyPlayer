package com.example.freelocalplayer.music

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Local chord estimation from audio/video track: Android system decoder -> PCM -> chromagram.
 * No network, external models, audio uploads or access to other app files.
 * Analysis capped at first 10 minutes to avoid unbounded CPU use on a phone.
 */
object AudioFileChordDetector {
    private const val DIAG_TAG = "FLP_CHORD_DIAG"
    const val MAX_DURATION_MS = 10 * 60 * 1000L

    fun analyze(
        context: Context,
        uri: Uri,
        progress: (Int) -> Unit = {},
        keepGoing: () -> Boolean = { true }
    ): List<ChordCue> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var stage = "extractor"
        try {
            Log.i(DIAG_TAG, "START")
            extractor.setDataSource(context, uri, null)
            stage = "audio-track"
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("В файле нет аудиодорожки")
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("Не определён формат аудио")
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L) / 1000L else 0L
            val durationTarget = if (durationMs > 0) durationMs.coerceAtMost(MAX_DURATION_MS.toLong())
                else MAX_DURATION_MS.toLong()
            Log.i(DIAG_TAG, "SOURCE mime=$mime durationMs=$durationMs")
            extractor.selectTrack(audioTrack)
            stage = "pcm-decoding"

            var streamAnalyzer: AudioChroma? = null
            var configuredRate = 0
            var reportPercent = -1

            fun acceptPcm(bytes: ByteBuffer, sampleFormat: MediaFormat) {
                val rate = sampleFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = sampleFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (rate !in 11_025..384_000 || channels !in 1..8)
                    throw IOException("Неподдерживаемый формат звука: $rate Гц, $channels каналов")
                if (streamAnalyzer == null) {
                    streamAnalyzer = AudioChroma(rate)
                    configuredRate = rate
                    Log.i(DIAG_TAG, "PCM_START rate=$rate channels=$channels")
                }
                if (rate != configuredRate) throw IOException("Частота дискретизации изменилась во время анализа")
                val encoding = if (sampleFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    sampleFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                val bytesPerSample = when (encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> 1
                    AudioFormat.ENCODING_PCM_16BIT -> 2
                    AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                    else -> throw IOException("Кодек вернул неподдерживаемый PCM: $encoding")
                }
                val audio = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                val frameBytes = channels * bytesPerSample
                while (audio.remaining() >= frameBytes) {
                    var mono = 0.0
                    repeat(channels) {
                        val value = when (encoding) {
                            AudioFormat.ENCODING_PCM_8BIT -> ((audio.get().toInt() and 255) - 128) / 128.0
                            AudioFormat.ENCODING_PCM_16BIT -> audio.short.toDouble() / 32768.0
                            AudioFormat.ENCODING_PCM_FLOAT -> audio.float.toDouble().takeIf { it.isFinite() } ?: 0.0
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                val lo = audio.get().toInt() and 255
                                val mid = audio.get().toInt() and 255
                                val hi = audio.get().toInt()
                                ((hi shl 16) or (mid shl 8) or lo) / 8388608.0
                            }
                            else -> audio.int.toDouble() / 2147483648.0
                        }
                        mono += value
                    }
                    streamAnalyzer!!.consume((mono / channels).toFloat())
                    if (streamAnalyzer!!.processedMilliseconds >= MAX_DURATION_MS) break
                }
                val percent = (streamAnalyzer!!.processedMilliseconds * 100L / durationTarget)
                    .coerceIn(0L, 100L).toInt()
                if (percent > reportPercent) { reportPercent = percent; progress(percent) }
            }

            if (mime == "audio/raw") {
                val input = ByteBuffer.allocateDirect(128 * 1024)
                while ((streamAnalyzer?.processedMilliseconds ?: 0L) < MAX_DURATION_MS) {
                    if (!keepGoing()) throw java.util.concurrent.CancellationException("Анализ отменён")
                    input.clear()
                    val read = extractor.readSampleData(input, 0)
                    if (read < 0) break
                    input.position(0)
                    input.limit(read)
                    acceptPcm(input, format)
                    if (!extractor.advance()) break
                }
            } else {
                codec = try { MediaCodec.createDecoderByType(mime) }
                    catch (error: Exception) { throw IOException("На устройстве нет декодера для $mime", error) }
                val decoder = codec!!
                decoder.configure(format, null, null, 0)
                decoder.start()
                val info = MediaCodec.BufferInfo()
                var endInput = false
                var endOutput = false
                var idlePolls = 0
                var pcmFormat = format
                while (!endOutput && (streamAnalyzer?.processedMilliseconds ?: 0L) < MAX_DURATION_MS) {
                    if (!keepGoing()) throw java.util.concurrent.CancellationException("Анализ отменён")
                    if (!endInput) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val input = decoder.getInputBuffer(index) ?: throw IOException("Нет входного буфера кодека")
                            input.clear()
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                endInput = true
                            } else {
                                val timeUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(index, 0, size, timeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            pcmFormat = decoder.outputFormat
                            idlePolls = 0
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            idlePolls++
                            if (idlePolls > 500) throw IOException("Декодер не возвращает аудиоданные")
                        }
                        else -> if (outputIndex >= 0) {
                            idlePolls = 0
                            if (info.size > 0) {
                                val buffer = decoder.getOutputBuffer(outputIndex)
                                    ?: throw IOException("Нет выходного буфера кодека")
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                acceptPcm(buffer, pcmFormat)
                            }
                            endOutput = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            stage = "chord-classification"
            val result = streamAnalyzer?.finish().orEmpty()
            val summary = streamAnalyzer?.diagnosticSummary() ?: "no-pcm"
            val identified = result.count { it.name != AudioChroma.UNKNOWN_CHORD }
            val distinct = result.filter { it.name != AudioChroma.UNKNOWN_CHORD }
                .map { it.name }.distinct().size
            Log.i(DIAG_TAG, "RESULT $summary identified=$identified distinct=$distinct")
            if (result.isEmpty()) {
                Log.w(DIAG_TAG, "EMPTY_RESULT stage=$stage; $summary")
                throw IOException(
                    "Декодирование завершилось, но уверенно распознать гармонию не удалось. " +
                        "Проверь, есть ли звук в файле; для сложной записи попробуй MIDI " +
                        "или ручные аккорды. Старый анализ сохранён."
                )
            }
            progress(100)
            return result
        } catch (error: Exception) {
            Log.e(DIAG_TAG, "FAIL stage=$stage type=${error.javaClass.simpleName} " +
                "message=${error.message?.replace('\n', ' ')}")
            throw error
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            extractor.release()
        }
    }
}
