package com.orbit.music.data.online.source.kugou

import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.source.IOnlineMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 酷狗音乐在线音源实现类
 */
class KugouMusicSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : IOnlineMusicSource {

    override val platform: OnlinePlatform = OnlinePlatform.KUGOU

    private val userAgent = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private suspend fun getApi(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "http://m.kugou.com/")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw IllegalStateException("酷狗网络响应为空")
        JSONObject(bodyStr)
    }

    override suspend fun getTags(): List<OnlinePlaylistTag> = withContext(Dispatchers.IO) {
        try {
            val root = getApi("http://mobilecdnbj.kugou.com/api/v5/tag/list?pid=0&apiver=55&showtype=3")
            val infoArr = root.optJSONObject("data")?.optJSONArray("info")
            val tags = mutableListOf<OnlinePlaylistTag>()
            tags.add(OnlinePlaylistTag("0", "推荐", "默认"))

            if (infoArr != null) {
                for (i in 0 until infoArr.length()) {
                    val obj = infoArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val id = obj.optString("id")
                    if (name.isNotEmpty() && tags.none { it.name == name }) {
                        tags.add(OnlinePlaylistTag(id, name, "分类"))
                    }
                }
            }
            if (tags.size <= 1) {
                listOf(
                    OnlinePlaylistTag("0", "推荐"),
                    OnlinePlaylistTag("153", "华语"),
                    OnlinePlaylistTag("154", "欧美"),
                    OnlinePlaylistTag("155", "日韩"),
                    OnlinePlaylistTag("156", "流行"),
                    OnlinePlaylistTag("157", "摇滚"),
                    OnlinePlaylistTag("158", "古风"),
                    OnlinePlaylistTag("159", "民谣"),
                    OnlinePlaylistTag("160", "ACG"),
                    OnlinePlaylistTag("161", "治愈")
                )
            } else tags
        } catch (e: Exception) {
            listOf(
                OnlinePlaylistTag("0", "推荐"),
                OnlinePlaylistTag("153", "华语"),
                OnlinePlaylistTag("154", "欧美"),
                OnlinePlaylistTag("155", "日韩"),
                OnlinePlaylistTag("156", "流行"),
                OnlinePlaylistTag("157", "摇滚"),
                OnlinePlaylistTag("158", "古风"),
                OnlinePlaylistTag("159", "民谣"),
                OnlinePlaylistTag("160", "ACG"),
                OnlinePlaylistTag("161", "治愈")
            )
        }
    }

    override suspend fun getPlaylists(
        tagId: String,
        page: Int,
        pageSize: Int
    ): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val url = if (tagId == "0" || tagId == "推荐" || tagId == "全部") {
            "http://m.kugou.com/plist/index&json=true&page=$page"
        } else {
            "http://m.kugou.com/plist/index&json=true&page=$page"
        }

        try {
            val root = getApi(url)
            val infoArr = root.optJSONObject("plist")?.optJSONObject("list")?.optJSONArray("info")
                ?: return@withContext emptyList()

            val list = mutableListOf<OnlinePlaylist>()
            for (i in 0 until infoArr.length()) {
                val obj = infoArr.optJSONObject(i) ?: continue
                val id = obj.optString("specialid")
                val title = obj.optString("specialname")
                var coverUrl = obj.optString("imgurl")
                if (coverUrl.contains("{size}")) {
                    coverUrl = coverUrl.replace("{size}", "400")
                }
                val playCount = obj.optLong("playcount", 0L)
                val trackCount = obj.optInt("songcount", 0)
                val creatorName = obj.optString("nickname").ifEmpty { obj.optString("user_name").ifEmpty { "酷狗音乐" } }

                list.add(
                    OnlinePlaylist(
                        id = id,
                        platform = platform,
                        title = title,
                        coverUrl = coverUrl,
                        playCount = playCount,
                        trackCount = trackCount,
                        creatorName = creatorName,
                        description = obj.optString("intro")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getLeaderboards(): List<OnlineLeaderboard> = withContext(Dispatchers.IO) {
        try {
            val root = getApi("http://mobilecdnbj.kugou.com/api/v5/rank/list")
            val infoArr = root.optJSONObject("data")?.optJSONArray("info") ?: return@withContext emptyList()

            val list = mutableListOf<OnlineLeaderboard>()
            for (i in 0 until infoArr.length()) {
                val obj = infoArr.optJSONObject(i) ?: continue
                val id = obj.optString("rankid")
                val title = obj.optString("rankname")
                var cover = obj.optString("imgurl")
                if (cover.contains("{size}")) {
                    cover = cover.replace("{size}", "400")
                }
                if (cover.isEmpty()) {
                    cover = obj.optString("banner7url").replace("{size}", "400")
                }

                val songsPreview = mutableListOf<String>()
                val songInfoArr = obj.optJSONArray("songinfo")
                if (songInfoArr != null) {
                    for (j in 0 until songInfoArr.length()) {
                        val sObj = songInfoArr.optJSONObject(j) ?: continue
                        val sName = sObj.optString("songname")
                        if (sName.isNotEmpty()) songsPreview.add(sName)
                    }
                }

                list.add(
                    OnlineLeaderboard(
                        id = id,
                        platform = platform,
                        title = title,
                        coverUrl = cover,
                        updateFrequency = obj.optString("update_frequency").ifEmpty { "每日更新" },
                        topSongsPreview = songsPreview
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPlaylistDetail(playlistId: String): Pair<OnlinePlaylist, List<OnlineSongItem>> =
        withContext(Dispatchers.IO) {
            // 如果是排行榜
            if (playlistId.length <= 5 && playlistId.toIntOrNull() != null) {
                return@withContext getRankDetail(playlistId)
            }

            // 1. 获取歌单元数据
            var title = "酷狗歌单"
            var coverUrl = ""
            var creatorName = "酷狗音乐"
            var playCount = 0L
            var description: String? = null

            try {
                val infoRoot = getApi("http://mobilecdnbj.kugou.com/api/v5/special/info?specialid=$playlistId")
                val infoData = infoRoot.optJSONObject("data")
                if (infoData != null) {
                    title = infoData.optString("specialname").ifEmpty { title }
                    coverUrl = infoData.optString("imgurl").replace("{size}", "400")
                    creatorName = infoData.optString("nickname").ifEmpty {
                        infoData.optString("singername").ifEmpty { creatorName }
                    }
                    playCount = infoData.optLong("playcount", 0L)
                    description = infoData.optString("intro")
                }
            } catch (_: Exception) {
            }

            // 2. 获取歌单歌曲列表（官方移动端专用 API，包含 union_cover 与专辑信息）
            val songs = mutableListOf<OnlineSongItem>()
            try {
                val songUrl = "http://mobilecdnbj.kugou.com/api/v5/special/song?specialid=$playlistId&page=1&pagesize=200&plat=0&version=9108"
                val root = getApi(songUrl)
                val infoArr = root.optJSONObject("data")?.optJSONArray("info")

                if (infoArr != null) {
                    for (i in 0 until infoArr.length()) {
                        val sObj = infoArr.optJSONObject(i) ?: continue
                        val songItem = parseKugouSongItem(sObj, coverUrl)
                        if (songItem != null) {
                            songs.add(songItem)
                        }
                    }
                }
            } catch (_: Exception) {
                // 备用网页版接口降级
                try {
                    val fallbackUrl = "https://m.kugou.com/plist/list/$playlistId?json=true"
                    val root = getApi(fallbackUrl)
                    val infoObj = root.optJSONObject("info")?.optJSONObject("list")
                    if (title == "酷狗歌单") {
                        title = infoObj?.optString("specialname") ?: title
                        coverUrl = infoObj?.optString("imgurl")?.replace("{size}", "400") ?: coverUrl
                        creatorName = infoObj?.optString("nickname") ?: creatorName
                    }
                    val songsArr = root.optJSONObject("list")?.optJSONObject("list")?.optJSONArray("info")
                    if (songsArr != null) {
                        for (i in 0 until songsArr.length()) {
                            val sObj = songsArr.optJSONObject(i) ?: continue
                            val songItem = parseKugouSongItem(sObj, coverUrl)
                            if (songItem != null) {
                                songs.add(songItem)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val playlist = OnlinePlaylist(
                id = playlistId,
                platform = platform,
                title = title,
                coverUrl = coverUrl.ifEmpty { songs.firstOrNull()?.coverUrl ?: "" },
                playCount = playCount,
                trackCount = songs.size,
                creatorName = creatorName,
                description = description
            )

            Pair(playlist, songs)
        }

    private suspend fun getRankDetail(rankId: String): Pair<OnlinePlaylist, List<OnlineSongItem>> =
        withContext(Dispatchers.IO) {
            val url = "http://mobilecdnbj.kugou.com/api/v5/rank/song?rankid=$rankId&page=1&pagesize=200"
            val root = getApi(url)
            val infoArr = root.optJSONObject("data")?.optJSONArray("info")

            val rankName = root.optJSONObject("data")?.optJSONObject("rank_info")?.optString("rankname") ?: "酷狗排行榜"
            var coverUrl = root.optJSONObject("data")?.optJSONObject("rank_info")?.optString("banner7url") ?: ""
            if (coverUrl.contains("{size}")) {
                coverUrl = coverUrl.replace("{size}", "400")
            }

            val songs = mutableListOf<OnlineSongItem>()
            if (infoArr != null) {
                for (i in 0 until infoArr.length()) {
                    val sObj = infoArr.optJSONObject(i) ?: continue
                    val songItem = parseKugouSongItem(sObj, coverUrl)
                    if (songItem != null) {
                        songs.add(songItem)
                    }
                }
            }

            val playlist = OnlinePlaylist(
                id = rankId,
                platform = platform,
                title = rankName,
                coverUrl = coverUrl,
                playCount = 0L,
                trackCount = songs.size,
                creatorName = "酷狗官方",
                description = "官方权威排行榜"
            )

            Pair(playlist, songs)
        }

    private fun parseKugouSongItem(sObj: JSONObject, defaultCover: String): OnlineSongItem? {
        val hash = sObj.optString("hash").ifEmpty { sObj.optString("320hash").ifEmpty { sObj.optString("sqhash") } }
        val audioId = sObj.optString("audio_id").ifEmpty { sObj.optString("album_audio_id") }
        val id = hash.ifEmpty { audioId }
        if (id.isEmpty()) return null

        val filename = sObj.optString("filename")
        val remark = sObj.optString("remark")

        // 歌手提取
        var singerName = sObj.optString("singername")
        if (singerName.isBlank()) {
            val authorsArr = sObj.optJSONArray("authors")
            if (authorsArr != null && authorsArr.length() > 0) {
                val names = mutableListOf<String>()
                for (j in 0 until authorsArr.length()) {
                    val aName = authorsArr.optJSONObject(j)?.optString("author_name")
                    if (!aName.isNullOrBlank()) names.add(aName.trim())
                }
                if (names.isNotEmpty()) singerName = names.joinToString(" & ")
            }
        }
        if (singerName.isBlank() && filename.contains("-")) {
            singerName = filename.substringBefore("-").trim()
        }
        if (singerName.isBlank()) singerName = "未知歌手"

        // 歌曲名提取
        var songName = sObj.optString("songname")
        if (songName.isBlank() && remark.isNotBlank()) {
            songName = remark
        }
        if (songName.isBlank() && filename.contains("-")) {
            songName = filename.substringAfter("-").trim()
        }
        if (songName.isBlank()) {
            songName = filename.ifEmpty { "酷狗单曲" }
        }

        // 专辑名提取
        var albumName = sObj.optString("album_name")
        if (albumName.isBlank()) {
            albumName = sObj.optJSONObject("album_info")?.optString("album_name") ?: ""
        }
        if (albumName.isBlank()) {
            albumName = sObj.optJSONObject("trans_param")?.optString("album_name") ?: ""
        }
        if (albumName.isBlank()) {
            albumName = if (remark.isNotBlank() && remark != songName) remark else "单曲"
        }

        // 歌曲专属封面提取
        var songCover = ""
        val unionCover = sObj.optJSONObject("trans_param")?.optString("union_cover")
        if (!unionCover.isNullOrBlank()) {
            songCover = unionCover.replace("{size}", "400")
        }
        if (songCover.isBlank()) {
            val albumCover = sObj.optString("album_sizable_cover")
            if (albumCover.isNotBlank()) {
                songCover = albumCover.replace("{size}", "400")
            }
        }
        if (songCover.isBlank()) {
            val sizableCover = sObj.optJSONObject("album_info")?.optString("sizable_cover")
            if (!sizableCover.isNullOrBlank()) {
                songCover = sizableCover.replace("{size}", "400")
            }
        }
        if (songCover.isBlank()) {
            val authorAvatar = sObj.optJSONArray("authors")?.optJSONObject(0)?.optString("sizable_avatar")
            if (!authorAvatar.isNullOrBlank()) {
                songCover = authorAvatar.replace("{size}", "400")
            }
        }
        if (songCover.isBlank()) {
            val img = sObj.optString("imgurl")
            if (img.isNotBlank()) {
                songCover = img.replace("{size}", "400")
            }
        }
        if (songCover.isBlank()) {
            songCover = defaultCover
        }

        val durationMs = sObj.optLong("duration", sObj.optLong("duration_high", 0L)) * 1000L
        val isVip = sObj.optInt("privilege", 0) > 0 ||
                sObj.optInt("320privilege", 0) == 10 ||
                sObj.optInt("pkg_price", 0) > 0 ||
                sObj.optInt("pay_type", 0) > 0

        return OnlineSongItem(
            id = id,
            platform = platform,
            title = songName,
            artist = singerName,
            album = albumName,
            durationMs = durationMs,
            coverUrl = songCover,
            isVip = isVip
        )
    }

    override suspend fun searchPlaylists(
        keyword: String,
        page: Int,
        pageSize: Int
    ): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "http://mobilecdn.kugou.com/api/v3/search/special?keyword=$encoded&page=$page&pagesize=$pageSize"
        try {
            val root = getApi(url)
            val infoArr = root.optJSONObject("data")?.optJSONArray("info") ?: return@withContext emptyList()

            val list = mutableListOf<OnlinePlaylist>()
            for (i in 0 until infoArr.length()) {
                val obj = infoArr.optJSONObject(i) ?: continue
                val id = obj.optString("specialid")
                val title = obj.optString("specialname")
                var cover = obj.optString("imgurl").replace("{size}", "400")
                val playCount = obj.optLong("playcount", 0L)
                val trackCount = obj.optInt("songcount", 0)

                list.add(
                    OnlinePlaylist(
                        id = id,
                        platform = platform,
                        title = title,
                        coverUrl = cover,
                        playCount = playCount,
                        trackCount = trackCount,
                        creatorName = obj.optString("nickname").ifEmpty { "酷狗用户" },
                        description = obj.optString("intro")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun extractPlaylistId(urlOrText: String): String? {
        val trimmed = urlOrText.trim()
        val matcher = Pattern.compile("(?:specialid=|plist/list/|special/|id=)(\\d+)").matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        if (trimmed.matches(Regex("^\\d{4,12}$"))) {
            return trimmed
        }
        return null
    }
}
