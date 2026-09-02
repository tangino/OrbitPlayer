package com.antigravity.equalizer

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque

class ShuffleLogicTest {

    @Test
    fun testHistoryStackNavigation() {
        val playHistory = ArrayDeque<Int>()
        val maxHistorySize = 50

        fun onPlayNext(currentIndex: Int) {
            if (playHistory.peekLast() != currentIndex) {
                playHistory.addLast(currentIndex)
                if (playHistory.size > maxHistorySize) {
                    playHistory.removeFirst()
                }
            }
        }

        fun onPlayPrevious(currentPlaying: Int): Int? {
            if (playHistory.isNotEmpty()) {
                val prev = playHistory.removeLast()
                if (prev != currentPlaying) {
                    return prev
                }
            }
            return null
        }

        // 模拟依次播放歌曲索引: 5 -> 12 -> 3 -> 8
        onPlayNext(5)
        onPlayNext(12)
        onPlayNext(3)
        // 当前在 8
        val current = 8

        // 用户点击上一曲，应回到 3
        val back1 = onPlayPrevious(current)
        assertEquals(3, back1)

        // 再次点击上一曲，应回到 12
        val back2 = onPlayPrevious(back1!!)
        assertEquals(12, back2)

        // 再次点击上一曲，应回到 5
        val back3 = onPlayPrevious(back2!!)
        assertEquals(5, back3)

        // 历史栈已空
        val back4 = onPlayPrevious(back3!!)
        assertNull(back4)
    }

    @Test
    fun testReshuffleDoesNotPickCurrentSong() {
        val playlistSize = 10
        val currentIndex = 6

        // 当一轮洗牌结束，候选列表应排除当前歌曲，避免连续播放同一首歌
        val remaining = (0 until playlistSize).filter { it != currentIndex }
        assertEquals(playlistSize - 1, remaining.size)
        assertFalse(remaining.contains(currentIndex))

        // 多次重复验证
        repeat(50) {
            val nextIndex = remaining.random()
            assertNotEquals(currentIndex, nextIndex)
            assertTrue(nextIndex in 0 until playlistSize)
        }
    }

    @Test
    fun testHistoryStackLimitsSize() {
        val playHistory = ArrayDeque<Int>()
        val maxHistorySize = 50

        fun onPlayNext(currentIndex: Int) {
            if (playHistory.peekLast() != currentIndex) {
                playHistory.addLast(currentIndex)
                if (playHistory.size > maxHistorySize) {
                    playHistory.removeFirst()
                }
            }
        }

        // 写入 100 个不同歌曲索引
        for (i in 0 until 100) {
            onPlayNext(i)
        }

        // 栈大小不应超过 50
        assertEquals(maxHistorySize, playHistory.size)
        // 栈底最早应为 50，栈顶最近应为 99
        assertEquals(50, playHistory.first)
        assertEquals(99, playHistory.last)
    }
}
