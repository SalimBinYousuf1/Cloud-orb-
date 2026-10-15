package com.example.graphics

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp

class CloudOrbRenderer : GLSurfaceView.Renderer {

    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
        -1.0f,  1.0f,
         1.0f, -1.0f,
         1.0f,  1.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadVertices)
            position(0)
        }

    private var programId = 0
    private var aPositionLoc = -1
    private var uResolutionLoc = -1
    private var uTimeLoc = -1
    private var uAudioBoostLoc = -1

    private var surfaceWidth = 280
    private var surfaceHeight = 280

    // High precision continuous phase tracking
    private var lastFrameTimeNs: Long = 0L
    private var continuousPhase: Float = 0.0f
    private var smoothedAudioBoost: Float = 0.0f

    @Volatile
    private var targetAudioBoost: Float = 0.0f

    fun setAudioBoost(boost: Float) {
        targetAudioBoost = boost
    }

    fun getAudioBoost(): Float = targetAudioBoost

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Transparent background for seamless circular rendering
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        programId = CloudShader.createProgram(
            CloudShader.VERTEX_SHADER_SOURCE,
            CloudShader.FRAGMENT_SHADER_SOURCE
        )

        if (programId != 0) {
            aPositionLoc = GLES20.glGetAttribLocation(programId, "a_position")
            uResolutionLoc = GLES20.glGetUniformLocation(programId, "resolution")
            uTimeLoc = GLES20.glGetUniformLocation(programId, "time")
            uAudioBoostLoc = GLES20.glGetUniformLocation(programId, "audioBoost")
        }

        lastFrameTimeNs = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = if (width > 0) width else 1
        surfaceHeight = if (height > 0) height else 1
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        lastFrameTimeNs = SystemClock.elapsedRealtimeNanos()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (lastFrameTimeNs == 0L) {
            lastFrameTimeNs = nowNs
        }
        val dt = ((nowNs - lastFrameTimeNs) * 1e-9f).coerceIn(0.001f, 0.05f)
        lastFrameTimeNs = nowNs

        // Exponential smoothing filter for responsive, silky smooth audio boost transitions
        val target = targetAudioBoost.coerceIn(0.0f, 2.5f)
        val alpha = 1.0f - exp(-dt * 9.5f)
        smoothedAudioBoost += (target - smoothedAudioBoost) * alpha

        // Integrate phase continuously for vibrant motion driven by sounds
        continuousPhase += dt * (0.28f + smoothedAudioBoost * 1.25f)

        if (programId == 0) return

        GLES20.glUseProgram(programId)

        if (aPositionLoc >= 0) {
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(
                aPositionLoc,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )
            GLES20.glEnableVertexAttribArray(aPositionLoc)
        }

        if (uResolutionLoc >= 0) {
            GLES20.glUniform2f(uResolutionLoc, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        }

        if (uTimeLoc >= 0) {
            GLES20.glUniform1f(uTimeLoc, continuousPhase)
        }

        if (uAudioBoostLoc >= 0) {
            GLES20.glUniform1f(uAudioBoostLoc, smoothedAudioBoost)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        if (aPositionLoc >= 0) {
            GLES20.glDisableVertexAttribArray(aPositionLoc)
        }
    }
}
