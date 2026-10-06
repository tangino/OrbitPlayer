package com.orbit.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 殿堂级多颜色混合弥散流光渐变背景 (Mesh Gradient Background)
 *
 * 核心技术与兼容性：
 * - 纯底层 2D Canvas 软衰减径向色阶渲染，完全兼容 Android 5.0 (API 21+)。
 * - 鲜明色斑光晕 (Vibrant Color Blobs) + 有机多相流体漂移 + 半径呼吸缩放。
 * - 18阶高密度余弦 S 曲线平滑插值 (Cosine S-Curve)，彻底消除马赫带 (Mach Banding) 同心硬环。
 * - 亚像素三角概率分布 (TPDF) 高频微抖动层 (Sub-pixel Dithering)，消除高分辨率屏幕 8-bit 色彩断层与栅格感。
 * - 硬件级 isDither 开启，确保 2K / 4K 车机与大屏色彩过渡如丝般顺滑。
 */
@Composable
fun MeshGradientBackground(
    colors: List<Long>,
    modifier: Modifier = Modifier,
    isDynamic: Boolean = true,
    baseColor: Color = Color(0xFF0C0E14)
) {
    // 颜色约束在 2 ~ 4 种
    val safeColorInts = remember(colors) {
        val mapped = colors.map { Color(it).toArgb() }
        when {
            mapped.isEmpty() -> listOf(
                Color(0xFF1E284A).toArgb(),
                Color(0xFF423328).toArgb(),
                Color(0xFF382D4A).toArgb()
            )
            mapped.size == 1 -> listOf(
                mapped[0],
                Color(mapped[0]).copy(alpha = 0.6f).toArgb()
            )
            mapped.size > 4 -> mapped.take(4)
            else -> mapped
        }
    }

    // 预计算 18 阶余弦平滑数组，避免每帧重复分配内存
    val smoothSpecs = remember(safeColorInts) {
        safeColorInts.map { colorInt ->
            createSmoothGradientSpec(colorInt, steps = 18)
        }
    }

    val blobPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isDither = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    val ditherPaint = remember {
        Paint().apply {
            isAntiAlias = false
            isDither = true
            isFilterBitmap = true
            shader = MeshDitherShaderHolder.shader
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

            val count = smoothSpecs.size
            val minDim = minOf(w, h)
            val maxDim = maxOf(w, h)

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas

                smoothSpecs.forEachIndexed { index, spec ->
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

                    blobPaint.shader = RadialGradient(
                        centerOffset.x,
                        centerOffset.y,
                        radius,
                        spec.colors,
                        spec.stops,
                        Shader.TileMode.CLAMP
                    )

                    nativeCanvas.drawCircle(centerOffset.x, centerOffset.y, radius, blobPaint)
                }

                // 叠加亚像素高频微抖动抗色带层，打破 2K/4K 屏幕上 8-bit 色阶量化阶梯与马赫带栅格
                nativeCanvas.drawRect(0f, 0f, w, h, ditherPaint)
            }
        }
    }
}

private class SmoothGradientSpec(
    val colors: IntArray,
    val stops: FloatArray
)

/**
 * 构造 18 阶余弦平滑衰减色阶 (Cosine S-Curve)
 * 使得衰减曲线首尾导数严格为 0，彻底消除 5 点线性插值产生的硬转折圆环与马赫带效应
 */
private fun createSmoothGradientSpec(baseColorInt: Int, steps: Int = 18): SmoothGradientSpec {
    val colors = IntArray(steps + 1)
    val stops = FloatArray(steps + 1)
    val a = android.graphics.Color.alpha(baseColorInt)
    val r = android.graphics.Color.red(baseColorInt)
    val g = android.graphics.Color.green(baseColorInt)
    val b = android.graphics.Color.blue(baseColorInt)

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        stops[i] = t
        // 余弦平滑 S 曲线: t从0到1，smoothFactor从1平滑降到0
        val smoothFactor = 0.5f * (1.0f + cos(PI.toFloat() * t))
        val alpha = (a * 0.92f * smoothFactor).toInt().coerceIn(0, 255)
        colors[i] = android.graphics.Color.argb(alpha, r, g, b)
    }
    return SmoothGradientSpec(colors, stops)
}

/**
 * 亚像素三角概率分布 (TPDF) 高频微抖动着色器单例
 * 在 64x64 空间内平铺循环，利用人眼空间低通滤波特性，彻底消除高分屏 8-bit 色阶量化栅格感。
 */
private object MeshDitherShaderHolder {
    val shader: BitmapShader by lazy {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = java.util.Random(1337)
        for (i in pixels.indices) {
            val r1 = random.nextFloat()
            val r2 = random.nextFloat()
            // TPDF 三角概率分布噪波 [-1.0, 1.0]，期望值为 0
            val delta = r1 - r2
            // 极低微透明度 (最大约 4%~5%)，保证人眼无法察觉噪点颗粒，仅打破色阶硬阶梯
            val alpha = (abs(delta) * 12).toInt().coerceIn(0, 12)
            pixels[i] = if (delta >= 0f) {
                (alpha shl 24) or 0x00FFFFFF // 亚像素微透白
            } else {
                (alpha shl 24) or 0x00000000 // 亚像素微透黑
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
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
