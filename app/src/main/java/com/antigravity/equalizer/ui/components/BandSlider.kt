package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.ui.theme.OrbitTheme
import java.util.Locale

/**
 * 混音台风格全高专业推子组件 (Professional Long-Throw Mixer Fader)
 * 行程加大，阻尼感顺滑，直观展示频段与增益数值
 */
@Composable
fun BandSlider(
    frequencyHz: Float,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minGain: Float = -6f,
    maxGain: Float = 6f
) {
    val colors = OrbitTheme.colors
    val freqLabel = formatFrequency(frequencyHz)
    val gainLabel = when {
        gainDb > 0 -> String.format(Locale.US, "+%.1f", gainDb)
        gainDb == 0f -> "0.0"
        else -> String.format(Locale.US, "%.1f", gainDb)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部：增益数值
        Text(
            text = gainLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled && gainDb != 0f) colors.primary else colors.textSecondary
        )

        // 中间：高行程垂直滑块轨道
        Box(
            modifier = Modifier
                .weight(1f)
                .width(32.dp)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景滑轨槽
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surface)
                    .border(0.5.dp, colors.surfaceBorder, RoundedCornerShape(3.dp))
            )

            // 中间 0dB 基准刻度线
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .background(colors.textSecondary.copy(alpha = 0.4f))
            )

            // 滑块交互区
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectVerticalDragGestures { change, _ ->
                            val totalH = size.height
                            val touchY = change.position.y.coerceIn(0f, totalH.toFloat())
                            // 0 at top = maxGain, totalH at bottom = minGain
                            val fraction = 1f - (touchY / totalH)
                            val newGain = (minGain + fraction * (maxGain - minGain)).coerceIn(minGain, maxGain)
                            onGainChanged(newGain)
                        }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                val totalH = maxHeight
                val fraction = ((gainDb - minGain) / (maxGain - minGain)).coerceIn(0f, 1f)
                val thumbLength = 36.dp
                val thumbOffset = (totalH - thumbLength) * (1f - fraction)

                // 专业加长推子按钮 (Long Professional Fader Knob)
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset.coerceAtLeast(0.dp))
                        .width(28.dp)
                        .height(thumbLength)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(6.dp),
                            spotColor = if (isEnabled) colors.primary.copy(alpha = 0.4f) else Color.Black
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (colors.isDark) {
                                if (isEnabled) {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF353C4E), Color(0xFF1E222D), Color(0xFF151820))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF2A2D36), Color(0xFF1A1C22))
                                    )
                                }
                            } else {
                                if (isEnabled) {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                                    )
                                }
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isEnabled) colors.primary.copy(alpha = 0.7f) else colors.surfaceBorder,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 推子中心高亮荧光发光中线
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isEnabled) colors.primary else colors.textSecondary.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // 底部：频点名称
        Text(
            text = freqLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) colors.textPrimary else colors.textSecondary
        )
    }
}

private fun formatFrequency(hz: Float): String {
    return when {
        hz >= 1000f -> "${(hz / 1000f).toInt()}k"
        else -> "${hz.toInt()}"
    }
}
