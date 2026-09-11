package com.antigravity.equalizer.ui.components

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antigravity.equalizer.data.model.VisualizerColorScheme

/**
 * 3D 八芒星阵 (Octgrams) 音频动态可视化 Jetpack Compose 组件
 * 承载 OpenGL ES 2.0 / 3.0 Raymarching 着色器并与音频频谱频段实时联动
 */
@Composable
fun OctgramsVisualizer(
    magnitudes: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    colorScheme: VisualizerColorScheme = VisualizerColorScheme.FOLLOW_BACKGROUND,
    customColor: Long = 0xFF00E5FFL,
    customColor2: Long = 0xFF7C4DFFL,
    backgroundLightColor: Long? = null,
    backgroundDarkColor: Long? = null,
    onClick: (() -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 创建并持久化渲染器
    val renderer = remember { OctgramsGLRenderer() }

    // 计算当前有效的主副配色
    val (color1, color2) = remember(
        colorScheme,
        customColor,
        customColor2,
        backgroundLightColor,
        backgroundDarkColor
    ) {
        when (colorScheme) {
            VisualizerColorScheme.FOLLOW_BACKGROUND -> {
                val c1 = if (backgroundLightColor != null && backgroundLightColor != 0L) {
                    Color(backgroundLightColor)
                } else {
                    Color(0xFF00E5FF)
                }
                val c2 = if (backgroundDarkColor != null && backgroundDarkColor != 0L) {
                    Color(backgroundDarkColor)
                } else {
                    Color(0xFFFF2A6D)
                }
                Pair(c1, c2)
            }
            VisualizerColorScheme.NEON_CYAN_PURPLE -> {
                Pair(Color(0xFF00F0FF), Color(0xFFB026FF))
            }
            VisualizerColorScheme.FIRE_AMBER -> {
                Pair(Color(0xFFFF9100), Color(0xFFFF1744))
            }
            VisualizerColorScheme.ELECTRIC_GREEN -> {
                Pair(Color(0xFF00E676), Color(0xFF00B0FF))
            }
            VisualizerColorScheme.CUSTOM -> {
                Pair(Color(customColor), Color(customColor2))
            }
        }
    }

    // 实时同步颜色与播放状态到渲染器
    LaunchedEffect(color1, color2, isPlaying) {
        renderer.color1R = color1.red
        renderer.color1G = color1.green
        renderer.color1B = color1.blue
        renderer.color2R = color2.red
        renderer.color2G = color2.green
        renderer.color2B = color2.blue
        renderer.isPlaying = isPlaying
    }

    // 实时从 magnitudes 提取能量并同步给渲染器
    LaunchedEffect(magnitudes) {
        if (magnitudes.isEmpty()) {
            renderer.targetBass = 0f
            renderer.targetMid = 0f
            renderer.targetTreble = 0f
            return@LaunchedEffect
        }

        val size = magnitudes.size
        // 1. 低频段 (Bass / Kick)
        val bassEnd = (size / 8).coerceAtLeast(1)
        var bassSum = 0f
        for (i in 0 until bassEnd) {
            bassSum += magnitudes[i]
        }
        val bass = (bassSum / bassEnd).coerceIn(0f, 1.5f)

        // 2. 中频段 (Mid / Vocal)
        val midEnd = (size / 2).coerceAtLeast(bassEnd + 1)
        var midSum = 0f
        var midCount = 0
        for (i in bassEnd until midEnd.coerceAtMost(size)) {
            midSum += magnitudes[i]
            midCount++
        }
        val mid = if (midCount > 0) (midSum / midCount).coerceIn(0f, 1.5f) else 0f

        // 3. 高频段 (Treble / Air)
        var trebleSum = 0f
        var trebleCount = 0
        for (i in midEnd until size) {
            trebleSum += magnitudes[i]
            trebleCount++
        }
        val treble = if (trebleCount > 0) (trebleSum / trebleCount).coerceIn(0f, 1.5f) else 0f

        renderer.targetBass = bass
        renderer.targetMid = mid
        renderer.targetTreble = treble
    }

    // 生命周期管理：切换到后台时 pause 渲染节省电量，恢复时 resume
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceViewRef?.onPause()
            renderer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        AndroidView(
            factory = { ctx: Context ->
                object : GLSurfaceView(ctx) {
                    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                        super.onSizeChanged(w, h, oldw, oldh)
                        if (w > 0 && h > 0) {
                            // 移动端 3D Raymarching 黄金自适应渲染分辨率缓冲
                            // 严格保持实际宽高比，硬件自动铺满全屏，消除 85% 片元开销，实现满帧 60FPS 丝滑流畅
                            val maxShortEdge = 400f
                            val shortEdge = kotlin.math.min(w, h).toFloat()
                            val scale = if (shortEdge > maxShortEdge) maxShortEdge / shortEdge else 1.0f
                            val targetW = kotlin.math.max(1, (w * scale).toInt())
                            val targetH = kotlin.math.max(1, (h * scale).toInt())
                            holder.setFixedSize(targetW, targetH)
                        }
                    }
                }.apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    glSurfaceViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
