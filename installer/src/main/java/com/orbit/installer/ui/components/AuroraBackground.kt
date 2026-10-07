package com.orbit.installer.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * 现代科技感极光弥散渐变背景 (Dynamic Aurora & Glow Mesh)
 * 赋予车机安装助手生命力与现代科技视觉，告别单调死黑
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val baseBgColor = Color(0xFF0A0D14)
    val color1 = Color(0xFF1E3A8A).copy(alpha = 0.45f).toArgb() // 科技深蓝
    val color2 = Color(0xFF581C87).copy(alpha = 0.35f).toArgb() // 星云暗紫
    val color3 = Color(0xFF064E3B).copy(alpha = 0.30f).toArgb() // 电光青绿
    val transparentColor = Color.Transparent.toArgb()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            drawRect(baseBgColor)

            drawIntoCanvas { canvas ->
                val nativeCanvas: Canvas = canvas.nativeCanvas
                val paint = Paint().apply {
                    isAntiAlias = true
                    isDither = true
                }

                // 光球 1：顶部左侧动态漂移 (蓝色)
                val x1 = width * (0.25f + 0.15f * sin(time))
                val y1 = height * (0.20f + 0.10f * cos(time))
                val r1 = (width.coerceAtLeast(height)) * 0.55f
                paint.shader = RadialGradient(
                    x1, y1, r1,
                    intArrayOf(color1, transparentColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                nativeCanvas.drawCircle(x1, y1, r1, paint)

                // 光球 2：右侧居中动态漂移 (紫色)
                val x2 = width * (0.75f + 0.15f * cos(time * 0.8f))
                val y2 = height * (0.45f + 0.15f * sin(time * 0.8f))
                val r2 = (width.coerceAtLeast(height)) * 0.60f
                paint.shader = RadialGradient(
                    x2, y2, r2,
                    intArrayOf(color2, transparentColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                nativeCanvas.drawCircle(x2, y2, r2, paint)

                // 光球 3：底部左侧微光 (青绿色)
                val x3 = width * (0.30f + 0.20f * sin(time * 1.2f))
                val y3 = height * (0.80f + 0.10f * cos(time * 1.2f))
                val r3 = (width.coerceAtLeast(height)) * 0.50f
                paint.shader = RadialGradient(
                    x3, y3, r3,
                    intArrayOf(color3, transparentColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                nativeCanvas.drawCircle(x3, y3, r3, paint)
            }
        }

        // 内容承载层
        content()
    }
}
