package com.antigravity.equalizer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antigravity.equalizer.ui.utils.PinchTransitionState
import com.antigravity.equalizer.ui.viewmodel.LibraryViewMode

/**
 * Poweramp 电影级双指捏放 (Pinch) 视图切换转场容器：
 * 精确还原 1.mp4 与 2.mp4 视频中的标志性物理动效：
 * 1. 实时手势跟手：手指按住捏放滑动时，按双指 Focal Point 产生微缩放阻尼反馈
 * 2. 深度景深缩放 (Depth Zoom) & 交叉渐变 (Cross-Fade)：
 *    - Zoom In：旧视图向外继续放大 (1.20f) 并迅速淡出；新视图从深层 (0.74f) 伴随自然物理阻尼弹入推进至 1.0f
 *    - Zoom Out：旧视图向内收敛缩小 (0.82f) 并迅速淡出；新视图从外层 (1.26f) 向内收缩入场
 * 3. 弹性自然收敛：采用 spring(dampingRatio = 0.82f, stiffness = 380f)，触感饱满顺滑
 */
@Composable
fun PowerampViewModeTransitionContainer(
    viewMode: LibraryViewMode,
    pinchState: PinchTransitionState,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryViewMode) -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                val isZoomIn = pinchState.lastTriggeredDirection
                val pivot = pinchState.lastTriggeredPivot

                if (isZoomIn) {
                    // Zoom In 转场：新视图从深层平滑放大推进至 1.0f (去除了回弹振荡，直接平滑到位)
                    (scaleIn(
                        initialScale = 0.74f,
                        transformOrigin = pivot,
                        animationSpec = tween(
                            durationMillis = 260,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )).togetherWith(
                        // 旧视图：向外继续扩张放大至 1.20f，并在 200ms 内迅速淡出
                        scaleOut(
                            targetScale = 1.20f,
                            transformOrigin = pivot,
                            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                        )
                    )
                } else {
                    // Zoom Out 转场：新视图从前景层平滑收缩进入至 1.0f (去除了回弹振荡，直接平滑到位)
                    (scaleIn(
                        initialScale = 1.26f,
                        transformOrigin = pivot,
                        animationSpec = tween(
                            durationMillis = 260,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )).togetherWith(
                        // 旧视图：向内收敛缩小至 0.82f，并在 200ms 内迅速淡出
                        scaleOut(
                            targetScale = 0.82f,
                            transformOrigin = pivot,
                            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                        )
                    )
                }.using(
                    SizeTransform(clip = false)
                )
            },
            label = "PowerampPinchTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetMode ->
            content(targetMode)
        }
    }
}
