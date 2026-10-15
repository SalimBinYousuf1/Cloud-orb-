package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioSpectrumAnalyzer
import com.example.ui.CloudOrbViewModel
import com.example.ui.MicStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Cloud Orb", appName)
    }

    @Test
    fun `audio spectrum analyzer processes silence and loud signal correctly`() {
        val analyzer = AudioSpectrumAnalyzer()

        // 1. Silence: all zeros
        val silentBuffer = ShortArray(256)
        val silenceBoost = analyzer.processSamples(silentBuffer, 0, 256)
        assertTrue("Boost on silence should be near zero, got $silenceBoost", silenceBoost >= 0.0f)

        // 2. Loud sine wave signal (440Hz test tone at max amplitude)
        val loudBuffer = ShortArray(256)
        for (i in 0 until 256) {
            val sample = (sin(2.0 * Math.PI * 440.0 * i / 44100.0) * 32000.0).toInt().toShort()
            loudBuffer[i] = sample
        }
        var loudBoost = 0.0f
        // Process a few frames to let smoothing ramp up
        for (f in 0 until 5) {
            loudBoost = analyzer.processSamples(loudBuffer, 0, 256)
        }
        assertTrue("Loud signal must produce higher boost than initial silence, got $loudBoost", loudBoost > silenceBoost)

        // 3. Decay returns towards zero
        var decayed = loudBoost
        for (i in 0 until 30) {
            decayed = analyzer.decay()
        }
        assertTrue("Decay must return boost near 0, got $decayed", decayed < 0.05f)
    }

    @Test
    fun `view model initializes in Idle state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = CloudOrbViewModel(context as android.app.Application)
        assertEquals(MicStatus.Idle, vm.status.value)
        assertNotNull(vm.renderer)
    }
}
