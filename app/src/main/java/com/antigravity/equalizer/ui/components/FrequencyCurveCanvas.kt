package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.antigravity.equalizer.ui.theme.OrbitTheme

@Composable
fun FrequencyCurveCanvas(
    frequencies: FloatArray,
    gainsDb: FloatArray,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val maxGain = 12f // +-12dB
        val minGain = -12f

        // 1. 绘制网格参考线 (-12dB, -6dB, 0dB, +6dB, +12dB)
        val dbSteps = listOf(12f, 6f, 0f, -6f, -12f)
        dbSteps.forEach { db ->
            val y = midY - (db / maxGain) * (height * 0.42f)
            drawLine(
                color = if (db == 0f) colors.gridLine.copy(alpha = 0.6f) else colors.gridLine,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = if (db == 0f) 1.5f else 1.0f
            )
        }

        if (frequencies.isEmpty() || gainsDb.isEmpty()) return@Canvas

        val count = minOf(frequencies.size, gainsDb.size)
        val points = mutableListOf<Offset>()

        // 2. 映射频点坐标到画布 (对数坐标 X, 线性增益 Y)
        val minFreq = 20f
        val maxFreq = 20000f
        val logMin = kotlin.math.log10(minFreq)
        val logMax = kotlin.math.log10(maxFreq)

        for (i in 0 until count) {
            val freq = frequencies[i].coerceIn(minFreq, maxFreq)
            val gain = if (isEnabled) gainsDb[i].coerceIn(minGain, maxGain) else 0f

            val logFreq = kotlin.math.log10(freq)
            val normalizedX = (logFreq - logMin) / (logMax - logMin)
            val x = normalizedX * (width - 40f) + 20f
            val y = midY - (gain / maxGain) * (height * 0.42f)

            points.add(Offset(x, y))
        }

        // 3. 构建平滑贝塞尔曲线
        val curvePath = Path()
        val fillPath = Path()

        if (points.isNotEmpty()) {
            curvePath.moveTo(0f, if (isEnabled) points.first().y else midY)
            curvePath.lineTo(points.first().x, points.first().y)

            fillPath.moveTo(0f, height)
            fillPath.lineTo(0f, if (isEnabled) points.first().y else midY)
            fillPath.lineTo(points.first().x, points.first().y)

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX1 = (p0.x + p1.x) / 2f
                val controlY1 = p0.y
                val controlX2 = (p0.x + p1.x) / 2f
                val controlY2 = p1.y

                curvePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
            }

            curvePath.lineTo(width, if (isEnabled) points.last().y else midY)
            fillPath.lineTo(width, if (isEnabled) points.last().y else midY)
            fillPath.lineTo(width, height)
            fillPath.close()

            // 4. 填充渐变发光区域
            val activeCyan = if (isEnabled) colors.primary else colors.textSecondary.copy(alpha = 0.4f)
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        activeCyan.copy(alpha = 0.25f),
                        activeCyan.copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // 5. 绘制主曲线轮廓
            drawPath(
                path = curvePath,
                color = activeCyan,
                style = Stroke(width = 3.dp.toPx())
            )

            // 6. 绘制各频点锚点圆圈
            points.forEach { pt ->
                drawCircle(
                    color = activeCyan,
                    radius = 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = colors.surfaceCard,
                    radius = 2.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
