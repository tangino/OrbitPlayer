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

    @Test
    fun testFavoriteFirstShufflePicksFavoritesBeforeOthers() {
        data class MockSong(val id: Long, val isFavorite: Boolean, val playCount: Int)
        val songs = listOf(
            MockSong(1, isFavorite = false, playCount = 10),
            MockSong(2, isFavorite = true, playCount = 5),
            MockSong(3, isFavorite = false, playCount = 2),
            MockSong(4, isFavorite = true, playCount = 8),
            MockSong(5, isFavorite = false, playCount = 0)
        )

        // 模拟未播放集合与当前索引
        val playedIndices = mutableSetOf<Int>()
        val currentIndex = 0

        fun pickNextFavoriteFirst(current: Int): Int {
            val unplayed = (songs.indices).filter { it !in playedIndices && it != current }
            val pool = if (unplayed.isEmpty()) (songs.indices).filter { it != current } else unplayed
            val favorites = pool.filter { songs[it].isFavorite }
            val candidate = if (favorites.isNotEmpty()) favorites.random() else pool.random()
            playedIndices.add(candidate)
            return candidate
        }

        // 第 1 次挑选：池子里有 index 1 和 3 (即 id 2 和 4)，必然选中红心歌曲
        val pick1 = pickNextFavoriteFirst(currentIndex)
        assertTrue("第一次必须挑选红心歌曲", songs[pick1].isFavorite)

        // 第 2 次挑选：剩余红心歌曲
        val pick2 = pickNextFavoriteFirst(pick1)
        assertTrue("第二次必须挑选红心歌曲", songs[pick2].isFavorite)
        assertNotEquals(pick1, pick2)

        // 第 3 次挑选：红心歌曲已挑完，挑出非红心歌曲
        val pick3 = pickNextFavoriteFirst(pick2)
        assertFalse("红心挑选完毕后应挑出普通歌曲", songs[pick3].isFavorite)
    }

    @Test
    fun testLeastPlayedShufflePicksLowestCountFirst() {
        data class MockSong(val id: Long, val playCount: Int)
        val songs = listOf(
            MockSong(1, playCount = 12),
            MockSong(2, playCount = 0),
            MockSong(3, playCount = 8),
            MockSong(4, playCount = 0),
            MockSong(5, playCount = 3)
        )

        val playedIndices = mutableSetOf<Int>()
        val currentIndex = 0

        fun pickNextLeastPlayed(current: Int): Int {
            val unplayed = (songs.indices).filter { it !in playedIndices && it != current }
            val pool = if (unplayed.isEmpty()) (songs.indices).filter { it != current } else unplayed
            val minPlay = pool.minOfOrNull { songs[it].playCount } ?: 0
            val candidates = pool.filter { songs[it].playCount == minPlay }
            val chosen = candidates.random()
            playedIndices.add(chosen)
            return chosen
        }

        // 第一次挑选：playCount 最小值为 0 (index 1 或 3)
        val pick1 = pickNextLeastPlayed(currentIndex)
        assertEquals("第一次必须挑到播放次数为 0 的歌曲", 0, songs[pick1].playCount)

        // 第二次挑选：另一首 playCount 值为 0 的歌曲
        val pick2 = pickNextLeastPlayed(pick1)
        assertEquals("第二次必须挑到播放次数为 0 的歌曲", 0, songs[pick2].playCount)
        assertNotEquals(pick1, pick2)

        // 第三次挑选：下一个最小为 3 (index 4)
        val pick3 = pickNextLeastPlayed(pick2)
        assertEquals("第三次必须挑到播放次数为 3 的歌曲", 3, songs[pick3].playCount)
    }

    @Test
    fun testFolderFilteringWhitelistAndBlacklist() {
        val paths = listOf(
            "/storage/emulated/0/Music/song1.mp3",
            "/storage/emulated/0/Download/song2.mp3",
            "/storage/emulated/0/Android/media/app/song3.mp3",
            "/storage/emulated/0/Music/Rock/song4.flac"
        )

        val included = setOf("/storage/emulated/0/Music")
        val excluded = setOf("/storage/emulated/0/Music/Rock")

        val filtered = paths.filter { path ->
            val isExcluded = excluded.any { path.startsWith(it) }
            val isIncluded = included.isEmpty() || included.any { path.startsWith(it) }
            !isExcluded && isIncluded
        }

        // 只有 song1 应该保留
        assertEquals(1, filtered.size)
        assertEquals("/storage/emulated/0/Music/song1.mp3", filtered.first())
    }

    private data class DummySong(val id: Long, val title: String, val isFavorite: Boolean = false, val isDisliked: Boolean = false, val playCount: Int = 0)

    @Test
    fun testShuffleExcludesDislikedSongs() {
        // 创建 5 首歌曲，其中第 1 首和第 3 首标记为不喜欢
        val songs = listOf(
            DummySong(id = 0, title = "Track 0", isFavorite = false, isDisliked = false),
            DummySong(id = 1, title = "Track 1 (Disliked)", isFavorite = false, isDisliked = true),
            DummySong(id = 2, title = "Track 2", isFavorite = true, isDisliked = false),
            DummySong(id = 3, title = "Track 3 (Disliked)", isFavorite = false, isDisliked = true),
            DummySong(id = 4, title = "Track 4", isFavorite = false, isDisliked = false)
        )

        val currentIndex = 0
        val historySet = setOf<Int>()

        fun pickNext(current: Int): Int {
            var candidates = songs.indices.filter { it != current && it !in historySet && !songs[it].isDisliked }
            if (candidates.isEmpty()) {
                candidates = songs.indices.filter { it != current && !songs[it].isDisliked }
                if (candidates.isEmpty()) {
                    candidates = songs.indices.filter { it != current }
                }
            }
            return candidates.random()
        }

        // 随机执行 100 次，验证绝对不包含 index 1 和 index 3
        repeat(100) {
            val picked = pickNext(currentIndex)
            assertFalse("被标记为不喜欢的歌曲绝对不应被随机播放选中", songs[picked].isDisliked)
            assertTrue("选中的必须是非不喜欢歌曲", picked in listOf(2, 4))
        }
    }

    @Test
    fun testFallbackWhenAllSongsDisliked() {
        val songs = listOf(
            DummySong(id = 0, title = "Track 0", isDisliked = true),
            DummySong(id = 1, title = "Track 1", isDisliked = true),
            DummySong(id = 2, title = "Track 2", isDisliked = true)
        )
        val currentIndex = 0
        var candidates = songs.indices.filter { it != currentIndex && !songs[it].isDisliked }
        if (candidates.isEmpty()) {
            candidates = songs.indices.filter { it != currentIndex }
        }
        assertTrue("当所有歌曲均不喜欢时，应安全降级返回除当前歌曲外的其他歌曲", candidates.isNotEmpty())
        assertFalse("降级时不挑当前歌曲", candidates.contains(currentIndex))
    }
}
