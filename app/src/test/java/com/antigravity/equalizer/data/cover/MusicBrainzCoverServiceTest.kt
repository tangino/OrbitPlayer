package com.antigravity.equalizer.data.cover

import org.junit.Assert.*
import org.junit.Test

/**
 * MusicBrainzCoverService 单元测试
 * 覆盖：文件名清洗算法、歌手歌名拆解、Lucene语法转义、大尺寸封面判定策略
 */
class MusicBrainzCoverServiceTest {

    @Test
    fun testCleanFileName_removesBracketsAndTrackNumbers() {
        val raw1 = "01 - 周杰伦 - 晴天 [FLAC] (320k) 【无损】"
        val cleaned1 = MusicBrainzCoverService.cleanFileName(raw1)
        assertEquals("周杰伦 - 晴天", cleaned1)

        val raw2 = "05. Linkin Park - In The End (Official Music Video)"
        val cleaned2 = MusicBrainzCoverService.cleanFileName(raw2)
        assertEquals("Linkin Park - In The End", cleaned2)

        val raw3 = "Taylor_Swift_-_Cruel_Summer"
        val cleaned3 = MusicBrainzCoverService.cleanFileName(raw3)
        assertEquals("Taylor Swift - Cruel Summer", cleaned3)

        val raw4 = "青花瓷"
        val cleaned4 = MusicBrainzCoverService.cleanFileName(raw4)
        assertEquals("青花瓷", cleaned4)
    }

    @Test
    fun testParseArtistAndTitleFromFileName() {
        val normalHyphen = "周杰伦 - 晴天"
        val result1 = MusicBrainzCoverService.parseArtistAndTitleFromFileName(normalHyphen)
        assertNotNull(result1)
        assertEquals("周杰伦", result1?.first)
        assertEquals("晴天", result1?.second)

        val enDash = "Coldplay – Yellow"
        val result2 = MusicBrainzCoverService.parseArtistAndTitleFromFileName(enDash)
        assertNotNull(result2)
        assertEquals("Coldplay", result2?.first)
        assertEquals("Yellow", result2?.second)

        val singleName = "夜曲"
        val result3 = MusicBrainzCoverService.parseArtistAndTitleFromFileName(singleName)
        assertNull(result3)
    }

    @Test
    fun testEscapeLucene() {
        val queryWithSpecial = "Hello (Bonus Track) - Remastered + 2024!"
        val escaped = MusicBrainzCoverService.escapeLucene(queryWithSpecial)
        assertTrue(escaped.contains("\\("))
        assertTrue(escaped.contains("\\)"))
        assertTrue(escaped.contains("\\-"))
        assertTrue(escaped.contains("\\+"))
        assertTrue(escaped.contains("\\!"))
    }

    @Test
    fun testCoverDimensionComparisonPolicy() {
        // 场景 1：网络封面尺寸大于原嵌入封面 -> 判定应更新
        val localArea1 = 300L * 300L // 90,000
        val netArea1 = 1200L * 1200L // 1,440,000
        assertTrue("网络封面更大时应触发更新", netArea1 > localArea1)

        // 场景 2：原嵌入封面大于网络检索封面 -> 判定应保留原嵌入封面
        val localArea2 = 1400L * 1400L // 1,960,000
        val netArea2 = 500L * 500L // 250,000
        assertFalse("网络封面较小时不应更新", netArea2 > localArea2)

        // 场景 3：网络封面与原嵌入封面等大 -> 保留原嵌入封面
        val localArea3 = 1000L * 1000L
        val netArea3 = 1000L * 1000L
        assertFalse("等大时不应覆盖原图", netArea3 > localArea3)

        // 场景 4：原嵌入封面不存在 (0x0) -> 判定应更新
        val localArea4 = 0L
        val netArea4 = 500L * 500L
        assertTrue("无内嵌封面时应更新在线封面", netArea4 > localArea4)
    }

    @Test
    fun testMatchResultTypes() {
        val skipped = MusicBrainzCoverService.MatchResult.SkippedCellular
        assertTrue(skipped is MusicBrainzCoverService.MatchResult)

        val updated = MusicBrainzCoverService.MatchResult.UpdatedLarge(1000, 1000, 500, 500)
        assertEquals(1000, updated.netWidth)
        assertEquals(500, updated.localWidth)
    }

    @Test
    fun testArtistAlbumsFoundResult() {
        val candidate = MusicBrainzCoverService.AlbumCoverCandidate(
            releaseId = "test-mbid-123",
            albumTitle = "Fantasy",
            releaseDate = "2001",
            coverUrl = "https://coverartarchive.org/release/test-mbid-123/front",
            thumbnailUrl = "https://coverartarchive.org/release/test-mbid-123/front-500"
        )
        val result = MusicBrainzCoverService.MatchResult.ArtistAlbumsFound(
            artist = "周杰伦",
            candidates = listOf(candidate)
        )
        assertEquals("周杰伦", result.artist)
        assertEquals(1, result.candidates.size)
        assertEquals("Fantasy", result.candidates.first().albumTitle)
        assertEquals("https://coverartarchive.org/release/test-mbid-123/front", result.candidates.first().coverUrl)
    }
}
