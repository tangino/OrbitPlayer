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
 * 基于 Inigo Quilez 经典名作移植的 3D 音乐都市 (Sound City) 动态可视化渲染器
 * 核心技术：体素网格步进 (2D Grid Traversal)、圆角盒子距离场 (udBox)、真实物理光照与软阴影、4 频段音频驱动摩天大楼高度起伏
 */
class SoundCityGLRenderer : GLSurfaceView.Renderer {

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
    private var freq0 = 0.0f
    private var freq1 = 0.0f
    private var freq2 = 0.0f
    private var freq3 = 0.0f

    // 配色方案（RGB）
    @Volatile var color1R: Float = 0.0f
    @Volatile var color1G: Float = 0.85f
    @Volatile var color1B: Float = 1.0f

    @Volatile var color2R: Float = 0.95f
    @Volatile var color2G: Float = 0.35f
    @Volatile var color2B: Float = 0.15f

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

        // 4 频段音频能量物理阻尼平滑 (Attack 迅捷，Decay 平滑)
        val attack = 0.40f
        freq0 += (freq0Target - freq0) * attack
        freq1 += (freq1Target - freq1) * attack
        freq2 += (freq2Target - freq2) * attack
        freq3 += (freq3Target - freq3) * attack

        // 播放状态下以标准时间推进，暂停状态下以微速漫游
        val speedMultiplier = if (isPlaying) 1.0f else 0.18f
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

            float hash(float n) { 
                return fract(sin(n) * 13.5453123); 
            }

            float maxcomp(vec3 v) { 
                return max(max(v.x, v.y), v.z); 
            }

            // 圆角立方体距离场 (Inigo Quilez)
            float udBox(vec3 p, vec3 b, float r) {
                return length(max(abs(p) - b, 0.0)) - r;
            }

            // 程序化现代摩天楼夜景窗户贴图
            vec3 getBuildingTexture(vec3 p, vec3 n) {
                vec3 a = abs(n);
                vec2 uv = (a.x > 0.5) ? p.zy : ((a.y > 0.5) ? p.xz : p.xy);
                vec2 grid = fract(uv * vec2(8.0, 14.0));
                float windowBorder = step(0.18, grid.x) * step(0.22, grid.y);
                float noise = fract(sin(dot(floor(uv * vec2(8.0, 14.0)), vec2(12.9898, 78.233))) * 43758.5453);
                float lit = (noise > 0.42) ? 1.0 : 0.25;
                return vec3(windowBorder * lit * 0.9 + 0.15);
            }

            // 4 频段音频驱动大厦高度映射
            vec3 mapH(vec2 pos) {
                vec2 ipos = floor(pos);
                float id = hash(ipos.x + ipos.y * 57.0);
                
                float f = 0.0;
                f += uFreqs.x * clamp(1.0 - abs(id - 0.20) / 0.30, 0.0, 1.0);
                f += uFreqs.y * clamp(1.0 - abs(id - 0.40) / 0.30, 0.0, 1.0);
                f += uFreqs.z * clamp(1.0 - abs(id - 0.60) / 0.30, 0.0, 1.0);
                f += uFreqs.w * clamp(1.0 - abs(id - 0.80) / 0.30, 0.0, 1.0);

                f = pow(clamp(f * 1.5, 0.0, 1.5), 1.8);
                float h = 2.8 * f + 0.15;

                return vec3(h, id, f);
            }

            vec3 map(vec3 pos) {
                vec2 p = fract(pos.xz);
                vec3 m = mapH(pos.xz);
                float d = udBox(vec3(p.x - 0.5, pos.y - 0.5 * m.x, p.y - 0.5), vec3(0.3, m.x * 0.5, 0.3), 0.08);
                return vec3(d, m.yz);
            }

            const float surface = 0.0015;

            // 体素规整网格穿透遍历 (2D Grid Traversal)
            vec3 trace(vec3 ro, vec3 rd, float tmin, float tmax) {
                ro += tmin * rd;
                vec2 pos = floor(ro.xz);
                vec3 rdi = 1.0 / rd;
                vec3 rda = abs(rdi);
                vec2 rds = sign(rd.xz);
                vec2 dis = (pos - ro.xz + 0.5 + rds * 0.5) * rdi.xz;
                
                vec3 res = vec3(-1.0);
                vec2 mm = vec2(0.0);
                
                // 移动端优化步进
                for (int i = 0; i < 22; i++) {
                    vec3 cub = mapH(pos);

                    vec2 pr = pos + 0.5 - ro.xz;
                    vec2 mini = (pr - 0.5 * rds) * rdi.xz;
                    float s = max(mini.x, mini.y);
                    if ((tmin + s) > tmax) break;
                    
                    vec3 ce = vec3(pos.x + 0.5, 0.5 * cub.x, pos.y + 0.5);
                    vec3 rb = vec3(0.3, cub.x * 0.5, 0.3);
                    vec3 ra = rb + 0.12;
                    vec3 rc = ro - ce;
                    float tN = maxcomp(-rdi * rc - rda * ra);
                    float tF = maxcomp(-rdi * rc + rda * ra);
                    
                    if (tN < tF) {
                        float st = tN;
                        float h = 1.0;
                        for (int j = 0; j < 16; j++) {
                            h = udBox(rc + st * rd, rb, 0.08);
                            st += h;
                            if (st > tF) break;
                        }

                        if (h < (surface * st * 2.5)) {
                            res = vec3(st, cub.yz);
                            break;
                        }
                    }

                    mm = step(dis.xy, dis.yx);
                    dis += mm * rda.xz;
                    pos += mm * rds;
                }

                res.x += tmin;
                return res;
            }

            float usmoothstep(float x) {
                x = clamp(x, 0.0, 1.0);
                return x * x * (3.0 - 2.0 * x);
            }

            // 柔和建筑阴影
            float softshadow(vec3 ro, vec3 rd, float mint, float maxt, float k) {
                float res = 1.0;
                float t = mint;
                for (int i = 0; i < 20; i++) {
                    float h = map(ro + rd * t).x;
                    res = min(res, usmoothstep(k * h / t));
                    t += clamp(h, 0.06, 0.25);
                    if (res < 0.005 || t > maxt) break;
                }
                return clamp(res, 0.0, 1.0);
            }

            // 法线快速逼近
            vec3 calcNormal(vec3 pos, float t) {
                vec2 e = vec2(1.0, -1.0) * surface * t;
                return normalize(e.xyy * map(pos + e.xyy).x + 
                                 e.yyx * map(pos + e.yyx).x + 
                                 e.yxy * map(pos + e.yxy).x + 
                                 e.xxx * map(pos + e.xxx).x);
            }

            const vec3 light1 = vec3(0.70, 0.52, -0.45);
            const vec3 light2 = vec3(-0.71, 0.0, 0.71);
            const vec3 lpos = vec3(0.0) + 6.0 * light1;

            vec2 boundingVolume(vec2 tminmax, vec3 ro, vec3 rd) {
                float bp = 3.2;
                float tp = (bp - ro.y) / rd.y;
                if (tp > 0.0) {
                    if (ro.y > bp) tminmax.x = max(tminmax.x, tp);
                    else          tminmax.y = min(tminmax.y, tp);
                }
                bp = 0.0;
                tp = (bp - ro.y) / rd.y;
                if (tp > 0.0) {
                    if (ro.y > bp) tminmax.y = min(tminmax.y, tp);
                }
                return tminmax;
            }

            // 经典三向物理光照模型 (Inigo Quilez)
            vec3 doLighting(vec3 col, float ks, vec3 pos, vec3 nor, vec3 rd) {
                vec3 ldif = lpos - pos;
                float llen = length(ldif);
                ldif /= llen;
                float con = dot(light1, ldif);
                float occ = mix(clamp(pos.y / 4.0, 0.0, 1.0), 1.0, 0.2 * max(0.0, nor.y));
                
                float sha = softshadow(pos, ldif, 0.01, 5.0, 24.0);
                
                float bb = smoothstep(0.5, 0.8, con);
                float lkey = clamp(dot(nor, ldif), 0.0, 1.0);
                vec3 lkat = vec3(1.0);
                lkat *= vec3(bb * bb * 0.6 + 0.4 * bb, bb * 0.5 + 0.5 * bb * bb, bb).zyx;
                lkat /= (1.0 + 0.25 * llen * llen);
                lkat *= 30.0;
                lkat *= vec3(sha, 0.6 * sha + 0.4 * sha * sha, 0.3 * sha + 0.7 * sha * sha);
                
                float lbac = clamp(0.5 + 0.5 * dot(light2, nor), 0.0, 1.0);
                lbac *= smoothstep(0.0, 0.8, con);
                lbac /= (1.0 + 0.2 * llen * llen);
                lbac *= 7.0;
                
                float lamb = 1.0 - 0.5 * nor.y;
                lamb *= 1.0 - smoothstep(10.0, 25.0, length(pos.xz));
                lamb *= 0.25 + 0.75 * smoothstep(0.0, 0.8, con);
                lamb *= 0.25;

                vec3 lin = vec3(1.60, 0.70, 0.30) * lkey * lkat * (0.5 + 0.5 * occ);
                lin += vec3(0.20, 0.05, 0.02) * lamb * occ * occ;
                lin += vec3(0.70, 0.20, 0.08) * lbac * occ * occ;
                lin *= vec3(1.3, 1.1, 1.0);
                
                col = col * lin;

                vec3 hal = normalize(ldif - rd);
                vec3 spe = lkey * lkat * (0.5 + 0.5 * occ) * 4.0 *
                           pow(clamp(dot(hal, nor), 0.0, 1.0), 6.0 + 6.0 * ks) * 
                           (0.04 + 0.96 * pow(clamp(1.0 - dot(hal, ldif), 0.0, 1.0), 5.0));

                col += (0.4 + 0.6 * ks) * spe * vec3(0.8, 0.9, 1.0);
                col = 1.4 * col / (1.0 + col);
                return col;
            }

            mat3 setLookAt(vec3 ro, vec3 ta, float cr) {
                vec3 cw = normalize(ta - ro);
                vec3 cp = vec3(sin(cr), cos(cr), 0.0);
                vec3 cu = normalize(cross(cw, cp));
                vec3 cv = normalize(cross(cu, cw));
                return mat3(cu, cv, cw);
            }

            vec3 render(vec3 ro, vec3 rd) {
                vec3 col = vec3(0.02, 0.03, 0.06); // 深邃夜空底色
                vec2 tminmax = vec2(0.0, 36.0);
                tminmax = boundingVolume(tminmax, ro, rd);

                vec3 res = trace(ro, rd, tminmax.x, tminmax.y);
                if (res.y > -0.5) {
                    float t = res.x;
                    vec3 pos = ro + t * rd;
                    vec3 nor = calcNormal(pos, t);

                    // 建筑色彩：融合当前播放主题色与频段 ID 色相
                    vec3 baseColor = 0.5 + 0.5 * cos(6.2831 * res.y + vec3(0.0, 0.4, 0.8));
                    vec3 themeBlend = mix(uColor1, uColor2, fract(res.y * 3.0));
                    col = mix(baseColor, themeBlend, 0.45);

                    // 建筑格子窗户夜景纹理
                    vec3 tex = getBuildingTexture(0.21 * vec3(pos.x, 4.0 * res.z - pos.y, pos.z), nor);
                    tex = pow(tex, vec3(1.3)) * 1.1;
                    col *= tex.x;

                    // 真实物理光照
                    col = doLighting(col, tex.x * tex.x * 2.0, pos, nor, rd);
                    col *= 1.0 - smoothstep(18.0, 36.0, t);
                }
                return col;
            }

            void main() {
                vec2 p = (-uResolution.xy + 2.0 * gl_FragCoord.xy) / uResolution.y;
                float time = 5.0 + 0.22 * uTime;

                // 3D 摄像机全景环绕穿梭轨迹 (Inigo Quilez)
                vec3 ro = vec3(8.5 * cos(0.2 + 0.33 * time), 5.0 + 2.0 * cos(0.1 * time), 8.5 * sin(0.1 + 0.37 * time));
                vec3 ta = vec3(-2.5 + 3.0 * cos(1.2 + 0.41 * time), 0.2, 2.0 + 3.0 * sin(2.0 + 0.38 * time));
                float roll = 0.2 * sin(0.1 * time);

                mat3 ca = setLookAt(ro, ta, roll);
                vec3 rd = normalize(ca * vec3(p, 1.75));

                vec3 col = render(ro, rd);
                
                // 经典电影胶片伽马与色彩平衡调整
                col = pow(col, vec3(0.4545));
                col = pow(col, vec3(0.85, 0.94, 1.0));

                // 边缘晕影 (Vignette)
                vec2 q = gl_FragCoord.xy / uResolution.xy;
                col *= 0.25 + 0.75 * pow(16.0 * q.x * q.y * (1.0 - q.x) * (1.0 - q.y), 0.12);

                gl_FragColor = vec4(col, 1.0);
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
