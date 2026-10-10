package com.orbit.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.orbit.music.ui.theme.OrbitTheme

/**
 * 纯净动态声学频谱视窗 (Pure Spectrum Visualizer Bar)
 * 移除了悬浮峰 (Peak Caps) 与中间线条，纯粹展示灵动跳动的声学频谱柱
 */
@Composable
fun PowerampVisualizerBar(
    frequencies: FloatArray,
    gainsDb: FloatArray,
    spectrumBars: FloatArray,
    spectrumPeaks: FloatArray = FloatArray(0),
    visualizerEnabled: Boolean,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
) {
    val colors = OrbitTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(0.8.dp, colors.surfaceBorder, RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 动态跳动的纯净声学频谱柱 (自底向上升起，平滑圆角渐变)
            if (visualizerEnabled && spectrumBars.isNotEmpty()) {
                val barCount = minOf(spectrumBars.size, 32)
                val step = width / barCount.toFloat()
                val barWidth = (step * 0.72f).coerceAtLeast(2.dp.toPx())

                for (i in 0 until barCount) {
                    val rawMag = spectrumBars[i].coerceIn(0f, 1f)
                    val x = i * step + (step - barWidth) * 0.5f

                    // 绘制主题色渐变声学柱体
                    if (rawMag > 0.015f) {
                        val barHeight = (rawMag * height * 0.88f).coerceAtLeast(2.dp.toPx())
                        val topY = height - barHeight

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = 0.88f),
                                    colors.primary.copy(alpha = 0.25f)
                                ),
                                startY = topY,
                                endY = height
                            ),
                            topLeft = Offset(x, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

/**
 * 兼容参数均衡器等界面的频响曲线画布组件
 */
@Composable
fun FrequencyCurveCanvas(
    frequencies: FloatArray,
    gainsDb: FloatArray,
    isEnabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)
) {
    PowerampVisualizerBar(
        frequencies = frequencies,
        gainsDb = gainsDb,
        spectrumBars = floatArrayOf(),
        spectrumPeaks = floatArrayOf(),
        visualizerEnabled = isEnabled,
        modifier = modifier
    )
}
