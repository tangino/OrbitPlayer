package com.orbit.music

import com.orbit.music.ui.floating.FloatingLyricHelper
import com.orbit.music.utils.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮窗"快唱完剩2个字提前平滑向上滚动"算法校验
 */
class FloatingLyricHelperTest {

    private val lines = listOf(
        LyricLine(5000L, "床前明月光"),   // 5字, 下一句在 9000ms (时长 4000ms)
        LyricLine(9000L, "疑是地上霜"),   // 5字, 下一句在 13000ms (时长 4000ms)
        LyricLine(13000L, "举头望明月"),  // 5字, 下一句在 17000ms (时长 4000ms)
        LyricLine(17000L, "低头思故乡")   // 5字, 最后一句
    )

    @Test
    fun advanceMsIsCalculatedBasedOnTwoCharsRemaining() {
        // 4000ms 唱 5 个字，单字 800ms，2 个字 1600ms -> 被安全区间钳制在 850ms
        val advance = FloatingLyricHelper.calculateAdvanceMs(lines[0], lines[1])
        assertEquals(850L, advance)
    }

    @Test
    fun advanceMsForFastSongIsScaledNaturally() {
        // 快速说唱：2000ms 唱 10 个字，单字 200ms，2 个字 400ms -> 被区间钳制在 450ms
        val fastLine1 = LyricLine(1000L, "快速说唱歌词一二三四五六七八")
        val fastLine2 = LyricLine(3000L, "接下一句说唱")
        val advance = FloatingLyricHelper.calculateAdvanceMs(fastLine1, fastLine2)
        assertEquals(450L, advance)
    }

    @Test
    fun scrollsUpEarlyWhenTwoCharsRemaining() {
        // 第 0 句在 5000ms，第 1 句在 9000ms，提前量 850ms -> 切换点为 8150ms
        // 8140ms 时还在唱第 0 句前 3 个字
        assertEquals(0, FloatingLyricHelper.findActiveLyricIndex(lines, 8140L))
        // 8150ms 时快唱完剩 2 个字，应该立即切换到第 1 句触发滚动动画！
        assertEquals(1, FloatingLyricHelper.findActiveLyricIndex(lines, 8150L))
        // 8500ms 时滚动动效(300ms)已完成，第 1 句高亮就位
        assertEquals(1, FloatingLyricHelper.findActiveLyricIndex(lines, 8500L))
        // 9000ms 正式开唱第 1 句
        assertEquals(1, FloatingLyricHelper.findActiveLyricIndex(lines, 9000L))
    }

    @Test
    fun nextLineAlsoScrollsEarly() {
        // 第 1 句在 9000ms，第 2 句在 13000ms，切换点为 13000 - 850 = 12150ms
        assertEquals(1, FloatingLyricHelper.findActiveLyricIndex(lines, 12140L))
        assertEquals(2, FloatingLyricHelper.findActiveLyricIndex(lines, 12150L))
    }

    @Test
    fun emptyListReturnsNegativeOne() {
        assertEquals(-1, FloatingLyricHelper.findActiveLyricIndex(emptyList(), 1000L))
    }

    @Test
    fun resolvesIntroStateBeforeFirstLine() {
        val testLines = listOf(
            LyricLine(10000L, "天青色等烟雨"),
            LyricLine(25000L, "而我在等你")
        )
        // 0s 到 9.3s 为纯音乐前奏阶段
        val state = FloatingLyricHelper.resolveLyricState(testLines, 2000L)
        assertEquals("intro", state.stateKey)
        assertEquals("♪ 前奏 ♪", state.text1)
        assertEquals("天青色等烟雨", state.text2)
        assertTrue(state.isInstrumental)
    }

    @Test
    fun resolvesInterludeWhenVocalFinishesAndGapIsLong() {
        // 第一句在 10000ms，第二句在 25000ms (长达 15 秒间隔)
        // "天青色等烟雨" 共 6 个字，估算演唱时长: 6 * 380 + 1200 = 3480ms
        // 演唱结束时间 = 10000 + 3480 = 13480ms，留白阅读 800ms -> 14280ms
        val testLines = listOf(
            LyricLine(10000L, "天青色等烟雨"),
            LyricLine(25000L, "而我在等你")
        )
        // 11000ms 时正在唱第一句
        val singingState = FloatingLyricHelper.resolveLyricState(testLines, 11000L)
        assertEquals("line_0", singingState.stateKey)
        assertEquals("天青色等烟雨", singingState.text1)

        // 14500ms 时人声已经唱完，音乐伴奏还在继续 -> 自动向上滚动变为间奏！
        val interludeState = FloatingLyricHelper.resolveLyricState(testLines, 14500L)
        assertEquals("interlude_0", interludeState.stateKey)
        assertEquals("♪ 间奏 ♪", interludeState.text1)
        assertEquals("而我在等你", interludeState.text2)
        assertTrue(interludeState.isInstrumental)

        // 24500ms 时间奏即将结束，提前量切入第二句
        val nextState = FloatingLyricHelper.resolveLyricState(testLines, 24500L)
        assertEquals("line_1", nextState.stateKey)
        assertEquals("而我在等你", nextState.text1)
    }

    @Test
    fun doesNotTriggerInterludeWhenLinesAreCompact() {
        // 两句之间仅相隔 3 秒，紧凑连唱，不应插入晃眼的间奏
        val compactLines = listOf(
            LyricLine(5000L, "床前明月光"),
            LyricLine(8000L, "疑是地上霜")
        )
        // 整个过程中状态应该直接从 line_0 过渡到 line_1，不会出现 interlude
        val state1 = FloatingLyricHelper.resolveLyricState(compactLines, 6000L)
        assertEquals("line_0", state1.stateKey)

        val state2 = FloatingLyricHelper.resolveLyricState(compactLines, 7500L)
        assertEquals("line_1", state2.stateKey)
    }

    @Test
    fun resolvesOutroWhenLastLineFinishes() {
        val testLines = listOf(
            LyricLine(5000L, "这是全曲最后一句歌词") // 10 个字
        )
        // 6000ms 还在演唱最后一句
        val singingState = FloatingLyricHelper.resolveLyricState(testLines, 6000L)
        assertEquals("line_0", singingState.stateKey)

        // 15000ms 时最后一句人声早已唱完，伴奏仍在继续 -> 向上滚动变为尾奏！
        val outroState = FloatingLyricHelper.resolveLyricState(testLines, 15000L)
        assertEquals("outro", outroState.stateKey)
        assertEquals("♪ 尾奏 ♪", outroState.text1)
        assertTrue(outroState.isInstrumental)
    }
}
