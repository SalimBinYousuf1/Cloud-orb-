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
     * Fragment shader faithfully matching the reference video:
     * - Top: vibrant deep royal/azure blue (#2472F5)
     * - Middle: thick, billowing, volumetric white clouds with organic wispy cumulus crests
     * - Bottom: soft pastel sky blue (#98BFF8)
     * - Silky smooth, continuous fluid mixing with zero stutter, snap, or hang
     */
    const val FRAGMENT_SHADER_SOURCE = """
        precision highp float;
        uniform vec2 resolution;
        uniform float time;
        uniform float audioBoost;

        // Rotation matrix to remove grid bias and create realistic fluid vortices
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

            // Coordinated fluid flow: horizontal drift combined with gentle vertical vapor rise
            vec2 flow = vec2(t * 0.075, -t * 0.022);

            // Double domain warping to produce natural smoke/cloud fluid curls
            vec2 q = vec2(
                fbm(p * 1.75 + flow),
                fbm(p * 1.75 + flow + vec2(5.2, 1.3))
            );

            // Secondary vortex swirls reacting smoothly to voice
            vec2 swirl = vec2(
                sin(p.y * 2.5 + t * 0.3) * 0.06,
                cos(p.x * 2.5 - t * 0.25) * 0.06
            ) * (1.0 + boost * 0.8);

            vec2 r = vec2(
                fbm(p * 2.1 + 1.25 * q + swirl + vec2(t * 0.045, -t * 0.035)),
                fbm(p * 2.1 + 1.25 * q - swirl + vec2(8.3, 2.8))
            );

            vec2 warpedP = p + (r - 0.5) * (0.85 + boost * 0.22);

            // Multi-scale cloud density field
            float field = fbm(warpedP * 1.55 + vec2(t * 0.03, -t * 0.02));
            float detail = noise(warpedP * 4.6 + vec2(t * 0.07, -t * 0.05));

            // Vertical envelope precisely matching the video:
            // Top cloud crests billow across y = 0.45..0.72 into deep blue sky
            float topThreshold = 0.52 + 0.23 * field + 0.07 * (detail - 0.5) + boost * 0.06;
            float topMask = 1.0 - smoothstep(topThreshold - 0.14, topThreshold + 0.06, uv.y);

            // Bottom cloud edge softly dissolves into light blue sky
            float bottomThreshold = 0.16 + 0.10 * (1.0 - field);
            float bottomMask = smoothstep(bottomThreshold - 0.08, bottomThreshold + 0.18, uv.y);

            // Combined volumetric cloud opacity
            float cloudBand = topMask * bottomMask;
            float density = smoothstep(0.08, 0.85, cloudBand * (field * 0.72 + detail * 0.28 + 0.22));
            float cloudAlpha = clamp(density * 1.08, 0.0, 1.0);

            // Exact sky gradient matching reference video:
            // Top: vibrant rich royal blue (#2472F5)
            // Bottom: soft pastel sky blue (#98BFF8)
            vec3 skyTop = vec3(0.14, 0.45, 0.96);
            vec3 skyBottom = vec3(0.60, 0.76, 0.98);
            vec3 skyColor = mix(skyBottom, skyTop, pow(uv.y, 0.90));

            // Pure, bright cloud whites with subtle soft volumetric body
            vec3 cloudHighlight = vec3(1.0, 1.0, 1.0);
            vec3 cloudBody = vec3(0.97, 0.985, 1.0);
            vec3 cloudColor = mix(cloudBody, cloudHighlight, smoothstep(0.45, 0.90, cloudAlpha));

            // Soft atmospheric blending of cloud into the sky
            vec3 finalColor = mix(skyColor, cloudColor, cloudAlpha * 0.985);

            // Razor-sharp anti-aliased circular mask
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
