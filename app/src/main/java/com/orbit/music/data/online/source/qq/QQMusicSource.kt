package com.orbit.music.data.online.source.qq

import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.source.IOnlineMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * QQ 音乐音源实现类
 */
class QQMusicSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : IOnlineMusicSource {

    override val platform: OnlinePlatform = OnlinePlatform.QQ

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 向 QQ 音乐 musicu.fcg 发送通用网关请求
     */
    private suspend fun postMusicU(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = payload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", userAgent)
            .header("Referer", "https://y.qq.com/")
            .header("Host", "u.y.qq.com")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw IllegalStateException("QQ 音乐网络响应为空")
        JSONObject(bodyStr)
    }

    override suspend fun getTags(): List<OnlinePlaylistTag> = withContext(Dispatchers.IO) {
        // QQ 音乐分类标签预设与兜底
        listOf(
            OnlinePlaylistTag("10000000", "全部", "默认"),
            OnlinePlaylistTag("165", "流行", "风格"),
            OnlinePlaylistTag("166", "摇滚", "风格"),
            OnlinePlaylistTag("167", "民谣", "风格"),
            OnlinePlaylistTag("168", "电子", "风格"),
            OnlinePlaylistTag("169", "说唱", "风格"),
            OnlinePlaylistTag("170", "轻音乐", "风格"),
            OnlinePlaylistTag("171", "爵士", "风格"),
            OnlinePlaylistTag("172", "古典", "风格"),
            OnlinePlaylistTag("173", "中国风", "风格"),
            OnlinePlaylistTag("174", "古风", "风格"),
            OnlinePlaylistTag("175", "ACG", "风格"),
            OnlinePlaylistTag("176", "影视原声", "主题"),
            OnlinePlaylistTag("177", "治愈", "心情"),
            OnlinePlaylistTag("178", "夜晚", "场景"),
            OnlinePlaylistTag("179", "运动", "场景")
        )
    }

    override suspend fun getPlaylists(tagId: String, page: Int, pageSize: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val categoryId = if (tagId.isEmpty() || tagId == "全部") 10000000 else tagId.toIntOrNull() ?: 10000000
        val sin = (page - 1) * pageSize
        val ein = sin + pageSize - 1

        val url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg" +
                "?sin=$sin&ein=$ein&categoryId=$categoryId&sortId=5&format=json&inCharset=utf8&outCharset=utf-8"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://y.qq.com/")
            .header("Host", "c.y.qq.com")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: return@withContext emptyList()
        val root = JSONObject(bodyStr)
        val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
        val listArr = dataObj.optJSONArray("list") ?: JSONArray()
        val result = mutableListOf<OnlinePlaylist>()

        for (i in 0 until listArr.length()) {
            val item = listArr.optJSONObject(i) ?: continue
            val dissid = item.optString("dissid")
            val dissname = item.optString("dissname")
            val imgurl = item.optString("imgurl")
            val listennum = item.optLong("listennum")
            val creatorObj = item.optJSONObject("creator")
            val creatorName = creatorObj?.optString("name")
            val creatorAvatar = creatorObj?.optString("avatarUrl")
            val songNum = item.optInt("song_count")

            result.add(
                OnlinePlaylist(
                    id = dissid,
                    platform = OnlinePlatform.QQ,
                    title = dissname,
                    coverUrl = imgurl,
                    playCount = listennum,
                    trackCount = songNum,
                    creatorName = creatorName,
                    creatorAvatarUrl = creatorAvatar,
                    description = item.optString("introduction")
                )
            )
        }
        result
    }

    override suspend fun getLeaderboards(): List<OnlineLeaderboard> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            val req = JSONObject().apply {
                put("module", "musicToplist.ToplistInfoServer")
                put("method", "GetAll")
                put("param", JSONObject())
            }
            put("req_0", req)
        }

        val root = postMusicU(payload)
        val req0 = root.optJSONObject("req_0") ?: return@withContext emptyList()
        val dataObj = req0.optJSONObject("data") ?: return@withContext emptyList()
        val groupArr = dataObj.optJSONArray("group") ?: JSONArray()
        val result = mutableListOf<OnlineLeaderboard>()

        for (i in 0 until groupArr.length()) {
            val group = groupArr.optJSONObject(i) ?: continue
            val toplistArr = group.optJSONArray("toplist") ?: continue
            for (j in 0 until toplistArr.length()) {
                val item = toplistArr.optJSONObject(j) ?: continue
                val topId = item.optInt("topId").toString()
                val title = item.optString("title")
                val picUrl = item.optString("headPicUrl").ifEmpty { item.optString("frontPicUrl") }
                val update = item.optString("updateTime").ifEmpty { item.optString("period") }
                val songArr = item.optJSONArray("song")
                val previews = mutableListOf<String>()
                if (songArr != null) {
                    for (k in 0 until songArr.length()) {
                        val s = songArr.optJSONObject(k) ?: continue
                        val sTitle = s.optString("title")
                        val singer = s.optString("singerName")
                        if (sTitle.isNotEmpty()) {
                            previews.add("$sTitle - $singer")
                        }
                    }
                }

                result.add(
                    OnlineLeaderboard(
                        id = topId,
                        platform = OnlinePlatform.QQ,
                        title = title,
                        coverUrl = picUrl,
                        updateFrequency = update,
                        topSongsPreview = previews
                    )
                )
            }
        }
        result
    }

    override suspend fun getPlaylistDetail(playlistId: String): Pair<OnlinePlaylist, List<OnlineSongItem>> = withContext(Dispatchers.IO) {
        val numericId = playlistId.removePrefix("top_").toIntOrNull()
        // QQ 音乐官方排行榜 ID 通常为较小的数字 (如 4, 26, 27, 62 等)，普通歌单 disstid 通常为 10 位数
        if (playlistId.startsWith("top_") || (numericId != null && numericId < 100000)) {
            try {
                return@withContext getLeaderboardDetailInternal(numericId ?: playlistId.toInt())
            } catch (e: Exception) {
                // 若排行榜解析失败，继续尝试普通歌单
            }
        }

        try {
            val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
                    "?type=1&json=1&utf8=1&onlysong=0&disstid=$playlistId&format=json&inCharset=utf8&outCharset=utf-8"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "https://y.qq.com/n/ryqq/playlist/$playlistId")
                .header("Cookie", "uin=0; qm_keyst=;")
                .header("Host", "c.y.qq.com")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: throw IllegalStateException("QQ 歌单详情响应为空")
            val root = JSONObject(bodyStr)
            val cdlistArr = root.optJSONArray("cdlist")
            if (cdlistArr == null || cdlistArr.length() == 0) {
                // 若旧版 qzone 接口未能返回 cdlist，自动降级切换至 musicu.fcg 网关接口
                return@withContext getPlaylistDetailViaMusicU(playlistId)
            }
            val cdObj = cdlistArr.optJSONObject(0) ?: return@withContext getPlaylistDetailViaMusicU(playlistId)

            val dissid = cdObj.optString("disstid")
            val dissname = cdObj.optString("dissname")
            val logo = cdObj.optString("logo")
            val desc = cdObj.optString("desc")
            val visitnum = cdObj.optLong("visitnum")
            val nickname = cdObj.optString("nickname")
            val headurl = cdObj.optString("headurl")
            val totalSongNum = cdObj.optInt("total_song_num")

            val playlist = OnlinePlaylist(
                id = dissid,
                platform = OnlinePlatform.QQ,
                title = dissname,
                coverUrl = logo,
                playCount = visitnum,
                trackCount = totalSongNum,
                creatorName = nickname,
                creatorAvatarUrl = headurl,
                description = desc
            )

            val songlistArr = cdObj.optJSONArray("songlist") ?: JSONArray()
            val songs = mutableListOf<OnlineSongItem>()

            for (i in 0 until songlistArr.length()) {
                val songObj = songlistArr.optJSONObject(i) ?: continue
                val songMid = songObj.optString("songmid")
                val songName = songObj.optString("songname")
                val interval = songObj.optLong("interval") * 1000 // 转换为毫秒
                val albumMid = songObj.optString("albummid")
                val albumName = songObj.optString("albumname")
                val pay = songObj.optJSONObject("pay")
                val payPlay = pay?.optInt("payplay") ?: 0
                val isVip = payPlay != 0

                // 歌手列表
                val singerArr = songObj.optJSONArray("singer")
                val singers = mutableListOf<String>()
                if (singerArr != null) {
                    for (s in 0 until singerArr.length()) {
                        val sObj = singerArr.optJSONObject(s)
                        val sName = sObj?.optString("name")
                        if (!sName.isNullOrEmpty()) singers.add(sName)
                    }
                }
                val singerStr = if (singers.isEmpty()) "未知歌手" else singers.joinToString(", ")

                // 专辑封面 (QQ音乐标准专辑封面 URL 模版)
                val coverUrl = if (albumMid.isNotEmpty()) {
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg"
                } else {
                    null
                }

                songs.add(
                    OnlineSongItem(
                        id = songMid,
                        platform = OnlinePlatform.QQ,
                        title = songName,
                        artist = singerStr,
                        album = albumName,
                        durationMs = interval,
                        coverUrl = coverUrl,
                        isVip = isVip
                    )
                )
            }

            Pair(playlist, songs)
        } catch (e: Exception) {
            try {
                getPlaylistDetailViaMusicU(playlistId)
            } catch (e2: Exception) {
                if (numericId != null) {
                    getLeaderboardDetailInternal(numericId)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * 通过 QQ 音乐官方 musicu.fcg 网关获取歌单详情 (支持全量与现代歌单)
     */
    private suspend fun getPlaylistDetailViaMusicU(playlistId: String): Pair<OnlinePlaylist, List<OnlineSongItem>> = withContext(Dispatchers.IO) {
        val dissIdNum = playlistId.toLongOrNull() ?: throw IllegalArgumentException("非法歌单 ID: $playlistId")
        val payload = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("cv", 4747474)
                put("ct", 24)
                put("format", "json")
                put("inCharset", "utf-8")
                put("outCharset", "utf-8")
                put("notice", 0)
                put("platform", "yqq.json")
                put("needNewCode", 1)
            })
            put("req_0", JSONObject().apply {
                put("module", "music.srfDissInfo.aiDissInfo")
                put("method", "uniform_get_Dissinfo")
                put("param", JSONObject().apply {
                    put("disstid", dissIdNum)
                    put("userinfo", 1)
                    put("tag", 1)
                    put("order", 1)
                })
            })
        }

        val root = postMusicU(payload)
        val req0 = root.optJSONObject("req_0") ?: throw IllegalStateException("未能解析到 QQ 网关数据")
        val dataObj = req0.optJSONObject("data") ?: throw IllegalStateException("QQ 歌单网关响应为空")
        val dirinfo = dataObj.optJSONObject("dirinfo") ?: dataObj

        val title = dirinfo.optString("title").ifEmpty { dirinfo.optString("dissname") }.ifEmpty { "QQ 音乐精选歌单" }
        val logo = dirinfo.optString("picurl").ifEmpty { dirinfo.optString("logo") }
        val desc = dirinfo.optString("desc")
        val listenNum = dirinfo.optLong("listennum")
        val nickname = dirinfo.optString("nickname")
        val totalSongNum = dirinfo.optInt("songnum")

        val playlist = OnlinePlaylist(
            id = playlistId,
            platform = OnlinePlatform.QQ,
            title = title,
            coverUrl = logo,
            playCount = listenNum,
            trackCount = totalSongNum,
            creatorName = nickname,
            creatorAvatarUrl = null,
            description = desc
        )

        val songlistArr = dataObj.optJSONArray("songlist") ?: JSONArray()
        val songs = mutableListOf<OnlineSongItem>()

        for (i in 0 until songlistArr.length()) {
            val songObj = songlistArr.optJSONObject(i) ?: continue
            val songMid = songObj.optString("mid")
                .ifEmpty { songObj.optString("songmid") }
                .ifEmpty { songObj.optLong("id").toString() }
            val songName = songObj.optString("name")
                .ifEmpty { songObj.optString("title") }
                .ifEmpty { songObj.optString("songname") }
            val interval = songObj.optLong("interval") * 1000L

            val albumObj = songObj.optJSONObject("album")
            val albumMid = albumObj?.optString("mid") ?: songObj.optString("albummid")
            val albumName = albumObj?.optString("name") ?: songObj.optString("albumname")

            val singerNameField = songObj.optString("singerName").ifEmpty { songObj.optString("singer_name") }
            val singerArr = songObj.optJSONArray("singer")
            val singers = mutableListOf<String>()
            if (singerNameField.isNotEmpty()) {
                singers.add(singerNameField)
            } else if (singerArr != null) {
                for (s in 0 until singerArr.length()) {
                    val sObj = singerArr.optJSONObject(s)
                    val sName = sObj?.optString("name") ?: sObj?.optString("title")
                    if (!sName.isNullOrEmpty()) singers.add(sName)
                }
            }
            val singerStr = if (singers.isEmpty()) "未知歌手" else singers.joinToString(", ")

            val cover = songObj.optString("cover")
            val coverUrl = if (cover.isNotEmpty()) {
                cover
            } else if (albumMid.isNotEmpty()) {
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg"
            } else {
                null
            }

            val pay = songObj.optJSONObject("pay")
            val payPlay = pay?.optInt("pay_play") ?: pay?.optInt("payplay") ?: 0
            val isVip = payPlay != 0

            songs.add(
                OnlineSongItem(
                    id = songMid,
                    platform = OnlinePlatform.QQ,
                    title = songName,
                    artist = singerStr,
                    album = albumName,
                    durationMs = interval,
                    coverUrl = coverUrl,
                    isVip = isVip
                )
            )
        }

        Pair(playlist, songs)
    }

    private suspend fun getLeaderboardDetailInternal(topId: Int): Pair<OnlinePlaylist, List<OnlineSongItem>> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("cv", 4747474)
                put("ct", 24)
                put("format", "json")
                put("inCharset", "utf-8")
                put("outCharset", "utf-8")
                put("notice", 0)
                put("platform", "yqq.json")
                put("needNewCode", 1)
            })
            put("req_0", JSONObject().apply {
                put("module", "musicToplist.ToplistInfoServer")
                put("method", "GetDetail")
                put("param", JSONObject().apply {
                    put("topId", topId)
                    put("offset", 0)
                    put("num", 100)
                    put("period", "")
                })
            })
        }

        val root = postMusicU(payload)
        val req0 = root.optJSONObject("req_0") ?: throw IllegalStateException("未能解析到 QQ 榜单数据")
        val dataObj = req0.optJSONObject("data") ?: throw IllegalStateException("QQ 榜单数据为空")
        val dataData = dataObj.optJSONObject("data") ?: dataObj

        val title = dataData.optString("title").ifEmpty { "QQ 音乐排行榜" }
        val headPic = dataData.optString("headPicUrl").ifEmpty { dataData.optString("frontPicUrl") }
        val desc = dataData.optString("intro").ifEmpty { dataData.optString("period") }
        val totalNum = dataData.optInt("totalNum")

        println("QQ Leaderboard detail data keys: ${dataData.keys().asSequence().toList()}")

        val playlist = OnlinePlaylist(
            id = topId.toString(),
            platform = OnlinePlatform.QQ,
            title = title,
            coverUrl = headPic,
            playCount = 0L,
            trackCount = if (totalNum > 0) totalNum else 100,
            creatorName = "QQ 音乐官方",
            creatorAvatarUrl = null,
            description = desc
        )

        val songInfoList = dataData.optJSONArray("songInfoList")
            ?: dataData.optJSONArray("song_list")
            ?: dataData.optJSONArray("songList")
            ?: dataData.optJSONArray("list")
            ?: dataData.optJSONArray("song")
            ?: JSONArray()
        val songs = mutableListOf<OnlineSongItem>()

        for (i in 0 until songInfoList.length()) {
            val songObj = songInfoList.optJSONObject(i) ?: continue
            val songMid = songObj.optString("songMid")
                .ifEmpty { songObj.optString("mid") }
                .ifEmpty { songObj.optLong("songId").toString() }
            val songName = songObj.optString("title")
                .ifEmpty { songObj.optString("name") }
            val interval = songObj.optLong("interval") * 1000L

            val albumObj = songObj.optJSONObject("album")
            val albumMid = songObj.optString("albumMid")
                .ifEmpty { albumObj?.optString("mid") ?: "" }
            val albumName = songObj.optString("albumName")
                .ifEmpty { albumObj?.optString("name") ?: "" }

            val singerNameField = songObj.optString("singerName")
                .ifEmpty { songObj.optString("singer_name") }
            val singerArr = songObj.optJSONArray("singer")
            val singers = mutableListOf<String>()
            if (singerNameField.isNotEmpty()) {
                singers.add(singerNameField)
            } else if (singerArr != null) {
                for (s in 0 until singerArr.length()) {
                    val sObj = singerArr.optJSONObject(s)
                    val sName = sObj?.optString("name") ?: sObj?.optString("title")
                    if (!sName.isNullOrEmpty()) singers.add(sName)
                }
            }
            val singerStr = if (singers.isEmpty()) "未知歌手" else singers.joinToString(", ")

            val cover = songObj.optString("cover")
            val coverUrl = if (cover.isNotEmpty()) {
                cover
            } else if (albumMid.isNotEmpty()) {
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg"
            } else {
                null
            }

            val pay = songObj.optJSONObject("pay")
            val payPlay = pay?.optInt("pay_play") ?: 0
            val isVip = payPlay != 0

            songs.add(
                OnlineSongItem(
                    id = songMid,
                    platform = OnlinePlatform.QQ,
                    title = songName,
                    artist = singerStr,
                    album = albumName,
                    durationMs = interval,
                    coverUrl = coverUrl,
                    isVip = isVip
                )
            )
        }

        Pair(playlist, songs)
    }

    override suspend fun searchPlaylists(keyword: String, page: Int, pageSize: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val pageNo = page - 1
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist" +
                "?page_no=$pageNo&num_per_page=$pageSize&format=json&inCharset=utf8&outCharset=utf-8&query=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://y.qq.com/")
            .header("Host", "c.y.qq.com")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: return@withContext emptyList()
        val root = JSONObject(bodyStr)
        val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
        val listArr = dataObj.optJSONArray("list") ?: JSONArray()
        val list = mutableListOf<OnlinePlaylist>()

        for (i in 0 until listArr.length()) {
            val item = listArr.optJSONObject(i) ?: continue
            val dissid = item.optString("dissid")
            val dissname = item.optString("dissname")
            val imgurl = item.optString("imgurl")
            val listennum = item.optLong("listennum")
            val creatorObj = item.optJSONObject("creator")
            val creatorName = creatorObj?.optString("name")
            val songCount = item.optInt("song_count")

            list.add(
                OnlinePlaylist(
                    id = dissid,
                    platform = OnlinePlatform.QQ,
                    title = dissname,
                    coverUrl = imgurl,
                    playCount = listennum,
                    trackCount = songCount,
                    creatorName = creatorName
                )
            )
        }
        list
    }

    override fun extractPlaylistId(urlOrText: String): String? {
        val trimmed = urlOrText.trim()
        // 纯数字 ID
        if (trimmed.matches(Regex("^\\d+$"))) {
            return trimmed
        }
        // 匹配 id=123456 或 playlist/123456 或 taoge.html?id=123456
        val matcher = Pattern.compile("(?:id=|playlist/|disstid=)(\\d+)").matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }
}
