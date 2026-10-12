package com.orbit.music

import com.orbit.music.ui.floating.FloatingResizeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮窗"长按拖动四角圆点自由调整大小"的换算逻辑校验
 */
class FloatingResizeMathTest {

    // 手机档位基准尺寸换算成像素 (205x54dp @ density 3.0625)
    private val baseW = 205f * 3.0625f
    private val baseH = 54f * 3.0625f

    @Test
    fun draggingOutwardEnlargesForEveryCorner() {
        // 四个角沿自身向外方向拖动，缩放增量必须完全一致且为正
        val deltaBr = FloatingResizeMath.scaleDelta(120f, 60f, 1, 1, baseW, baseH)
        val deltaTl = FloatingResizeMath.scaleDelta(-120f, -60f, -1, -1, baseW, baseH)
        val deltaTr = FloatingResizeMath.scaleDelta(120f, -60f, 1, -1, baseW, baseH)
        val deltaBl = FloatingResizeMath.scaleDelta(-120f, 60f, -1, 1, baseW, baseH)

        assertTrue("向外拖动必须放大", deltaBr > 0f)
        assertEquals(deltaBr, deltaTl, 1e-6f)
        assertEquals(deltaBr, deltaTr, 1e-6f)
        assertEquals(deltaBr, deltaBl, 1e-6f)
    }

    @Test
    fun draggingInwardShrinks() {
        val delta = FloatingResizeMath.scaleDelta(-120f, -60f, 1, 1, baseW, baseH)
        assertTrue("向内拖动必须缩小", delta < 0f)
    }

    @Test
    fun noMovementMeansNoScaleChange() {
        assertEquals(0f, FloatingResizeMath.scaleDelta(0f, 0f, 1, 1, baseW, baseH), 1e-6f)
    }

    @Test
    fun horizontalDragTracksFingerWidth() {
        // 中心对称展开下，单角向外拖动 dx 时，窗口向该侧单边扩展位移应精准跟随手指位移 (误差 < 15%)
        val dx = 200f
        val delta = FloatingResizeMath.scaleDelta(dx, 0f, 1, 1, baseW, baseH)
        val halfWidthGrowth = (delta * baseW) / 2f
        assertEquals(dx, halfWidthGrowth, dx * 0.15f)
    }

    @Test
    fun extremeDragIsBoundedByClamp() {
        // 极限拖动后仍受缩放区间钳制，不会出现负数或无穷大
        val delta = FloatingResizeMath.scaleDelta(4000f, 4000f, 1, 1, baseW, baseH)
        val clamped = FloatingResizeMath.clampScale(1.0f + delta, 0.6f, 2.0f)
        assertEquals(2.0f, clamped, 1e-6f)

        val shrink = FloatingResizeMath.clampScale(1.0f - 5f, 0.6f, 2.0f)
        assertEquals(0.6f, shrink, 1e-6f)
    }

    @Test
    fun maxScaleKeepsWindowInsideScreen() {
        // 1260x2800 手机屏幕：上限由屏幕宽度决定，且不会超过硬上限 2.2
        val maxScale = FloatingResizeMath.maxScaleForBounds(
            screenWidthPx = 1260,
            screenHeightPx = 2800,
            baseWidthPx = baseW,
            baseHeightPx = baseH,
            hardMaxScale = 2.2f,
            minScale = 0.6f
        )
        assertTrue("放大后必须仍在屏幕内", maxScale * baseW <= 1260f)
        assertTrue(maxScale <= 2.2f)
        assertTrue(maxScale > 1.0f)
    }

    @Test
    fun degenerateBaseSizeFallsBackToHardLimit() {
        assertEquals(
            2.2f,
            FloatingResizeMath.maxScaleForBounds(1260, 2800, 0f, 0f, 2.2f, 0.6f),
            1e-6f
        )
        assertEquals(
            0f,
            FloatingResizeMath.scaleDelta(100f, 100f, 1, 1, 0f, 0f),
            1e-6f
        )
    }
}
