package com.orbit.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.music.ui.theme.OrbitTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 1:1 还原 Poweramp 旗舰级丝滑手感与声学质感的专业圆盘旋钮 (Rotary Knob)
 * 1. 极致丝滑手感架构：
 *    - 本地瞬态无缝驱动 (Local Fast-path State)：手势帧内即时重绘，杜绝 Compose 重组掉帧；
 *    - 稳定持久手势信道 (Stable pointerInput)：永不因数值改变重启手势协程，杜绝手势断触；
 *    - 绕圈旋转 (Rotary Tracking)：手指沿圆周转动，按极坐标角度 1:1 丝滑跟随；
 *    - 垂直推拉 (Vertical Dragging)：单指垂直上下轻推，自然线性平滑调节；
 *    - 点击上下部微调 (Tap Micro-stepping)：点击旋钮上方 +2%，点击下方 -2%；
 *    - 双击重置 (Double-tap to Reset)：快速双击恢复 50% 默认平衡位置。
 * 2. 视觉表现：
 *    - 纯净金属质感微凸圆盘底座；
 *    - 270度外缘环形声学刻度轨 (135° ~ 405°)；
 *    - 内嵌高精度指向指针。
 */
@Composable
fun DspRotaryKnob(
    title: String,
    strength: Float, // 0.0f ~ 1.0f
    enabled: Boolean,
    onStrengthChanged: (Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    // 本地即时响应状态 (避免跨层级 Recomposition 导致掉帧与卡顿)
    var localStrength by remember { mutableFloatStateOf(strength) }

    // 当外部由于预设改变等传入新值时，同步给本地状态
    LaunchedEffect(strength) {
        if (kotlin.math.abs(localStrength - strength) > 0.001f) {
            localStrength = strength
        }
    }

    // 稳定引用回调，避免 pointerInput 协程因外部状态改变而意外销毁重启
    val currentOnStrengthChanged by rememberUpdatedState(onStrengthChanged)
    val currentEnabled by rememberUpdatedState(enabled)

    val percentage = (localStrength * 100).toInt()
    val startAngle = 135f
    val sweepAngle = 270f
    val currentAngle = startAngle + localStrength * sweepAngle
    val currentAngleRad = Math.toRadians(currentAngle.toDouble())

    // 记录双击时间戳
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部两行：第一行标题（低音/高音），第二行实时数值（50%）
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$percentage%",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (percentage > 0) colors.primary else colors.textSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 旋钮主体：支持 Poweramp 1:1 极坐标旋转、垂直拖动、点击微调与双击重置
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color.Black)
                .clip(CircleShape)
                .background(colors.surfaceCard)
                .border(1.dp, colors.surfaceBorder, CircleShape)
                .pointerInput(Unit) { // 关键：key 必须为 Unit，手势过程中绝对不重启协程！
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEnabled) return@awaitEachGesture

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val downPos = down.position
                        var totalMovedDistance = 0f
                        var prevPos = downPos
                        val pointer = down

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == pointer.id } ?: break

                            if (change.pressed) {
                                val newPos = change.position
                                val dist = (newPos - prevPos).getDistance()
                                totalMovedDistance += dist

                                if (totalMovedDistance > 6f) {
                                    change.consume()

                                    // 计算角位移
                                    val anglePrev = Math.toDegrees(atan2((prevPos.y - cy).toDouble(), (prevPos.x - cx).toDouble())).toFloat()
                                    val angleNow = Math.toDegrees(atan2((newPos.y - cy).toDouble(), (newPos.x - cx).toDouble())).toFloat()
                                    var deltaAngle = angleNow - anglePrev
                                    if (deltaAngle > 180f) deltaAngle -= 360f
                                    if (deltaAngle < -180f) deltaAngle += 360f

                                    // 垂直位移增量 (向上为正)
                                    val deltaY = -(newPos.y - prevPos.y)

                                    // 距离圆心的径向距离
                                    val radiusFromCenter = sqrt((newPos.x - cx) * (newPos.x - cx) + (newPos.y - cy) * (newPos.y - cy))

                                    // 手势融合：圆周拖动优先，中心上下推拉次之
                                    val delta = if (radiusFromCenter > 15f && kotlin.math.abs(deltaAngle) > 0.3f) {
                                        deltaAngle / 270f
                                    } else {
                                        deltaY / 220f
                                    }

                                    val newStrength = (localStrength + delta).coerceIn(0f, 1f)
                                    if (newStrength != localStrength) {
                                        localStrength = newStrength
                                        currentOnStrengthChanged(newStrength)
                                    }
                                }
                                prevPos = newPos
                            } else {
                                // 手指抬起
                                change.consume()
                                val now = System.currentTimeMillis()

                                // 若移动距离很小，判定为点击事件
                                if (totalMovedDistance <= 6f) {
                                    if (now - lastTapTime < 320L) {
                                        // 连续双击：1:1 还原 Poweramp 恢复默认 50%
                                        localStrength = 0.5f
                                        currentOnStrengthChanged(0.5f)
                                        lastTapTime = 0L
                                    } else {
                                        lastTapTime = now
                                        // 单击微调：点击圆心上方 +2%，点击下方 -2%
                                        val isUpperHalf = downPos.y < cy
                                        val microStep = if (isUpperHalf) 0.02f else -0.02f
                                        val newS = (localStrength + microStep).coerceIn(0f, 1f)
                                        localStrength = newS
                                        currentOnStrengthChanged(newS)
                                    }
                                }
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // 1. 270度外缘底轨弧线 (底色)
                drawArc(
                    color = colors.surfaceBorder.copy(alpha = 0.5f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius + 2.dp.toPx(), center.y - radius + 2.dp.toPx()),
                    size = Size((radius - 2.dp.toPx()) * 2, (radius - 2.dp.toPx()) * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // 2. 激活高亮弧线 (从 135° 顺时针延伸至当前角度)
                if (localStrength > 0.005f) {
                    drawArc(
                        color = colors.primary,
                        startAngle = startAngle,
                        sweepAngle = localStrength * sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius + 2.dp.toPx(), center.y - radius + 2.dp.toPx()),
                        size = Size((radius - 2.dp.toPx()) * 2, (radius - 2.dp.toPx()) * 2),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. 旋钮内缘指针指示条 (中心到边缘)
                val pointerLength = radius * 0.38f
                val startDist = radius * 0.35f
                val endDist = startDist + pointerLength

                val startX = center.x + startDist * cos(currentAngleRad).toFloat()
                val startY = center.y + startDist * sin(currentAngleRad).toFloat()
                val endX = center.x + endDist * cos(currentAngleRad).toFloat()
                val endY = center.y + endDist * sin(currentAngleRad).toFloat()

                drawLine(
                    color = if (percentage > 0) colors.primary else colors.textPrimary,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
