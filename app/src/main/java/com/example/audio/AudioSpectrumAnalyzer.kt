package com.example.audio

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 256-point Radix-2 FFT with precomputed twiddle factors and Hanning window.
 * Strictly allocation-free during real-time processing.
 * Replicates Web Audio API AnalyserNode (fftSize=256, 128 frequency bins,
 * smoothingTimeConstant=0.85, byteFrequencyData 0..255 scaling).
 */
class AudioSpectrumAnalyzer {
    companion object {
        const val FFT_SIZE = 256
        const val BIN_COUNT = FFT_SIZE / 2 // 128 bins
        private const val MIN_DB = -100.0f
        private const val MAX_DB = -30.0f
        private const val DB_RANGE = MAX_DB - MIN_DB // 70 dB
        private const val SMOOTHING_TIME_CONSTANT = 0.85f
    }

    private val window = FloatArray(FFT_SIZE)
    private val bitReversed = IntArray(FFT_SIZE)
    private val cosTable = FloatArray(FFT_SIZE / 2)
    private val sinTable = FloatArray(FFT_SIZE / 2)

    private val real = FloatArray(FFT_SIZE)
    private val imag = FloatArray(FFT_SIZE)
    private val smoothedBins = FloatArray(BIN_COUNT)

    @Volatile
    private var audioBoost: Float = 0.0f

    init {
        // Precompute Hanning window: w[n] = 0.5 * (1 - cos(2*pi*n / (N-1)))
        val pi2 = (2.0 * Math.PI).toFloat()
        for (n in 0 until FFT_SIZE) {
            window[n] = 0.5f * (1.0f - cos(pi2 * n / (FFT_SIZE - 1)))
        }

        // Precompute bit reversal permutation
        var j = 0
        for (i in 0 until FFT_SIZE) {
            bitReversed[i] = j
            var bit = FFT_SIZE shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
        }

        // Precompute twiddle factors
        for (k in 0 until FFT_SIZE / 2) {
            val angle = -2.0 * Math.PI * k / FFT_SIZE
            cosTable[k] = cos(angle).toFloat()
            sinTable[k] = sin(angle).toFloat()
        }
    }

    /**
     * Process 256 PCM 16-bit audio samples and return the current smoothed audio boost value.
     */
    fun processSamples(samples: ShortArray, offset: Int, count: Int): Float {
        val n = minOf(count, FFT_SIZE)

        // Window and bit-reverse copy into real buffer, clear imag
        for (i in 0 until n) {
            val rev = bitReversed[i]
            val normalizedSample = (samples[offset + i] / 32768.0f) * window[i]
            real[rev] = normalizedSample
            imag[rev] = 0.0f
        }
        for (i in n until FFT_SIZE) {
            val rev = bitReversed[i]
            real[rev] = 0.0f
            imag[rev] = 0.0f
        }

        // In-place Cooley-Tukey Radix-2 FFT
        var len = 2
        while (len <= FFT_SIZE) {
            val halfLen = len shr 1
            val step = FFT_SIZE / len
            var k = 0
            while (k < FFT_SIZE) {
                var w = 0
                for (m in 0 until halfLen) {
                    val c = cosTable[w]
                    val s = sinTable[w]
                    val uR = real[k + m]
                    val uI = imag[k + m]
                    val vR = real[k + m + halfLen] * c - imag[k + m + halfLen] * s
                    val vI = real[k + m + halfLen] * s + imag[k + m + halfLen] * c

                    real[k + m] = uR + vR
                    imag[k + m] = uI + vI
                    real[k + m + halfLen] = uR - vR
                    imag[k + m + halfLen] = uI - vI

                    w += step
                }
                k += len
            }
            len = len shl 1
        }

        // Calculate magnitude in dB, scale to 0..255 byte equivalent, and smooth
        var sum = 0.0f
        val normFactor = 2.0f / FFT_SIZE

        for (i in 0 until BIN_COUNT) {
            val r = real[i] * normFactor
            val im = imag[i] * normFactor
            val mag = sqrt(r * r + im * im)

            val db = if (mag > 1e-6f) 20.0f * log10(mag) else MIN_DB
            val clampedDb = db.coerceIn(MIN_DB, MAX_DB)
            val byteVal = ((clampedDb - MIN_DB) / DB_RANGE) * 255.0f

            // Web Audio smoothingTimeConstant = 0.85
            smoothedBins[i] = smoothedBins[i] * SMOOTHING_TIME_CONSTANT + byteVal * (1.0f - SMOOTHING_TIME_CONSTANT)
            sum += smoothedBins[i]
        }

        val volume = sum / BIN_COUNT

        // HTML audioBoostValue formula:
        // audioBoostValue = audioBoostValue * 0.85 + (volume / 40.0) * 0.15
        audioBoost = audioBoost * 0.85f + (volume / 40.0f) * 0.15f
        return audioBoost
    }

    /**
     * Smooth decay when silence or inactive
     */
    fun decay(): Float {
        audioBoost *= 0.85f
        if (audioBoost < 0.001f) {
            audioBoost = 0.0f
        }
        for (i in 0 until BIN_COUNT) {
            smoothedBins[i] *= 0.85f
        }
        return audioBoost
    }

    fun reset() {
        audioBoost = 0.0f
        for (i in 0 until BIN_COUNT) {
            smoothedBins[i] = 0.0f
        }
    }
}
