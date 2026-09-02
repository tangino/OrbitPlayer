package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.ui.theme.PrimaryNeonCyan
import com.antigravity.equalizer.ui.theme.TextPrimary
import com.antigravity.equalizer.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * 纯粹平滑的 2D 拖拽手势旋转旋钮 (Pure 2D Directional Drag Rotary Knob)
 * 1. 向上/向右滑动增加，向下/向左滑动减少
 * 2. 彻底移除点击瞬跳与误触，全卡片大范围跟手滑动
 */
@Composable
fun DspRotaryKnob(
    title: String,
    strength: Float, // 0.0f ~ 1.0f
    enabled: Boolean,
    onStrengthChanged: (Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val percentage = (strength * 100).toInt()
    val gainDb = strength * 12.0f // 0 ~ +12dB
    val gainLabel = if (strength > 0.01f) "+%.1f dB".format(gainDb) else "0.0 dB"

    // 270 度行程 (从 135度 左下 到 405度 右下)
    val startAngle = 135f
    val sweepAngle = 270f

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E212D))
            // 整个卡片区域均支持手势拖动：向上/向右增加，向下/向左减少
            .pointerInput(enabled, strength) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // 向上/向右为正，向下/向左为负 (阻尼灵敏度适中，滑动约 140dp 跑满全程)
                    val delta = (-dragAmount.y + dragAmount.x) / 140f
                    val newStrength = (strength + delta).coerceIn(0f, 1f)
                    onStrengthChanged(newStrength)
                }
            }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 顶部标题与开启/关闭轻触状态
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleEnabled(!enabled) }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) PrimaryNeonCyan else TextSecondary
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (enabled) PrimaryNeonCyan else Color.Gray)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. 核心大尺寸拟物旋钮盘
        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景圆弧刻度绘制
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 6.dp.toPx()
                val radius = (size.minDimension - strokeWidth - 4.dp.toPx()) / 2
                val center = Offset(size.width / 2, size.height / 2)

                // 背景灰色弧形轨道槽
                drawArc(
                    color = Color(0xFF282B38),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 活跃荧光青发光渐变进度弧
                if (enabled && strength > 0.005f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                PrimaryNeonCyan,
                                Color(0xFF00B0FF),
                                PrimaryNeonCyan
                            )
                        ),
                        startAngle = startAngle,
                        sweepAngle = (sweepAngle * strength).coerceAtLeast(2f),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // 刻度指针发光指示小圆点
                val currentRad = Math.toRadians((startAngle + sweepAngle * strength).toDouble())
                val dotRadius = radius - 8.dp.toPx()
                val dotX = center.x + dotRadius * cos(currentRad).toFloat()
                val dotY = center.y + dotRadius * sin(currentRad).toFloat()

                drawCircle(
                    color = if (enabled) PrimaryNeonCyan else Color.Gray,
                    radius = 3.5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }

            // 中心立体金属发光旋钮圆盘
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = if (enabled) PrimaryNeonCyan.copy(alpha = 0.45f) else Color.Black
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF3C4358),
                                Color(0xFF222634),
                                Color(0xFF151722)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (enabled) "$percentage%" else "OFF",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) PrimaryNeonCyan else TextSecondary
                    )
                    if (enabled && strength > 0.01f) {
                        Text(
                            text = gainLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
