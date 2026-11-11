package com.example.freelocalplayer.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Offline, approximate chord estimation. Does not reconstruct a musical score.
 *
 * Every 0.5 seconds of decoded audio produces one time-aligned cue. An undecidable
 * window produces "—" rather than misleadingly holding a prior chord or inventing
 * a harmonic change. A pooled chromagram is considerably more robust than asking
 * for three identical frame-level chord decisions (the old algorithm).
 *
 * Bounded buffers: 8192 samples, one FFT, 12 pitch-class accumulators, and at most
 * 1200 cues for a 10-minute track. No audio or models are uploaded.
 */
class AudioChroma(private val inputSampleRate: Int) {
    init { require(inputSampleRate in 11_025..384_000) }

    companion object {
        const val TARGET_RATE = 11_025
        const val FFT_SIZE = 4096
        const val HOP = 1024
        const val SEGMENT_MS = 500L
        const val UNKNOWN_CHORD = "—"
        private val names = arrayOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
        private val templates = listOf(
            "" to intArrayOf(0, 4, 7),
            "m" to intArrayOf(0, 3, 7),
            "7" to intArrayOf(0, 4, 7, 10),
            "m7" to intArrayOf(0, 3, 7, 10),
            "maj7" to intArrayOf(0, 4, 7, 11),
            "sus2" to intArrayOf(0, 2, 7),
            "sus4" to intArrayOf(0, 5, 7)
        )
    }

    private val ring = FloatArray(FFT_SIZE)
    private val hann = DoubleArray(FFT_SIZE) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1)) }
    private val real = DoubleArray(FFT_SIZE)
    private val imag = DoubleArray(FFT_SIZE)
    private val spectrum = DoubleArray(FFT_SIZE / 2 + 1)
    private val pooledChroma = DoubleArray(12)
    private val observations = ArrayList<ChordPathDecoder.Window>()
    private var writeIndex = 0
    private var phase = 0L
    private var accumulator = 0.0
    private var inputCount = 0
    private var downsampled = 0L
    private var nextWindowAt = FFT_SIZE.toLong()
    private var nextSegmentEnd = SEGMENT_MS
    private var framesInSegment = 0
    private var usefulFrames = 0
    private var totalFftFrames = 0
    private var totalUsefulFrames = 0
    private var finished = false
    private var decoded: List<ChordCue> = emptyList()

    val processedMilliseconds: Long get() = downsampled * 1000L / TARGET_RATE

    /** Counters for diagnostics only. Do not include file paths or audio content. */
    fun diagnosticSummary(): String =
        "processedMs=$processedMilliseconds fftFrames=$totalFftFrames " +
            "harmonicFrames=$totalUsefulFrames windows=${observations.size} " +
            "harmonicWindows=${observations.count { it.chroma != null }} " +
            "resultCues=${decoded.size} " + ChordPathDecoder.evidenceSummary(observations)


    fun consume(sample: Float) {
        accumulator += sample.coerceIn(-1f, 1f).toDouble()
        inputCount++
        phase += TARGET_RATE
        if (phase < inputSampleRate) return
        phase -= inputSampleRate
        val value = (accumulator / inputCount).toFloat()
        accumulator = 0.0
        inputCount = 0
        ring[writeIndex] = value
        writeIndex = (writeIndex + 1) % FFT_SIZE
        downsampled++
        if (downsampled >= nextWindowAt) {
            calculateFrame()
            nextWindowAt += HOP
        }
    }

    fun finish(): List<ChordCue> {
        if (!finished) {
            if (processedMilliseconds > (nextSegmentEnd - SEGMENT_MS) && framesInSegment > 0) {
                flushSegment()
            }
            finished = true
            decoded = ChordPathDecoder.decode(observations)
        }
        return decoded
    }

    private fun calculateFrame() {
        // Center the FFT window in time rather than using the newest sample timestamp.
        val centerMs = (downsampled - FFT_SIZE / 2L) * 1000L / TARGET_RATE
        while (centerMs >= nextSegmentEnd && observations.size < 3000) {
            flushSegment()
        }
        framesInSegment++
        totalFftFrames++
        var energy = 0.0
        for (i in 0 until FFT_SIZE) {
            val sample = ring[(writeIndex + i) % FFT_SIZE].toDouble()
            real[i] = sample * hann[i]
            imag[i] = 0.0
            energy += sample * sample
        }
        if (energy / FFT_SIZE < 0.000015) return
        fft(real, imag)
        for (i in spectrum.indices) spectrum[i] = hypot(real[i], imag[i])
        val frameChroma = DoubleArray(12)
        val minBin = (65.0 * FFT_SIZE / TARGET_RATE).toInt().coerceAtLeast(2)
        val maxBin = (1800.0 * FFT_SIZE / TARGET_RATE).toInt().coerceAtMost(FFT_SIZE / 2 - 1)
        for (bin in minBin until maxBin) {
            val magnitude = spectrum[bin]
            if (magnitude <= spectrum[bin - 1] || magnitude <= spectrum[bin + 1]) continue
            val hz = bin.toDouble() * TARGET_RATE / FFT_SIZE
            val semitone = 69.0 + 12.0 * log2(hz / 440.0)
            val nearest = semitone.roundToInt()
            if (abs(semitone - nearest) > 0.48) continue
            val pitchClass = ((nearest % 12) + 12) % 12
            frameChroma[pitchClass] += magnitude.pow(0.72) / (1.0 + hz / 900.0)
        }
        val total = frameChroma.sum()
        if (total < 1e-8) return
        usefulFrames++
        totalUsefulFrames++
        for (i in 0..11) pooledChroma[i] += frameChroma[i] / total
    }

    private fun flushSegment() {
        val startMs = nextSegmentEnd - SEGMENT_MS
        // Persist observations, not noisy frame-by-frame decisions. Viterbi
        // processes the whole sequence and respects silent/unresolved windows.
        val chroma = if (usefulFrames >= 2) pooledChroma.copyOf() else null
        observations += ChordPathDecoder.Window(startMs, chroma, usefulFrames)
        nextSegmentEnd += SEGMENT_MS
        pooledChroma.fill(0.0)
        framesInSegment = 0
        usefulFrames = 0
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val v = im[i]; im[i] = im[j]; im[j] = v
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wr = cos(angle)
            val wi = sin(angle)
            val half = length / 2
            var base = 0
            while (base < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until half) {
                    val a = base + k
                    val b = a + half
                    val tr = cr * re[b] - ci * im[b]
                    val ti = cr * im[b] + ci * re[b]
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val nextR = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nextR
                }
                base += length
            }
            length = length shl 1
        }
    }
}
