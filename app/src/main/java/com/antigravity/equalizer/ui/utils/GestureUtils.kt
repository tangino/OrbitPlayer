package com.antigravity.equalizer.ui.utils

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.antigravity.equalizer.ui.viewmodel.LibraryViewMode
import kotlin.math.abs
import kotlin.math.sqrt

data class PinchTransformState(
    val scale: Float = 1f,
    val pivot: TransformOrigin = TransformOrigin.Center,
    val isPinching: Boolean = false,
    val isZoomingIn: Boolean = true
)

/**
 * Poweramp 风格双指捏放动画控制器状态
 */
class PinchTransitionState(
    initialPivot: TransformOrigin = TransformOrigin.Center
) {
    var livePivot by mutableStateOf(initialPivot)
    var isPinching by mutableStateOf(false)
    var lastTriggeredDirection by mutableStateOf(true) // true: Zoom In, false: Zoom Out
    var lastTriggeredPivot by mutableStateOf(initialPivot)
}

@Composable
fun rememberPinchTransitionState(): PinchTransitionState {
    return remember { PinchTransitionState() }
}

/**
 * Poweramp 风格流体双指捏放手势引擎
 * 1. 实时计算双指中心点 (Focal Pivot) 与实时距离变化率
 * 2. 未达阈值时微弹性跟手拉伸，松开双指触发 spring 自然阻尼回弹
 * 3. 达到阈值触发切换，记录动画原点与缩放方向
 */
@Composable
fun Modifier.pinchToZoomViewMode(
    currentViewMode: LibraryViewMode,
    pinchState: PinchTransitionState,
    onViewModeChange: (LibraryViewMode) -> Unit
): Modifier {
    val currentModeState by rememberUpdatedState(currentViewMode)
    val onModeChangeState by rememberUpdatedState(onViewModeChange)

    return this.pointerInput(Unit) {
        var lastTriggerTime = 0L
        awaitEachGesture {
            var accumulatedZoom = 1f
            var hasTriggeredInCurrentGesture = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val activePointers = event.changes.filter { it.pressed }

                if (activePointers.size >= 2) {
                    pinchState.isPinching = true
                    val p1 = activePointers[0]
                    val p2 = activePointers[1]

                    // 1. 计算双指触控中心点 (Focal Pivot)
                    val focalX = (p1.position.x + p2.position.x) / 2f
                    val focalY = (p1.position.y + p2.position.y) / 2f

                    val pivotX = (focalX / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                    val pivotY = (focalY / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                    val pivotOrigin = TransformOrigin(pivotX, pivotY)
                    pinchState.livePivot = pivotOrigin

                    // 2. 计算双指欧氏距离变化量
                    val currentDistance = (p1.position - p2.position).calcDistance()
                    val prevDistance = (p1.previousPosition - p2.previousPosition).calcDistance()

                    if (prevDistance > 4f && currentDistance > 4f) {
                        val zoom = (currentDistance / prevDistance).coerceIn(0.6f, 1.6f)
                        accumulatedZoom *= zoom

                        if (!hasTriggeredInCurrentGesture) {
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerTime > 320L) {
                                val activeMode = currentModeState

                                // 双指张开（放大 / Zoom In）：列表 -> 网格大图 -> 网格 (灵敏阈值 1.10f)
                                if (accumulatedZoom > 1.10f) {
                                    val nextMode = when (activeMode) {
                                        LibraryViewMode.LIST_NO_ART -> LibraryViewMode.LIST_SMALL_ART
                                        LibraryViewMode.LIST_SMALL_ART -> LibraryViewMode.LIST_LARGE_ART
                                        LibraryViewMode.LIST_LARGE_ART -> LibraryViewMode.GRID_2_COL
                                        LibraryViewMode.GRID_2_COL -> LibraryViewMode.GRID_3_COL
                                        LibraryViewMode.GRID_3_COL -> LibraryViewMode.GRID_4_COL
                                        LibraryViewMode.GRID_4_COL -> LibraryViewMode.GRID_4_COL
                                    }
                                    if (nextMode != activeMode) {
                                        pinchState.lastTriggeredDirection = true
                                        pinchState.lastTriggeredPivot = pivotOrigin
                                        hasTriggeredInCurrentGesture = true
                                        lastTriggerTime = now
                                        onModeChangeState(nextMode)
                                    }
                                }
                                // 双指捏合（缩小 / Zoom Out）：多列网格 -> 大图列表 -> 小图列表 -> 无图列表 (灵敏阈值 0.90f)
                                else if (accumulatedZoom < 0.90f) {
                                    val prevMode = when (activeMode) {
                                        LibraryViewMode.GRID_4_COL -> LibraryViewMode.GRID_3_COL
                                        LibraryViewMode.GRID_3_COL -> LibraryViewMode.GRID_2_COL
                                        LibraryViewMode.GRID_2_COL -> LibraryViewMode.LIST_LARGE_ART
                                        LibraryViewMode.LIST_LARGE_ART -> LibraryViewMode.LIST_SMALL_ART
                                        LibraryViewMode.LIST_SMALL_ART -> LibraryViewMode.LIST_NO_ART
                                        LibraryViewMode.LIST_NO_ART -> LibraryViewMode.LIST_NO_ART
                                    }
                                    if (prevMode != activeMode) {
                                        pinchState.lastTriggeredDirection = false
                                        pinchState.lastTriggeredPivot = pivotOrigin
                                        hasTriggeredInCurrentGesture = true
                                        lastTriggerTime = now
                                        onModeChangeState(prevMode)
                                    }
                                }
                            }
                        }

                        p1.consume()
                        p2.consume()
                    }
                } else {
                    if (pinchState.isPinching) {
                        pinchState.isPinching = false
                    }
                    if (event.changes.all { !it.pressed }) {
                        break
                    }
                }
            }
        }
    }
}

/**
 * 兼容性重载：支持简写调用
 */
@Composable
fun Modifier.pinchToZoomViewMode(
    currentViewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit
): Modifier {
    val pinchState = rememberPinchTransitionState()
    return this.pinchToZoomViewMode(
        currentViewMode = currentViewMode,
        pinchState = pinchState,
        onViewModeChange = onViewModeChange
    )
}

/**
 * 左右滑动手势切歌修饰符 (MiniPlayer 与 NowPlaying 通用)
 * - 向左滑 (Swipe Left) -> 切换下一首
 * - 向右滑 (Swipe Right) -> 切换上一首
 */
@Composable
fun Modifier.swipeToChangeSong(
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    thresholdPx: Float = 100f
): Modifier {
    val nextAction by rememberUpdatedState(onSwipeNext)
    val prevAction by rememberUpdatedState(onSwipePrevious)

    return this.pointerInput(Unit) {
        var totalDragX = 0f
        var hasTriggered = false

        detectHorizontalDragGestures(
            onDragStart = {
                totalDragX = 0f
                hasTriggered = false
            },
            onDragEnd = {
                if (!hasTriggered) {
                    if (totalDragX < -thresholdPx) {
                        nextAction()
                    } else if (totalDragX > thresholdPx) {
                        prevAction()
                    }
                }
                totalDragX = 0f
                hasTriggered = false
            },
            onDragCancel = {
                totalDragX = 0f
                hasTriggered = false
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                if (!hasTriggered) {
                    if (totalDragX < -thresholdPx * 1.5f) {
                        nextAction()
                        hasTriggered = true
                    } else if (totalDragX > thresholdPx * 1.5f) {
                        prevAction()
                        hasTriggered = true
                    }
                }
                change.consume()
            }
        )
    }
}

private fun Offset.calcDistance(): Float {
    return sqrt(x * x + y * y)
}
