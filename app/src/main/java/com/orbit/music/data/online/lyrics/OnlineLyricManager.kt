package com.orbit.music.data.online.lyrics

import android.content.Context
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.orbit.music.data.model.Song
import com.orbit.music.utils.LyricLine
import com.orbit.music.utils.LyricParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 在线歌词与本地歌词多级智能聚合管理器
 */
class OnlineLyricManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "OnlineLyricManager"

        @Volatile
        private var instance: OnlineLyricManager? = null

        fun getInstance(context: Context): OnlineLyricManager {
            return instance ?: synchronized(this) {
                instance ?: OnlineLyricManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val memoryCache = LruCache<String, List<LyricLine>>(100)
    private val lyricCacheDir: File by lazy {
        File(context.cacheDir, "lyrics").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 异步获取歌曲的歌词列表（自动多级聚合与网络检索）
     */
    suspend fun getLyricForSong(song: Song?): List<LyricLine> {
        if (song == null) return emptyList()

        val cacheKey = buildCacheKey(song)

        // 1. 优先检查内存缓存
        memoryCache.get(cacheKey)?.let {
            if (it.isNotEmpty()) return it
        }

        return withContext(Dispatchers.IO) {
            // 2. 检查本地磁盘歌词缓存文件
            val diskCachedLines = loadFromDiskCache(cacheKey)
            if (diskCachedLines.isNotEmpty()) {
                memoryCache.put(cacheKey, diskCachedLines)
                return@withContext diskCachedLines
            }

            // 3. 如果是本地已存在的音频文件，尝试加载同目录下同名歌词
            if (!song.path.startsWith("http://") &&
                !song.path.startsWith("https://") &&
                !song.path.startsWith("online://")
            ) {
                val localLines = LyricParser.loadLyricForSong(song.path)
                if (localLines.isNotEmpty()) {
                    memoryCache.put(cacheKey, localLines)
                    return@withContext localLines
                }
            }

            // 4. 在线多源检索
            val lrcText = fetchOnlineLyricText(song)

            if (!lrcText.isNullOrBlank()) {
                val parsed = LyricParser.parseText(lrcText)
                if (parsed.isNotEmpty()) {
                    // 保存到磁盘缓存和内存缓存
                    saveToDiskCache(cacheKey, lrcText)
                    memoryCache.put(cacheKey, parsed)
                    return@withContext parsed
                }
            }

            emptyList()
        }
    }

    /**
     * 多渠道在线获取歌词纯文本
     */
    private fun fetchOnlineLyricText(song: Song): String? {
        val path = song.path

        // 渠道 A: 网易云原生直链
        if (path.startsWith("online://netease/")) {
            val songId = path.removePrefix("online://netease/").trim()
            val neteaseLrc = fetchNeteaseLyricById(songId)
            if (!neteaseLrc.isNullOrBlank()) {
                Log.d(TAG, "Fetched lyrics directly from Netease ID: $songId")
                return neteaseLrc
            }
        }

        // 渠道 B: QQ 音乐原生直链
        if (path.startsWith("online://qq/")) {
            val songMid = path.removePrefix("online://qq/").trim()
            val qqLrc = fetchQQLyricBySongMid(songMid)
            if (!qqLrc.isNullOrBlank()) {
                Log.d(TAG, "Fetched lyrics directly from QQ songmid: $songMid")
                return qqLrc
            }
        }

        // 渠道 C: 酷狗全网智能云端歌词检索（覆盖率高，格式标准）
        val kugouLrc = fetchKugouLyric(song.title, song.artist, song.durationMs)
        if (!kugouLrc.isNullOrBlank()) {
            Log.d(TAG, "Fetched lyrics from Kugou smart search for: ${song.title} - ${song.artist}")
            return kugouLrc
        }

        // 渠道 D: 网易云全网搜索匹配
        val neteaseSearchLrc = fetchNeteaseLyricBySearch(song.title, song.artist)
        if (!neteaseSearchLrc.isNullOrBlank()) {
            Log.d(TAG, "Fetched lyrics from Netease search for: ${song.title} - ${song.artist}")
            return neteaseSearchLrc
        }

        return null
    }

    /**
     * 网易云：根据歌曲 ID 直接获取歌词
     */
    private fun fetchNeteaseLyricById(songId: String): String? {
        if (songId.isBlank()) return null
        return try {
            val url = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val lrcObj = json.optJSONObject("lrc")
                lrcObj?.optString("lyric")?.takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "fetchNeteaseLyricById error: ${e.message}")
            null
        }
    }

    /**
     * QQ 音乐：根据 songmid 获取歌词
     */
    private fun fetchQQLyricBySongMid(songMid: String): String? {
        if (songMid.isBlank()) return null
        return try {
            val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                    "?songmid=$songMid&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://y.qq.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val base64Lyric = json.optString("lyric")
                if (base64Lyric.isNotBlank()) {
                    val decodedBytes = Base64.decode(base64Lyric, Base64.DEFAULT)
                    String(decodedBytes, Charsets.UTF_8)
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "fetchQQLyricBySongMid error: ${e.message}")
            null
        }
    }

    /**
     * 酷狗云端歌词引擎：基于标题和歌手智能搜索并下载歌词
     */
    private fun fetchKugouLyric(title: String, artist: String, durationMs: Long): String? {
        if (title.isBlank()) return null
        return try {
            val cleanTitle = cleanKeyword(title)
            val cleanArtist = cleanKeyword(artist)
            val query = if (cleanArtist.isNotBlank() && cleanArtist != "未知歌手") {
                "$cleanTitle $cleanArtist"
            } else {
                cleanTitle
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=$encodedQuery" +
                    if (durationMs > 0) "&duration=$durationMs" else ""

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val first = candidates.getJSONObject(0)
            val id = first.optString("id")
            val accesskey = first.optString("accesskey")
            if (id.isBlank() || accesskey.isBlank()) return null

            // 下载对应 lrc
            val downloadUrl = "https://krcs.kugou.com/download?id=$id&accesskey=$accesskey&fmt=lrc"
            val dlRequest = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .build()

            val dlResponse = httpClient.newCall(dlRequest).execute()
            if (!dlResponse.isSuccessful) return null

            val dlBody = dlResponse.body?.string() ?: return null
            val dlJson = JSONObject(dlBody)
            val contentBase64 = dlJson.optString("content")
            if (contentBase64.isNotBlank()) {
                val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
                String(decodedBytes, Charsets.UTF_8)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "fetchKugouLyric error: ${e.message}")
            null
        }
    }

    /**
     * 网易云全网搜索匹配歌词
     */
    private fun fetchNeteaseLyricBySearch(title: String, artist: String): String? {
        if (title.isBlank()) return null
        return try {
            val cleanTitle = cleanKeyword(title)
            val cleanArtist = cleanKeyword(artist)
            val query = if (cleanArtist.isNotBlank() && cleanArtist != "未知歌手") {
                "$cleanTitle $cleanArtist"
            } else {
                cleanTitle
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?s=$encodedQuery&type=1&limit=3"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val result = json.optJSONObject("result") ?: return null
            val songs = result.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null

            val matchedSong = songs.getJSONObject(0)
            val matchedId = matchedSong.optLong("id", -1L)
            if (matchedId <= 0) return null

            fetchNeteaseLyricById(matchedId.toString())
        } catch (e: Exception) {
            Log.w(TAG, "fetchNeteaseLyricBySearch error: ${e.message}")
            null
        }
    }

    private fun cleanKeyword(text: String): String {
        return text
            .replace(Regex("\\(.*\\)|\\[.*]|【.*】|（.*）"), "")
            .replace(Regex("[/\\\\_#$&]"), " ")
            .trim()
    }

    private fun buildCacheKey(song: Song): String {
        val raw = "${song.title}_${song.artist}_${song.path}"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            raw.hashCode().toString()
        }
    }

    private fun loadFromDiskCache(cacheKey: String): List<LyricLine> {
        return try {
            val file = File(lyricCacheDir, "$cacheKey.lrc")
            if (file.exists() && file.isFile && file.length() > 0) {
                LyricParser.parseFile(file)
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToDiskCache(cacheKey: String, lrcContent: String) {
        try {
            val file = File(lyricCacheDir, "$cacheKey.lrc")
            file.writeText(lrcContent, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "saveToDiskCache failed: ${e.message}")
        }
    }
}
