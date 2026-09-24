package com.orbit.music.data.online.source.netease

import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.source.IOnlineMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 网易云音乐音源实现类
 */
class NeteaseMusicSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : IOnlineMusicSource {

    override val platform: OnlinePlatform = OnlinePlatform.NETEASE

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val defaultCookie = "os=pc; appver=2.9.7; osver=Microsoft-Windows-10; NMTID=00O_orbit_player;"

    /**
     * 发送 GET 请求
     */
    private suspend fun getApi(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://music.163.com/")
            .header("Host", "music.163.com")
            .header("Cookie", defaultCookie)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw IllegalStateException("网易云网络响应为空")
        JSONObject(bodyStr)
    }

    /**
     * 发送 POST 请求
     */
    private suspend fun postApi(url: String, formBody: FormBody): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://music.163.com/")
            .header("Host", "music.163.com")
            .header("Cookie", defaultCookie)
            .post(formBody)
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw IllegalStateException("网易云网络响应为空")
        JSONObject(bodyStr)
    }

    override suspend fun getTags(): List<OnlinePlaylistTag> = withContext(Dispatchers.IO) {
        try {
            val root = getApi("https://music.163.com/api/playlist/catalogue")
            val subArr = root.optJSONArray("sub")
            val tags = mutableListOf<OnlinePlaylistTag>()
            tags.add(OnlinePlaylistTag("全部", "全部", "默认"))
            tags.add(OnlinePlaylistTag("华语", "华语", "语种"))
            tags.add(OnlinePlaylistTag("欧美", "欧美", "语种"))
            tags.add(OnlinePlaylistTag("流行", "流行", "风格"))
            tags.add(OnlinePlaylistTag("摇滚", "摇滚", "风格"))
            tags.add(OnlinePlaylistTag("民谣", "民谣", "风格"))
            tags.add(OnlinePlaylistTag("电子", "电子", "风格"))
            tags.add(OnlinePlaylistTag("ACG", "ACG", "风格"))
            tags.add(OnlinePlaylistTag("治愈", "治愈", "情感"))

            if (subArr != null) {
                for (i in 0 until subArr.length()) {
                    val obj = subArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val cat = obj.optString("category")
                    if (name.isNotEmpty() && tags.none { it.name == name }) {
                        tags.add(OnlinePlaylistTag(name, name, cat))
                    }
                }
            }
            tags
        } catch (e: Exception) {
            // 兜底常用标签
            listOf(
                OnlinePlaylistTag("全部", "全部"),
                OnlinePlaylistTag("华语", "华语"),
                OnlinePlaylistTag("欧美", "欧美"),
                OnlinePlaylistTag("流行", "流行"),
                OnlinePlaylistTag("摇滚", "摇滚"),
                OnlinePlaylistTag("民谣", "民谣"),
                OnlinePlaylistTag("电子", "电子"),
                OnlinePlaylistTag("ACG", "ACG"),
                OnlinePlaylistTag("治愈", "治愈"),
                OnlinePlaylistTag("夜晚", "夜晚")
            )
        }
    }

    override suspend fun getPlaylists(tagId: String, page: Int, pageSize: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * pageSize
        val encodedCat = URLEncoder.encode(if (tagId.isEmpty()) "全部" else tagId, "UTF-8")
        val url = "https://music.163.com/api/playlist/list?cat=$encodedCat&order=hot&offset=$offset&limit=$pageSize&total=true"
        val root = getApi(url)
        val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
        val result = mutableListOf<OnlinePlaylist>()

        for (i in 0 until playlistsArr.length()) {
            val item = playlistsArr.optJSONObject(i) ?: continue
            val id = item.optLong("id").toString()
            val name = item.optString("name")
            var cover = item.optString("coverImgUrl")
            if (cover.isNotEmpty() && !cover.contains("?param=")) {
                cover = "$cover?param=300y300"
            }
            val playCount = item.optLong("playCount")
            val trackCount = item.optInt("trackCount")
            val creatorObj = item.optJSONObject("creator")
            val creatorName = creatorObj?.optString("nickname")
            val creatorAvatar = creatorObj?.optString("avatarUrl")
            val desc = item.optString("description")

            result.add(
                OnlinePlaylist(
                    id = id,
                    platform = OnlinePlatform.NETEASE,
                    title = name,
                    coverUrl = cover,
                    playCount = playCount,
                    trackCount = trackCount,
                    creatorName = creatorName,
                    creatorAvatarUrl = creatorAvatar,
                    description = desc
                )
            )
        }
        result
    }

    override suspend fun getLeaderboards(): List<OnlineLeaderboard> = withContext(Dispatchers.IO) {
        val root = getApi("https://music.163.com/api/toplist")
        val listArr = root.optJSONArray("list") ?: JSONArray()
        val result = mutableListOf<OnlineLeaderboard>()

        for (i in 0 until listArr.length()) {
            val item = listArr.optJSONObject(i) ?: continue
            val id = item.optLong("id").toString()
            val name = item.optString("name")
            var cover = item.optString("coverImgUrl")
            if (cover.isNotEmpty() && !cover.contains("?param=")) {
                cover = "$cover?param=300y300"
            }
            val updateFreq = item.optString("updateFrequency")
            val tracks = item.optJSONArray("tracks")
            val previews = mutableListOf<String>()
            if (tracks != null) {
                for (j in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(j) ?: continue
                    val first = t.optString("first")
                    val second = t.optString("second")
                    if (first.isNotEmpty()) {
                        previews.add("$first - $second")
                    }
                }
            }

            result.add(
                OnlineLeaderboard(
                    id = id,
                    platform = OnlinePlatform.NETEASE,
                    title = name,
                    coverUrl = cover,
                    updateFrequency = updateFreq,
                    topSongsPreview = previews
                )
            )
        }
        result
    }

    override suspend fun getPlaylistDetail(playlistId: String): Pair<OnlinePlaylist, List<OnlineSongItem>> = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/v1/playlist/detail?id=$playlistId"
        val root = getApi(url)
        val playlistObj = root.optJSONObject("playlist") ?: throw IllegalStateException("解析歌单详情失败")

        val id = playlistObj.optLong("id").toString()
        val title = playlistObj.optString("name")
        var cover = playlistObj.optString("coverImgUrl")
        if (cover.isNotEmpty() && !cover.contains("?param=")) {
            cover = "$cover?param=500y500"
        }
        val playCount = playlistObj.optLong("playCount")
        val trackCount = playlistObj.optInt("trackCount")
        val creatorObj = playlistObj.optJSONObject("creator")
        val creatorName = creatorObj?.optString("nickname")
        val creatorAvatar = creatorObj?.optString("avatarUrl")
        val desc = playlistObj.optString("description")

        val playlist = OnlinePlaylist(
            id = id,
            platform = OnlinePlatform.NETEASE,
            title = title,
            coverUrl = cover,
            playCount = playCount,
            trackCount = trackCount,
            creatorName = creatorName,
            creatorAvatarUrl = creatorAvatar,
            description = desc
        )

        val tracksArr = playlistObj.optJSONArray("tracks") ?: JSONArray()
        val songs = mutableListOf<OnlineSongItem>()

        for (i in 0 until tracksArr.length()) {
            val trackObj = tracksArr.optJSONObject(i) ?: continue
            val songId = trackObj.optLong("id").toString()
            val songName = trackObj.optString("name").ifEmpty { trackObj.optString("title") }
            val dt = trackObj.optLong("dt").let { if (it > 0) it else trackObj.optLong("duration") }
            val fee = trackObj.optInt("fee") // 1: vip, 4: 购买专辑等
            val isVip = fee == 1 || fee == 4

            // 艺术家: ar 或 artists
            val arArr = trackObj.optJSONArray("ar") ?: trackObj.optJSONArray("artists")
            val artists = mutableListOf<String>()
            if (arArr != null) {
                for (a in 0 until arArr.length()) {
                    val aObj = arArr.optJSONObject(a)
                    val aName = aObj?.optString("name") ?: aObj?.optString("title")
                    if (!aName.isNullOrEmpty()) artists.add(aName)
                }
            }
            val artistStr = if (artists.isEmpty()) "未知歌手" else artists.joinToString(", ")

            // 专辑: al 或 album
            val alObj = trackObj.optJSONObject("al") ?: trackObj.optJSONObject("album")
            val albumName = alObj?.optString("name") ?: "未知专辑"
            var picUrl = alObj?.optString("picUrl")
            if (!picUrl.isNullOrEmpty() && !picUrl.contains("?param=")) {
                picUrl = "$picUrl?param=300y300"
            }

            songs.add(
                OnlineSongItem(
                    id = songId,
                    platform = OnlinePlatform.NETEASE,
                    title = songName,
                    artist = artistStr,
                    album = albumName,
                    durationMs = dt,
                    coverUrl = picUrl,
                    isVip = isVip
                )
            )
        }

        Pair(playlist, songs)
    }

    override suspend fun searchPlaylists(keyword: String, page: Int, pageSize: Int): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * pageSize
        val formBody = FormBody.Builder()
            .add("s", keyword)
            .add("type", "1000") // 1000 代表歌单
            .add("offset", offset.toString())
            .add("limit", pageSize.toString())
            .add("total", "true")
            .build()

        val root = postApi("https://music.163.com/api/search/get/web?csrf_token=", formBody)
        val resultObj = root.optJSONObject("result") ?: return@withContext emptyList()
        val playlistsArr = resultObj.optJSONArray("playlists") ?: return@withContext emptyList()
        val list = mutableListOf<OnlinePlaylist>()

        for (i in 0 until playlistsArr.length()) {
            val item = playlistsArr.optJSONObject(i) ?: continue
            val id = item.optLong("id").toString()
            val name = item.optString("name")
            var cover = item.optString("coverImgUrl")
            if (cover.isNotEmpty() && !cover.contains("?param=")) {
                cover = "$cover?param=300y300"
            }
            val playCount = item.optLong("playCount")
            val trackCount = item.optInt("trackCount")
            val creatorObj = item.optJSONObject("creator")
            val creatorName = creatorObj?.optString("nickname")
            val desc = item.optString("description")

            list.add(
                OnlinePlaylist(
                    id = id,
                    platform = OnlinePlatform.NETEASE,
                    title = name,
                    coverUrl = cover,
                    playCount = playCount,
                    trackCount = trackCount,
                    creatorName = creatorName,
                    description = desc
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
        // 包含 id=123456
        val matcher = Pattern.compile("id=(\\d+)").matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        // 包含 playlist/123456
        val matcher2 = Pattern.compile("playlist/(\\d+)").matcher(trimmed)
        if (matcher2.find()) {
            return matcher2.group(1)
        }
        return null
    }
}
