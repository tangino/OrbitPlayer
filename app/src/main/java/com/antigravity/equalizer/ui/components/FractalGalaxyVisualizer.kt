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
 * 3D 视差滚动分形星系 (Fractal Galaxy) 动态可视化 Jetpack Compose 组件
 * 承载双层 Kaliset 视差分形星云着色器并与 4 频段音频频谱实时联动
 */
@Composable
fun FractalGalaxyVisualizer(
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
    val renderer = remember { FractalGalaxyGLRenderer() }

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
                    Color(0xFFFF9500)
                }
                val c2 = if (backgroundDarkColor != null && backgroundDarkColor != 0L) {
                    Color(backgroundDarkColor)
                } else {
                    Color(0xFF8A2BE2)
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

    // 实时从 magnitudes 提取 4 频段能量并同步给渲染器
    LaunchedEffect(magnitudes) {
        if (magnitudes.isEmpty()) {
            renderer.freq0Target = 0.12f
            renderer.freq1Target = 0.12f
            renderer.freq2Target = 0.12f
            renderer.freq3Target = 0.12f
            return@LaunchedEffect
        }

        val size = magnitudes.size
        
        // 1. 频段 0 (Sub-Bass / 重低音，前 1/16 频段)
        val end0 = (size / 16).coerceAtLeast(1)
        var sum0 = 0f
        for (i in 0 until end0) sum0 += magnitudes[i]
        renderer.freq0Target = (sum0 / end0).coerceIn(0.1f, 1.6f)

        // 2. 频段 1 (Low-Mid / 中低音，1/16 ~ 1/8)
        val end1 = (size / 8).coerceAtLeast(end0 + 1)
        var sum1 = 0f
        var count1 = 0
        for (i in end0 until end1.coerceAtMost(size)) {
            sum1 += magnitudes[i]
            count1++
        }
        renderer.freq1Target = if (count1 > 0) (sum1 / count1).coerceIn(0.1f, 1.6f) else 0.1f

        // 3. 频段 2 (Mid-High / 中高音，1/8 ~ 1/4)
        val end2 = (size / 4).coerceAtLeast(end1 + 1)
        var sum2 = 0f
        var count2 = 0
        for (i in end1 until end2.coerceAtMost(size)) {
            sum2 += magnitudes[i]
            count2++
        }
        renderer.freq2Target = if (count2 > 0) (sum2 / count2).coerceIn(0.1f, 1.6f) else 0.1f

        // 4. 频段 3 (Treble / 高音，1/4 ~ 1/2)
        val end3 = (size / 2).coerceAtLeast(end2 + 1)
        var sum3 = 0f
        var count3 = 0
        for (i in end2 until end3.coerceAtMost(size)) {
            sum3 += magnitudes[i]
            count3++
        }
        renderer.freq3Target = if (count3 > 0) (sum3 / count3).coerceIn(0.1f, 1.6f) else 0.1f
    }

    // 生命周期管理：进入后台时 pause，恢复时 resume
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
                            // 移动端 3D Raymarching 黄金自适应渲染分辨率缓冲 (短边 400px，全屏 60FPS 满帧流畅)
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
