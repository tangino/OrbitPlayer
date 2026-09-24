package com.orbit.music.data.online

import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.repository.OnlineMusicRepository
import com.orbit.music.data.online.source.netease.NeteaseCrypto
import com.orbit.music.data.online.source.netease.NeteaseMusicSource
import com.orbit.music.data.online.source.qq.QQMusicSource
import org.junit.Assert.*
import org.junit.Test

class OnlineMusicSourceTest {

    @Test
    fun testNeteaseCryptoWeapi() {
        val payload = """{"cat":"全部","limit":30,"offset":0,"csrf_token":""}"""
        val (params, encSecKey) = NeteaseCrypto.weapi(payload)

        assertNotNull(params)
        assertNotNull(encSecKey)
        assertTrue("params 应当是非空 Base64 字符串", params.isNotEmpty())
        assertEquals("encSecKey 长度应当为 256 位十六进制字符", 256, encSecKey.length)
    }

    @Test
    fun testNeteaseExtractPlaylistId() {
        val source = NeteaseMusicSource()

        val id1 = source.extractPlaylistId("https://music.163.com/#/playlist?id=3778678")
        assertEquals("3778678", id1)

        val id2 = source.extractPlaylistId("https://music.163.com/playlist/758493021")
        assertEquals("758493021", id2)

        val id3 = source.extractPlaylistId("24381029")
        assertEquals("24381029", id3)
    }

    @Test
    fun testQQMusicExtractPlaylistId() {
        val source = QQMusicSource()

        val id1 = source.extractPlaylistId("https://y.qq.com/n/ryqq/playlist/7098492012")
        assertEquals("7098492012", id1)

        val id2 = source.extractPlaylistId("https://y.qq.com/m/act/sf/index.html?disstid=8839201923")
        assertEquals("8839201923", id2)

        val id3 = source.extractPlaylistId("8839201923")
        assertEquals("8839201923", id3)
    }

    @Test
    fun testRepositoryParseLinkOrText() {
        val repo = OnlineMusicRepository.getInstance()

        val parsedNetease = repo.parseLinkOrText("https://music.163.com/#/playlist?id=3778678")
        assertNotNull(parsedNetease)
        val parsedQQ = repo.parseLinkOrText("https://y.qq.com/n/ryqq/playlist/7098492012")
        assertNotNull(parsedQQ)
        assertEquals(OnlinePlatform.QQ, parsedQQ?.first)
        assertEquals("7098492012", parsedQQ?.second)
    }

    @Test
    fun testRealNetworkFetchNetease() {
        val source = NeteaseMusicSource()
        try {
            val playlists = kotlinx.coroutines.runBlocking { source.getPlaylists("全部", 1, 10) }
            println("Netease playlists size: ${playlists.size}")
            assertTrue("网易云歌单应不为空", playlists.isNotEmpty())
        } catch (e: Exception) {
            println("Netease fetch error: ${e.message}")
            e.printStackTrace()
            fail(e.message)
        }
    }

    @Test
    fun testRealNetworkFetchQQ() {
        val source = QQMusicSource()
        try {
            val playlists = kotlinx.coroutines.runBlocking { source.getPlaylists("全部", 1, 10) }
            println("QQ playlists size: ${playlists.size}")
            assertTrue("QQ歌单应不为空", playlists.isNotEmpty())
        } catch (e: Exception) {
            println("QQ fetch error: ${e.message}")
            e.printStackTrace()
            fail(e.message)
        }
    }

    @Test
    fun testQQPlaylistDetailSearchAndFetch() {
        val source = QQMusicSource()
        val list = kotlinx.coroutines.runBlocking {
            source.getPlaylists("全部", 1, 5)
        }
        println("Fetched playlists count: ${list.size}")
        assertTrue(list.isNotEmpty())
        list.forEach { p ->
            println("\nTesting playlist: ${p.title} (id=${p.id})")
            try {
                val (detail, songs) = kotlinx.coroutines.runBlocking {
                    source.getPlaylistDetail(p.id)
                }
                println("Success: ${detail.title}, songs: ${songs.size}, first: ${songs.firstOrNull()?.title} - ${songs.firstOrNull()?.artist}")
            } catch (e: Exception) {
                println("Failed for ${p.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    @Test
    fun testNeteasePlaylistDetailFetch() {
        val source = NeteaseMusicSource()
        try {
            // 测试热歌榜 3778678
            val (playlist, songs) = kotlinx.coroutines.runBlocking {
                source.getPlaylistDetail("3778678")
            }
            println("Netease playlist: ${playlist.title}, songs size: ${songs.size}")
            assertTrue("网易云热歌榜歌曲数量应大于10首", songs.size > 10)
        } catch (e: Exception) {
            println("Netease detail error: ${e.message}")
            e.printStackTrace()
            fail(e.message)
        }
    }

    @Test
    fun testKuwoPlaylistDetailFetch() {
        val source = com.orbit.music.data.online.source.kuwo.KuwoMusicSource()
        try {
            val (playlist, songs) = kotlinx.coroutines.runBlocking {
                source.getPlaylistDetail("3677105457")
            }
            println("Kuwo playlist: ${playlist.title}, total trackCount: ${playlist.trackCount}, songs size: ${songs.size}")
            assertTrue("酷我歌单歌曲数量应大于100首", songs.size > 100)
            assertTrue("歌单歌曲列表应非空", songs.isNotEmpty())
            println("First song: ${songs.firstOrNull()?.title} - ${songs.firstOrNull()?.artist}")
        } catch (e: Exception) {
            println("Kuwo detail error: ${e.message}")
            e.printStackTrace()
            fail(e.message)
        }
    }

    @Test
    fun testMiguPlaylistAndSongFetch() {
        val source = com.orbit.music.data.online.source.migu.MiguMusicSource()
        try {
            val (playlist, songs) = kotlinx.coroutines.runBlocking {
                source.getPlaylistDetail("203413794")
            }
            println("Migu playlist: ${playlist.title}, trackCount: ${playlist.trackCount}, songs size: ${songs.size}")
            assertTrue("咪咕歌单歌曲总数应大于50首", songs.size > 50)
            val pianAi = songs.firstOrNull { it.title.contains("偏爱") }
            assertNotNull("歌单中应包含《偏爱》", pianAi)
            println("Found song: ${pianAi?.title} (id=${pianAi?.id})")
        } catch (e: Exception) {
            println("Migu detail error: ${e.message}")
            e.printStackTrace()
            fail(e.message)
        }
    }
}
