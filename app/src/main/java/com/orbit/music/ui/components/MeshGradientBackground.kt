package com.orbit.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * 殿堂级多颜色混合弥散流光渐变背景 (Mesh Gradient Background)
 *
 * 核心技术与兼容性：
 * - 纯底层 2D Canvas 软衰减径向色阶渲染，完全兼容 Android 5.0 (API 21+)。
 * - 鲜明色斑光晕 (Vibrant Color Blobs) + 有机多相流体漂移 + 半径呼吸缩放。
 * - 确保 2~4 个颜色既有各自鲜明的色相区域，又能在交汇处产生极其细腻通透的极光过渡。
 */
@Composable
fun MeshGradientBackground(
    colors: List<Long>,
    modifier: Modifier = Modifier,
    isDynamic: Boolean = true,
    baseColor: Color = Color(0xFF0C0E14)
) {
    // 颜色约束在 2 ~ 4 种
    val safeColors = remember(colors) {
        val mapped = colors.map { Color(it) }
        when {
            mapped.isEmpty() -> listOf(Color(0xFF1E284A), Color(0xFF423328), Color(0xFF382D4A))
            mapped.size == 1 -> listOf(mapped[0], mapped[0].copy(alpha = 0.6f))
            mapped.size > 4 -> mapped.take(4)
            else -> mapped
        }
    }

    // 动态流光时间驱动（8秒无缝完美闭环大循环）
    val infiniteTransition = rememberInfiniteTransition(label = "MeshGradientTransition")
    val progress by if (isDynamic) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "MeshMotionProgress"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val count = safeColors.size
            val minDim = minOf(w, h)
            val maxDim = maxOf(w, h)

            safeColors.forEachIndexed { index, color ->
                val centerOffset = if (isDynamic) {
                    calculateMeshDynamicCenter(index, count, w, h, progress)
                } else {
                    calculateMeshStaticCenter(index, count, w, h)
                }

                // 半径优化：根据颜色数量自适应
                val baseRadius = when (count) {
                    2 -> maxDim * 0.72f
                    3 -> maxDim * 0.62f
                    else -> maxDim * 0.54f
                }

                // 动态呼吸缩放系数：使用严格整数谐波 (频率为 1 或 2)，确保 0 与 2*PI 处首尾完美无缝闭合
                val dynamicRadiusScale = if (isDynamic) {
                    val phaseOffset = index * 1.5707963f // PI/2 间隔初相
                    val harmonicFreq = if (index % 2 == 0) 1 else 2
                    1.0f + 0.14f * sin(progress * harmonicFreq + phaseOffset)
                } else {
                    1.0f
                }
                val radius = (baseRadius * dynamicRadiusScale).coerceAtLeast(minDim * 0.3f)

                // 核心区域色彩饱满，边缘软衰减
                val brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to color.copy(alpha = 0.92f),
                        0.25f to color.copy(alpha = 0.78f),
                        0.55f to color.copy(alpha = 0.38f),
                        0.82f to color.copy(alpha = 0.10f),
                        1.0f to Color.Transparent
                    ),
                    center = centerOffset,
                    radius = radius
                )

                drawCircle(
                    brush = brush,
                    radius = radius,
                    center = centerOffset,
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

/**
 * 静态锚点计算 (4种颜色分别锚定在4个象限核心)
 */
private fun calculateMeshStaticCenter(index: Int, total: Int, w: Float, h: Float): Offset {
    return when (total) {
        2 -> when (index) {
            0 -> Offset(w * 0.22f, h * 0.78f) // 左下
            else -> Offset(w * 0.78f, h * 0.22f) // 右上
        }
        3 -> when (index) {
            0 -> Offset(w * 0.20f, h * 0.80f) // 左下
            1 -> Offset(w * 0.80f, h * 0.20f) // 右上
            else -> Offset(w * 0.40f, h * 0.45f) // 中间偏左
        }
        else -> when (index) {
            0 -> Offset(w * 0.22f, h * 0.24f) // 1. 左上
            1 -> Offset(w * 0.78f, h * 0.22f) // 2. 右上
            2 -> Offset(w * 0.20f, h * 0.78f) // 3. 左下
            else -> Offset(w * 0.80f, h * 0.76f) // 4. 右下
        }
    }
}

/**
 * 动态运动轨迹计算
 * 关键数学约束：所有正余弦函数的角频率必须为严格正整数 (1 或 2)，
 * 保证 t 从 2*PI 跳回 0 时，sin/cos 的位置与导数完全一致，0 缝隙、0 卡顿跳跃。
 */
private fun calculateMeshDynamicCenter(index: Int, total: Int, w: Float, h: Float, t: Float): Offset {
    val base = calculateMeshStaticCenter(index, total, w, h)
    
    val motionX = w * 0.26f
    val motionY = h * 0.22f

    val dx = when (index) {
        0 -> cos(t * 1.0f) * motionX
        1 -> -sin(t * 1.0f + 1.047f) * motionX // 初相 PI/3
        2 -> sin(t * 2.0f + 2.094f) * motionX  // 2倍频闭环
        else -> -cos(t * 1.0f + 3.14159f) * motionX // 初相 PI
    }

    val dy = when (index) {
        0 -> sin(t * 1.0f) * motionY
        1 -> cos(t * 1.0f + 1.047f) * motionY
        2 -> -cos(t * 1.0f + 2.094f) * motionY
        else -> sin(t * 2.0f + 1.5708f) * motionY // 2倍频闭环
    }

    return Offset(
        x = (base.x + dx).coerceIn(w * 0.05f, w * 0.95f),
        y = (base.y + dy).coerceIn(h * 0.05f, h * 0.95f)
    )
}

