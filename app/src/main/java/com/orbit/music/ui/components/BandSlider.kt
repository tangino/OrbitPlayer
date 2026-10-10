package com.orbit.music.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.music.ui.theme.OrbitTheme
import java.util.Locale

/**
 * 符合 OrbitPlayer 全局设计语言的专业硬件推子组件
 * 1. 顶部与底部精密刻度线 (- - -)
 * 2. 融入应用主题色 (primary) 的动态发光能量槽
 * 3. 质感胶囊滑块手柄 (Thumb)
 * 4. 底部频点大字体与增益实时数值
 */
@Composable
fun BandSlider(
    frequencyHz: Float,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minGain: Float = -7f,
    maxGain: Float = 7f
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
            .width(42.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部两端微小刻度短线 - -
        Row(
            modifier = Modifier.width(22.dp).padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(3.dp).height(1.dp).background(colors.surfaceBorder))
            Box(modifier = Modifier.width(3.dp).height(1.dp).background(colors.surfaceBorder))
        }

        // 中间：高行程垂直滑块轨道
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景垂直导槽
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(colors.surface)
            )

            // 滑块交互区与能量槽渲染
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectVerticalDragGestures { change, _ ->
                            val totalH = size.height
                            val touchY = change.position.y.coerceIn(0f, totalH.toFloat())
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
                val thumbWidth = 24.dp
                val thumbOffset = (totalH - thumbLength) * (1f - fraction)

                // 主题色垂直能量槽：从胶囊滑块中心向下延伸到底部
                if (isEnabled) {
                    val energyTop = thumbOffset + thumbLength / 2f
                    val energyHeight = (totalH - energyTop).coerceAtLeast(0.dp)

                    Box(
                        modifier = Modifier
                            .offset(y = energyTop)
                            .width(2.8.dp)
                            .height(energyHeight)
                            .clip(RoundedCornerShape(1.4.dp))
                            .background(colors.primary)
                    )
                }

                // 经典质感胶囊滑块手柄 (Capsule Thumb)
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset.coerceAtLeast(0.dp))
                        .width(thumbWidth)
                        .height(thumbLength)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = if (isEnabled) colors.primary.copy(alpha = 0.35f) else Color.Black
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceCard)
                        .border(
                            width = 0.8.dp,
                            color = if (isEnabled) colors.surfaceBorder else colors.surface,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 滑块中央高亮横向凹槽指示条
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isEnabled) colors.textPrimary else colors.textSecondary.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // 底部刻度短线 - -
        Row(
            modifier = Modifier.width(22.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(3.dp).height(1.dp).background(colors.surfaceBorder))
            Box(modifier = Modifier.width(3.dp).height(1.dp).background(colors.surfaceBorder))
        }

        // 底部第一行：频点粗体标识 (如 31, 62, 125, 1K)
        Text(
            text = freqLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) colors.textPrimary else colors.textSecondary
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 底部第二行：当前增益数值 (如 0.0, +2.5)
        Text(
            text = gainLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isEnabled && gainDb != 0f) colors.primary else colors.textSecondary
        )
    }
}

/**
 * 独立的「增益」前级 Preamp 推子卡片
 */
@Composable
fun PreampFader(
    preampGainDb: Float,
    isEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minGain: Float = -12f,
    maxGain: Float = 12f
) {
    val colors = OrbitTheme.colors
    val gainLabel = when {
        preampGainDb > 0 -> String.format(Locale.US, "+%.1f", preampGainDb)
        preampGainDb == 0f -> "0.0"
        else -> String.format(Locale.US, "%.1f", preampGainDb)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .border(0.8.dp, colors.surfaceBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部刻度短线
            Row(
                modifier = Modifier.width(24.dp).padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.width(4.dp).height(1.dp).background(colors.surfaceBorder))
                Box(modifier = Modifier.width(4.dp).height(1.dp).background(colors.surfaceBorder))
            }

            // 中间垂直滑动区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.surface)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isEnabled) {
                            if (!isEnabled) return@pointerInput
                            detectVerticalDragGestures { change, _ ->
                                val totalH = size.height
                                val touchY = change.position.y.coerceIn(0f, totalH.toFloat())
                                val fraction = 1f - (touchY / totalH)
                                val newGain = (minGain + fraction * (maxGain - minGain)).coerceIn(minGain, maxGain)
                                onGainChanged(newGain)
                            }
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    val totalH = maxHeight
                    val fraction = ((preampGainDb - minGain) / (maxGain - minGain)).coerceIn(0f, 1f)
                    val thumbLength = 36.dp
                    val thumbWidth = 26.dp
                    val thumbOffset = (totalH - thumbLength) * (1f - fraction)

                    // 胶囊手柄
                    Box(
                        modifier = Modifier
                            .offset(y = thumbOffset.coerceAtLeast(0.dp))
                            .width(thumbWidth)
                            .height(thumbLength)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(13.dp),
                                spotColor = Color.Black
                            )
                            .clip(RoundedCornerShape(13.dp))
                            .background(colors.surface)
                            .border(0.8.dp, colors.surfaceBorder, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(colors.textPrimary)
                        )
                    }
                }
            }

            // 底部刻度短线
            Row(
                modifier = Modifier.width(24.dp).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.width(4.dp).height(1.dp).background(colors.surfaceBorder))
                Box(modifier = Modifier.width(4.dp).height(1.dp).background(colors.surfaceBorder))
            }

            // 底部两行：增益 与 0.0
            Text(
                text = "增益",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) colors.textPrimary else colors.textSecondary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = gainLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled && preampGainDb != 0f) colors.primary else colors.textSecondary
            )
        }
    }
}

private fun formatFrequency(hz: Float): String {
    return when {
        hz >= 1000f -> "${(hz / 1000f).toInt()}K"
        else -> "${hz.toInt()}"
    }
}
