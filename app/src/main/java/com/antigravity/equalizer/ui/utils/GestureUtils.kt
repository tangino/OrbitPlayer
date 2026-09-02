package com.antigravity.equalizer.ui.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    val isZoomingIn: Boolean = true
)

/**
 * Poweramp 风格流体双指捏放手势引擎
 */
@Composable
fun Modifier.pinchToZoomViewMode(
    currentViewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onTransformChange: (PinchTransformState) -> Unit
): Modifier {
    val currentModeState by rememberUpdatedState(currentViewMode)
    val onModeChangeState by rememberUpdatedState(onViewModeChange)
    val onTransformChangeState by rememberUpdatedState(onTransformChange)

    return this.pointerInput(Unit) {
        var lastTriggerTime = 0L
        awaitEachGesture {
            var accumulatedZoom = 1f
            var isPinching = false
            var hasTriggeredInCurrentGesture = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val activePointers = event.changes.filter { it.pressed }

                if (activePointers.size >= 2) {
                    isPinching = true
                    val p1 = activePointers[0]
                    val p2 = activePointers[1]

                    // 1. 计算双指触控中心点 (Focal Pivot)
                    val focalX = (p1.position.x + p2.position.x) / 2f
                    val focalY = (p1.position.y + p2.position.y) / 2f

                    val pivotX = (focalX / size.width.toFloat()).coerceIn(0.1f, 0.9f)
                    val pivotY = (focalY / size.height.toFloat()).coerceIn(0.1f, 0.9f)
                    val pivotOrigin = TransformOrigin(pivotX, pivotY)

                    // 2. 计算双指欧氏距离变化量
                    val currentDistance = (p1.position - p2.position).calcDistance()
                    val prevDistance = (p1.previousPosition - p2.previousPosition).calcDistance()

                    if (prevDistance > 10f && currentDistance > 10f) {
                        val zoom = (currentDistance / prevDistance).coerceIn(0.5f, 2.0f)
                        accumulatedZoom *= zoom

                        val visualScale = if (hasTriggeredInCurrentGesture) {
                            1f + (accumulatedZoom - 1f) * 0.08f
                        } else {
                            accumulatedZoom.coerceIn(0.75f, 1.35f)
                        }

                        onTransformChangeState(
                            PinchTransformState(
                                scale = visualScale,
                                pivot = pivotOrigin,
                                isZoomingIn = accumulatedZoom >= 1f
                            )
                        )

                        // 严格单次触发切换 + 400ms 时间戳冷却防抖
                        val now = System.currentTimeMillis()
                        if (!hasTriggeredInCurrentGesture && now - lastTriggerTime > 400L) {
                            val activeMode = currentModeState

                            // 双指张开（放大 / Zoom In）：无图列表 -> 小图列表 -> 大图列表 -> 2列grid -> 3列grid -> 4列grid
                            if (accumulatedZoom > 1.25f) {
                                val nextMode = when (activeMode) {
                                    LibraryViewMode.LIST_NO_ART -> LibraryViewMode.LIST_SMALL_ART
                                    LibraryViewMode.LIST_SMALL_ART -> LibraryViewMode.LIST_LARGE_ART
                                    LibraryViewMode.LIST_LARGE_ART -> LibraryViewMode.GRID_2_COL
                                    LibraryViewMode.GRID_2_COL -> LibraryViewMode.GRID_3_COL
                                    LibraryViewMode.GRID_3_COL -> LibraryViewMode.GRID_4_COL
                                    LibraryViewMode.GRID_4_COL -> LibraryViewMode.GRID_4_COL
                                }
                                if (nextMode != activeMode) {
                                    onModeChangeState(nextMode)
                                    hasTriggeredInCurrentGesture = true
                                    lastTriggerTime = now
                                }
                            }
                            // 双指捏合（缩小 / Zoom Out）：4列grid -> 3列grid -> 2列grid -> 大图列表 -> 小图列表 -> 无图列表
                            else if (accumulatedZoom < 0.75f) {
                                val prevMode = when (activeMode) {
                                    LibraryViewMode.GRID_4_COL -> LibraryViewMode.GRID_3_COL
                                    LibraryViewMode.GRID_3_COL -> LibraryViewMode.GRID_2_COL
                                    LibraryViewMode.GRID_2_COL -> LibraryViewMode.LIST_LARGE_ART
                                    LibraryViewMode.LIST_LARGE_ART -> LibraryViewMode.LIST_SMALL_ART
                                    LibraryViewMode.LIST_SMALL_ART -> LibraryViewMode.LIST_NO_ART
                                    LibraryViewMode.LIST_NO_ART -> LibraryViewMode.LIST_NO_ART
                                }
                                if (prevMode != activeMode) {
                                    onModeChangeState(prevMode)
                                    hasTriggeredInCurrentGesture = true
                                    lastTriggerTime = now
                                }
                            }
                        }

                        p1.consume()
                        p2.consume()
                    }
                } else {
                    if (isPinching) {
                        isPinching = false
                        accumulatedZoom = 1f
                        onTransformChangeState(PinchTransformState(scale = 1f))
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
