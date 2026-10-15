package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class AudioReactiveEngine(
    private val context: Context,
    private val onBoostUpdated: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioReactiveEngine"
        private val SAMPLE_RATES = intArrayOf(44100, 22050, 16000, 8000)
    }

    private val analyzer = AudioSpectrumAnalyzer()
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var workerThread: Thread? = null
    private var actualSampleRate: Int = 44100
    private var bufferSize: Int = 0

    val isActive: Boolean
        get() = isRunning.get() && !isPaused.get()

    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) {
            if (isPaused.get()) {
                resume()
            }
            return true
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        val record = createAudioRecord()
        if (record == null) {
            Log.e(TAG, "Failed to initialize AudioRecord with any supported sample rate")
            return false
        }

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord could not start recording")
                record.release()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            record.release()
            return false
        }

        audioRecord = record
        isRunning.set(true)
        isPaused.set(false)

        workerThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val audioBuffer = ShortArray(AudioSpectrumAnalyzer.FFT_SIZE)

            while (isRunning.get()) {
                if (isPaused.get()) {
                    val boost = analyzer.decay()
                    onBoostUpdated(boost)
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                val currentRecord = audioRecord
                if (currentRecord == null || currentRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    val boost = analyzer.decay()
                    onBoostUpdated(boost)
                    try {
                        Thread.sleep(16)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                val readCount = currentRecord.read(
                    audioBuffer,
                    0,
                    AudioSpectrumAnalyzer.FFT_SIZE
                )

                if (readCount > 0) {
                    val boost = analyzer.processSamples(audioBuffer, 0, readCount)
                    onBoostUpdated(boost)
                } else if (readCount < 0) {
                    // AudioRecord read error, smoothly decay
                    val boost = analyzer.decay()
                    onBoostUpdated(boost)
                }
            }

            // Smooth decay to zero on exit
            for (i in 0 until 10) {
                val boost = analyzer.decay()
                onBoostUpdated(boost)
                try {
                    Thread.sleep(16)
                } catch (_: InterruptedException) {
                    break
                }
            }
            onBoostUpdated(0.0f)
        }.apply {
            name = "AudioReactiveEngineThread"
            isDaemon = true
            start()
        }

        return true
    }

    fun pause() {
        isPaused.set(true)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord on pause", e)
        }
    }

    fun resume() {
        if (!isRunning.get()) {
            start()
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stop()
            return
        }

        try {
            val record = audioRecord
            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                record.startRecording()
                isPaused.set(false)
            } else {
                stop()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming AudioRecord", e)
            stop()
            start()
        }
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        isPaused.set(false)

        try {
            workerThread?.interrupt()
            workerThread?.join(300)
        } catch (e: Exception) {
            Log.w(TAG, "Error joining worker thread", e)
        }
        workerThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping/releasing AudioRecord", e)
        }
        audioRecord = null
        analyzer.reset()
        onBoostUpdated(0.0f)
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        for (rate in SAMPLE_RATES) {
            try {
                val minSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat)
                if (minSize > 0) {
                    val buffer = maxOf(minSize, AudioSpectrumAnalyzer.FFT_SIZE * 4)
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        rate,
                        channelConfig,
                        audioFormat,
                        buffer
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        actualSampleRate = rate
                        bufferSize = buffer
                        Log.i(TAG, "AudioRecord initialized at ${rate}Hz, buffer: $buffer bytes")
                        return record
                    } else {
                        record.release()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sample rate $rate not supported: ${e.message}")
            }
        }
        return null
    }
}
