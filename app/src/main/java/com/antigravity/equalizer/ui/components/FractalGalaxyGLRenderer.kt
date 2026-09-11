package com.antigravity.equalizer.ui.components

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * 3D 视差滚动分形星系 (Fractal Galaxy) 动态可视化渲染器
 * 核心技术：双层视差宇宙分形星云 (Kaliset 分形场)、多频段音频能量驱动星云涌动、程序化闪烁繁星星尘
 */
class FractalGalaxyGLRenderer : GLSurfaceView.Renderer {

    // 全屏四边形顶点 (两个三角形，NDC 坐标系)
    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadVertices)
            position(0)
        }

    private var programId = 0
    private var aPositionHandle = -1
    private var uResolutionHandle = -1
    private var uTimeHandle = -1
    private var uFreqsHandle = -1
    private var uColor1Handle = -1
    private var uColor2Handle = -1

    // 视口宽高
    private var width = 1f
    private var height = 1f

    // 时间戳与推进
    private var lastFrameTime = 0L
    private var internalTime = 0.0f

    // 外部传入状态（支持多线程并发安全更新）
    @Volatile var isPlaying: Boolean = true
    @Volatile var freq0Target: Float = 0.0f
    @Volatile var freq1Target: Float = 0.0f
    @Volatile var freq2Target: Float = 0.0f
    @Volatile var freq3Target: Float = 0.0f

    // 平滑后的 4 频段能量
    private var freq0 = 0.2f
    private var freq1 = 0.2f
    private var freq2 = 0.2f
    private var freq3 = 0.2f

    // 配色方案（RGB）
    @Volatile var color1R: Float = 1.0f
    @Volatile var color1G: Float = 0.6f
    @Volatile var color1B: Float = 0.2f

    @Volatile var color2R: Float = 0.4f
    @Volatile var color2G: Float = 0.2f
    @Volatile var color2B: Float = 0.95f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        initShaders()
        lastFrameTime = SystemClock.uptimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width).toFloat()
        this.height = max(1, height).toFloat()
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (programId == 0) return

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameTime == 0L) 0.016f else ((now - lastFrameTime) / 1000.0f).coerceIn(0.001f, 0.1f)
        lastFrameTime = now

        // 音频能量平滑插值 (有最低保底 0.15f，保证在音乐安静时星系依然有呼吸微光)
        val attack = 0.38f
        freq0 += (max(0.12f, freq0Target) - freq0) * attack
        freq1 += (max(0.12f, freq1Target) - freq1) * attack
        freq2 += (max(0.12f, freq2Target) - freq2) * attack
        freq3 += (max(0.12f, freq3Target) - freq3) * attack

        // 播放状态下速度由低频能量加速流转，暂停状态下以微速漂移
        val speedMultiplier = if (isPlaying) (1.0f + freq0 * 0.8f) else 0.2f
        internalTime += dt * speedMultiplier

        GLES20.glUseProgram(programId)

        // 绑定顶点数据
        if (aPositionHandle >= 0) {
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        }

        // 传递 Uniform 变量
        if (uResolutionHandle >= 0) GLES20.glUniform2f(uResolutionHandle, width, height)
        if (uTimeHandle >= 0) GLES20.glUniform1f(uTimeHandle, internalTime)
        if (uFreqsHandle >= 0) GLES20.glUniform4f(uFreqsHandle, freq0, freq1, freq2, freq3)
        if (uColor1Handle >= 0) GLES20.glUniform3f(uColor1Handle, color1R, color1G, color1B)
        if (uColor2Handle >= 0) GLES20.glUniform3f(uColor2Handle, color2R, color2G, color2B)

        // 绘制全屏四边形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (aPositionHandle >= 0) {
            GLES20.glDisableVertexAttribArray(aPositionHandle)
        }
    }

    private fun initShaders() {
        val vertexShaderSource = """
            attribute vec4 aPosition;
            void main() {
                gl_Position = aPosition;
            }
        """.trimIndent()

        val fragmentShaderSource = """
            precision mediump float;
            uniform vec2 uResolution;
            uniform float uTime;
            uniform vec4 uFreqs;
            uniform vec3 uColor1;
            uniform vec3 uColor2;

            // 第一层：核心主星云分形场 (Kaliset 反演分形)
            float field(vec3 p, float s) {
                float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(uTime) * 4373.11));
                float accum = s * 0.25;
                float prev = 0.0;
                float tw = 0.0;
                for (int i = 0; i < 24; ++i) {
                    float mag = dot(p, p);
                    p = abs(p) / mag + vec3(-0.5, -0.4, -1.5);
                    float w = exp(-float(i) * 0.142857);
                    accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
                    tw += w;
                    prev = mag;
                }
                return max(0.0, 5.0 * accum / tw - 0.7);
            }

            // 第二层：深远宇宙背景视差分形场
            float field2(vec3 p, float s) {
                float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(uTime) * 4373.11));
                float accum = s * 0.25;
                float prev = 0.0;
                float tw = 0.0;
                for (int i = 0; i < 16; ++i) {
                    float mag = dot(p, p);
                    p = abs(p) / mag + vec3(-0.5, -0.4, -1.5);
                    float w = exp(-float(i) * 0.142857);
                    accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
                    tw += w;
                    prev = mag;
                }
                return max(0.0, 5.0 * accum / tw - 0.7);
            }

            // 伪随机星光分布算法
            vec3 nrand3(vec2 co) {
                vec3 a = fract(cos(co.x * 8.3e-3 + co.y) * vec3(1.3e5, 4.7e5, 2.9e5));
                vec3 b = fract(sin(co.x * 0.3e-3 + co.y) * vec3(8.1e5, 1.0e5, 0.1e5));
                return mix(a, b, 0.5);
            }

            void main() {
                vec2 uv = 2.0 * gl_FragCoord.xy / uResolution.xy - 1.0;
                vec2 uvs = uv * uResolution.xy / max(uResolution.x, uResolution.y);
                
                // 第一层星云：平滑漫游漂浮
                vec3 p = vec3(uvs * 0.25, 0.0) + vec3(1.0, -1.3, 0.0);
                p += 0.2 * vec3(sin(uTime * 0.0625), sin(uTime * 0.0833), sin(uTime * 0.0078125));
                
                float t = field(p, uFreqs.z);
                float v = (1.0 - exp((abs(uv.x) - 1.0) * 6.0)) * (1.0 - exp((abs(uv.y) - 1.0) * 6.0));
                
                // 第二层星云：视差差速缩放与景深层级流动
                float zoom = 4.0 + sin(uTime * 0.11) * 0.2 + 0.2 + sin(uTime * 0.15) * 0.3 + 0.4;
                vec3 p2 = vec3(uvs / zoom, 1.5) + vec3(2.0, -1.3, -1.0);
                p2 += 0.25 * vec3(sin(uTime * 0.0625), sin(uTime * 0.0833), sin(uTime * 0.0078125));
                float t2 = field2(p2, uFreqs.w);
                
                vec4 c2 = mix(0.4, 1.0, v) * vec4(1.3 * t2 * t2 * t2, 1.8 * t2 * t2, t2 * uFreqs.x, t2);
                
                // 双层程序化闪烁宇宙繁星点缀
                vec2 seed = floor(p.xy * 2.0 * uResolution.x);
                vec3 rnd = nrand3(seed);
                vec4 starcolor = vec4(pow(rnd.y, 40.0));
                
                vec2 seed2 = floor(p2.xy * 2.0 * uResolution.x);
                vec3 rnd2 = nrand3(seed2);
                starcolor += vec4(pow(rnd2.y, 40.0));
                
                // 经典音频色彩驱动合成 (高音微尘，中频金橙，低音深紫)
                vec4 col1 = mix(uFreqs.w - 0.3, 1.0, v) * vec4(1.5 * uFreqs.z * t * t * t, 1.2 * uFreqs.y * t * t, uFreqs.w * t, 1.0);
                vec4 finalColor = col1 + c2 + starcolor;
                
                // 融合播放主题配色
                vec3 themeGrad = mix(uColor1, uColor2, clamp(t * 1.3, 0.0, 1.0));
                finalColor.rgb = mix(finalColor.rgb, finalColor.rgb * themeGrad * 1.4, 0.38);
                
                gl_FragColor = vec4(finalColor.rgb, 1.0);
            }
        """.trimIndent()

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

        if (vertexShader == 0 || fragmentShader == 0) return

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            return
        }

        programId = program
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        uResolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        uTimeHandle = GLES20.glGetUniformLocation(program, "uTime")
        uFreqsHandle = GLES20.glGetUniformLocation(program, "uFreqs")
        uColor1Handle = GLES20.glGetUniformLocation(program, "uColor1")
        uColor2Handle = GLES20.glGetUniformLocation(program, "uColor2")
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
    }
}
