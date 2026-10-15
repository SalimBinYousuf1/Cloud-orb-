package com.example.graphics

import android.opengl.GLES20
import android.util.Log

object CloudShader {
    private const val TAG = "CloudShader"

    const val VERTEX_SHADER_SOURCE = """
        attribute vec2 a_position;
        void main() {
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
    """

    /**
     * Fragment shader with:
     * - Idle cloud occupying the lower half of the circle from below (up to the center equator).
     * - Vibrant, lively macro billowing and expansion upwards into the deep blue sky driven by voice.
     * - Fine, high-frequency smoky curls and micro-eddies reacting vibrantly to sounds.
     * - 100% smooth, continuous phase integration with zero stutter or abrupt snaps.
     */
    const val FRAGMENT_SHADER_SOURCE = """
        precision highp float;
        uniform vec2 resolution;
        uniform float time;
        uniform float audioBoost;

        // Rotation matrix for organic fluid mixing without axial grid artifacts
        const mat2 rot = mat2(0.80, -0.60, 0.60, 0.80);

        float hash21(vec2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        // Smooth cubic-interpolated value noise
        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash21(i);
            float b = hash21(i + vec2(1.0, 0.0));
            float c = hash21(i + vec2(0.0, 1.0));
            float d = hash21(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        // 4-octave rotated fractal Brownian motion
        float fbm(vec2 p) {
            float v = 0.0;
            v += 0.5200 * noise(p);
            p = rot * p * 2.02 + vec2(17.1, 9.2);
            v += 0.2600 * noise(p);
            p = rot * p * 2.03 - vec2(8.3, 4.7);
            v += 0.1300 * noise(p);
            p = rot * p * 2.04 + vec2(5.2, 11.4);
            v += 0.0650 * noise(p);
            return v;
        }

        void main() {
            vec2 uv = gl_FragCoord.xy / resolution;
            float aspect = resolution.x / max(resolution.y, 1.0);
            vec2 p = uv;
            p.x *= aspect;

            float t = time;
            float boost = clamp(audioBoost, 0.0, 2.5);

            // Coordinated fluid flow: horizontal drift combined with gentle rising heat
            vec2 flow = vec2(t * 0.08, -t * 0.025);

            // Dual domain warping creates natural swirling vortices
            vec2 q = vec2(
                fbm(p * 1.8 + flow),
                fbm(p * 1.8 + flow + vec2(5.2, 1.3))
            );

            // Vibrant swirling eddies that amplify and mix dynamically with voice sound
            vec2 swirl = vec2(
                sin(p.y * 3.2 + t * 0.5),
                cos(p.x * 3.2 - t * 0.45)
            ) * (0.05 + boost * 0.18);

            vec2 r = vec2(
                fbm(p * 2.2 + 1.25 * q + swirl + vec2(t * 0.05, -t * 0.04)),
                fbm(p * 2.2 + 1.25 * q - swirl + vec2(8.3, 2.8))
            );

            vec2 warpedP = p + (r - 0.5) * (0.88 + boost * 0.32);

            // Multi-scale cloud noise fields
            float field = fbm(warpedP * 1.6 + vec2(t * 0.035, -t * 0.02));
            float detail = noise(warpedP * 4.4 + vec2(t * 0.08, -t * 0.06));

            // Fine, high-frequency turbulence that flickers and curls vibrantly to sound
            float fineTurbulence = noise(warpedP * 8.8 + vec2(t * 0.28, -t * 0.22));
            float microCurl = noise(warpedP * 14.0 - vec2(t * 0.40, t * 0.35));

            // Idle cloud is anchored occupying the lower half from below (center equator ~0.50)
            float baseEquator = 0.50;
            // Voice drives vibrant upward billowing expansion
            float voiceRise = boost * 0.16;

            // Macro billows and fine micro-movement driven by sound
            float macroWave = (field - 0.5) * 0.26;
            float fineMovement = (detail - 0.5) * (0.07 + boost * 0.12)
                               + (fineTurbulence - 0.5) * (boost * 0.09)
                               + (microCurl - 0.5) * (boost * 0.05);

            float cloudCeiling = baseEquator + voiceRise + macroWave + fineMovement;

            // Density fills the entire bottom half from below and billows softly at the ceiling
            // Below cloudCeiling, density is 1.0 (thick, rich cloud occupying the lower half)
            float density = 1.0 - smoothstep(cloudCeiling - 0.18, cloudCeiling + 0.05, uv.y);

            // Add internal volumetric billowing density variations inside the cloud mass
            float internalBillows = 0.88 + 0.12 * field + (detail - 0.5) * 0.08 + (fineTurbulence - 0.5) * (0.04 + boost * 0.06);
            float cloudAlpha = clamp(density * internalBillows * 1.06, 0.0, 1.0);

            // Rich sky background gradient in the upper half:
            // Top: vibrant royal/azure blue (#2472F5)
            // Mid-sky: soft azure blue (#60A5FA)
            vec3 skyTop = vec3(0.14, 0.45, 0.96);
            vec3 skyMid = vec3(0.42, 0.68, 0.98);
            vec3 skyColor = mix(skyMid, skyTop, smoothstep(0.35, 1.0, uv.y));

            // Pure, bright white cloud billows with soft volumetric lighting
            vec3 cloudHighlight = vec3(1.0, 1.0, 1.0);
            vec3 cloudBody = vec3(0.97, 0.985, 1.0);
            // Subtle ambient shadow in crevices
            vec3 cloudShadow = vec3(0.86, 0.92, 0.98);

            float lightGradient = smoothstep(0.4, 0.9, cloudAlpha) * (0.8 + 0.2 * detail);
            vec3 cloudColor = mix(
                mix(cloudShadow, cloudBody, smoothstep(0.2, 0.6, internalBillows)),
                cloudHighlight,
                lightGradient
            );

            // Soft atmospheric blending between cloud and sky
            vec3 finalColor = mix(skyColor, cloudColor, cloudAlpha);

            // Razor-sharp circular frame mask with sub-pixel anti-aliasing
            vec2 center = vec2(aspect * 0.5, 0.5);
            float dist = length(p - center);
            float pixelSize = 1.25 / max(resolution.x, resolution.y);
            float orbAlpha = 1.0 - smoothstep(0.499 - pixelSize, 0.500, dist);

            if (orbAlpha <= 0.0) {
                discard;
            }

            // Premultiplied alpha output
            gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0) * orbAlpha, orbAlpha);
        }
    """

    fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type $type")
            return 0
        }
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Compilation error in shader type $type: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Could not create OpenGL program")
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link error: $log")
            GLES20.glDeleteProgram(program)
            return 0
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }
}
