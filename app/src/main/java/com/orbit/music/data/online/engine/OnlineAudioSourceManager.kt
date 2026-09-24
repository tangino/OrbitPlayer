package com.orbit.music.data.online.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.LruCache
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlineSongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 在线音频直链解析调度器与音源管理器
 */
class OnlineAudioSourceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "OnlineAudioSourceMgr"
        private const val PREFS_NAME = "online_audio_source_prefs"
        private const val KEY_CUSTOM_SCRIPT = "custom_lx_script_content"
        private const val KEY_CUSTOM_SCRIPT_NAME = "custom_lx_script_name"

        @Volatile
        private var instance: OnlineAudioSourceManager? = null

        fun getInstance(context: Context): OnlineAudioSourceManager {
            return instance ?: synchronized(this) {
                instance ?: OnlineAudioSourceManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 为在线歌曲生成一个全局唯一且稳定的虚拟负数 ID（杜绝与本地 MediaStore 冲突）
         */
        fun generateVirtualSongId(platform: OnlinePlatform, songId: String): Long {
            val combined = "${platform.id}:$songId"
            val hash = combined.hashCode().toLong()
            // 确保为负数
            return if (hash > 0) -hash else if (hash == 0L) -999999L else hash
        }

        /**
         * 判断一首歌曲是否属于在线歌曲
         */
        fun isOnlineSong(song: Song): Boolean {
            return song.id < 0 || song.path.startsWith("online://") || song.path.startsWith("http://") || song.path.startsWith("https://")
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lxEngine: LxSourceEngine? = null

    // 内存 LRU 缓存：Key = "$platform:$songId", Value = CachedUrl(url, expireAtMs)
    private data class CachedUrl(val url: String, val expireAtMs: Long)
    private val memoryUrlCache = LruCache<String, CachedUrl>(200)

    init {
        loadCustomScriptEngine()
    }

    /**
     * 加载已保存的用户自定义洛雪源脚本
     */
    fun loadCustomScriptEngine() {
        val activeItem = SourceScriptManager.getInstance(context).activeScript.value
        if (activeItem != null && activeItem.scriptContent.isNotBlank()) {
            lxEngine?.destroy()
            lxEngine = LxSourceEngine(context, activeItem.scriptContent, activeItem.id, activeItem.name)
            Log.i(TAG, "Loaded active source script from SourceScriptManager: ${activeItem.name}")
        } else {
            val script = prefs.getString(KEY_CUSTOM_SCRIPT, null)
            val scriptName = prefs.getString(KEY_CUSTOM_SCRIPT_NAME, "用户自定义源") ?: "用户自定义源"
            if (!script.isNullOrBlank()) {
                lxEngine?.destroy()
                lxEngine = LxSourceEngine(context, script, "user_script", scriptName)
                Log.i(TAG, "Loaded custom LX source script: $scriptName")
            }
        }
    }

    /**
     * 热加载指定脚本内容
     */
    fun loadCustomScript(scriptContent: String) {
        lxEngine?.destroy()
        lxEngine = LxSourceEngine(context, scriptContent, "active_script", "活动音源")
        memoryUrlCache.evictAll()
        Log.i(TAG, "Hot-reloaded custom LX source script")
    }

    /**
     * 保存并激活新的洛雪源脚本
     */
    fun saveCustomScript(name: String, scriptContent: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_SCRIPT, scriptContent)
            .putString(KEY_CUSTOM_SCRIPT_NAME, name)
            .apply()
        loadCustomScript(scriptContent)
    }

    /**
     * 清除自定义源脚本
     */
    fun clearCustomScript() {
        prefs.edit().remove(KEY_CUSTOM_SCRIPT).remove(KEY_CUSTOM_SCRIPT_NAME).apply()
        lxEngine?.destroy()
        lxEngine = null
        memoryUrlCache.evictAll()
        Log.i(TAG, "Cleared custom LX source script")
    }

    fun hasCustomScript(): Boolean {
        return lxEngine != null || SourceScriptManager.getInstance(context).activeScript.value != null
    }

    fun getCustomScriptName(): String? {
        return SourceScriptManager.getInstance(context).activeScript.value?.name
            ?: prefs.getString(KEY_CUSTOM_SCRIPT_NAME, null)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private data class MatchedPlatformSong(
        val platform: OnlinePlatform,
        val songId: String,
        val hash: String? = null,
        val matchTitle: String? = null
    )

    /**
     * 咪咕音乐官方音频直链解析 (支持普通与高品质音频，获取真实重定向 CDN 直链)
     */
    private suspend fun resolveMiguDirectPlayUrl(copyrightId: String): String? = withContext(Dispatchers.IO) {
        if (copyrightId.isBlank()) return@withContext null
        try {
            // 1. 根据 copyrightId 查询 18 位 contentId
            val infoUrl = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?copyrightId=$copyrightId&resourceType=2"
            val req = Request.Builder()
                .url(infoUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
                .header("Referer", "https://m.music.migu.cn/")
                .build()
            val resStr = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext null
            val root = JSONObject(resStr)
            val resourceArr = root.optJSONArray("resource") ?: return@withContext null
            val resObj = resourceArr.optJSONObject(0) ?: return@withContext null
            val contentId = resObj.optString("contentId")
            if (contentId.isBlank()) return@withContext null

            // 2. 依次尝试 HQ 与 PQ 音质网关，获取 302 重定向后的真实 CDN 播放直链
            val toneFlags = listOf("HQ", "PQ")
            for (tone in toneFlags) {
                val gateUrl = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/sub/listenSong.do?toneFlag=$tone&netType=00&userId=1&ua=Android_migu&version=5.1&copyrightId=$copyrightId&contentId=$contentId&resourceType=2&channel=0"
                val gateReq = Request.Builder()
                    .url(gateUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
                    .header("Referer", "https://m.music.migu.cn/")
                    .header("channel", "0")
                    .build()
                okHttpClient.newCall(gateReq).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    if (resp.isSuccessful && !finalUrl.contains("listenSong.do", ignoreCase = true)) {
                        return@withContext finalUrl
                    }
                    val location = resp.header("Location")
                    if (!location.isNullOrBlank()) {
                        return@withContext location
                    }
                }
            }

            // 兜底返回默认网关 URL
            "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/sub/listenSong.do?toneFlag=HQ&netType=00&userId=1&ua=Android_migu&version=5.1&copyrightId=$copyrightId&contentId=$contentId&resourceType=2&channel=0"
        } catch (e: Exception) {
            Log.w(TAG, "resolveMiguDirectPlayUrl error for $copyrightId: ${e.message}")
            null
        }
    }

    /**
     * 跨平台在目标平台上轻量搜索匹配对应的单曲 ID 与哈希
     */
    private suspend fun searchMatchedSongOnPlatform(
        targetPlatform: OnlinePlatform,
        title: String,
        artist: String
    ): MatchedPlatformSong? = withContext(Dispatchers.IO) {
        val keyword = "$title $artist".trim()
        try {
            when (targetPlatform) {
                OnlinePlatform.NETEASE -> {
                    val postData = FormBody.Builder()
                        .add("s", keyword)
                        .add("type", "1")
                        .add("offset", "0")
                        .add("limit", "3")
                        .add("total", "true")
                        .build()
                    val req = Request.Builder()
                        .url("https://music.163.com/api/search/get/web?csrf_token=")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://music.163.com/")
                        .post(postData)
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext null
                    val obj = JSONObject(res).optJSONObject("result")?.optJSONArray("songs")?.optJSONObject(0)
                    val id = obj?.optLong("id")?.toString()
                    if (!id.isNullOrBlank() && id != "0") {
                        MatchedPlatformSong(targetPlatform, id, matchTitle = obj.optString("name"))
                    } else null
                }
                OnlinePlatform.QQ -> {
                    val payload = JSONObject().apply {
                        put("comm", JSONObject().apply {
                            put("ct", "19")
                            put("cv", "1873")
                            put("uin", "0")
                        })
                        put("req", JSONObject().apply {
                            put("module", "music.search.SearchCgiService")
                            put("method", "DoSearchForQQMusicDesktop")
                            put("param", JSONObject().apply {
                                put("query", keyword)
                                put("search_type", 0)
                                put("num_per_page", 3)
                                put("page_num", 1)
                            })
                        })
                    }
                    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val req = Request.Builder()
                        .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://y.qq.com/")
                        .post(body)
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext null
                    val list = JSONObject(res).optJSONObject("req")?.optJSONObject("data")?.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
                    val first = list?.optJSONObject(0)
                    val mid = first?.optString("mid")?.ifEmpty { first.optString("songmid") }
                    if (!mid.isNullOrBlank()) {
                        MatchedPlatformSong(targetPlatform, mid, matchTitle = first.optString("name").ifEmpty { first.optString("title") })
                    } else null
                }
                OnlinePlatform.KUGOU -> {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val url = "http://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=1&pagesize=3&platform=WebFilter"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext null
                    val first = JSONObject(res).optJSONObject("data")?.optJSONArray("lists")?.optJSONObject(0)
                    val hash = first?.optString("FileHash")?.ifEmpty { first.optString("HQFileHash") }
                    val id = first?.optString("Audioid")?.ifEmpty { first.optString("Scid") } ?: ""
                    if (!hash.isNullOrBlank()) {
                        MatchedPlatformSong(targetPlatform, if (id.isNotEmpty()) id else hash, hash = hash, matchTitle = first.optString("SongName"))
                    } else null
                }
                OnlinePlatform.KUWO -> {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val url = "http://search.kuwo.cn/r.s?all=$encoded&ft=music&itemset=web_2013&client=kt&pn=0&rn=3&rformat=json&encoding=utf8"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val raw = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext null
                    val fixed = raw.replace('\'', '"')
                    val first = JSONObject(fixed).optJSONArray("abslist")?.optJSONObject(0)
                    val id = first?.optString("MUSICRID")?.replace("MUSIC_", "")?.ifEmpty { first.optString("id") }
                    if (!id.isNullOrBlank()) {
                        MatchedPlatformSong(targetPlatform, id, matchTitle = first.optString("SONGNAME"))
                    } else null
                }
                OnlinePlatform.MIGU -> {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val switchJson = URLEncoder.encode("{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":0,\"mvSong\":0,\"songlist\":0,\"bestShow\":0}", "UTF-8")
                    val url = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?text=$encoded&pageNo=1&pageSize=3&searchSwitch=$switchJson"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://m.music.migu.cn")
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext null
                    val first = JSONObject(res).optJSONObject("songResultData")?.optJSONArray("result")?.optJSONObject(0)
                    val id = first?.optString("copyrightId")?.ifEmpty { first.optString("id") }
                    if (!id.isNullOrBlank()) {
                        MatchedPlatformSong(targetPlatform, id, matchTitle = first.optString("name"))
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Search match failed on ${targetPlatform.displayName} for $keyword: ${e.message}")
            null
        }
    }

    /**
     * 针对音频直链进行快速探活与有效性检验
     * 校验其是否为真正可播放的有效音频流（防止音源返回 404 / 403 / HTML 错误页面导致 ExoPlayer 解码失败或报错格式不支持）
     */
    private suspend fun probeAndValidateAudioUrl(rawUrl: String): String? = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext null
        if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }
        try {
            // 使用 Range 请求探测前 1KB 数据，既快速又不消耗过多流量
            val req = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-1024")
                .header("Accept", "*/*")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val finalUrl = resp.request.url.toString()
                val contentType = resp.header("Content-Type", "")?.lowercase() ?: ""

                // 若状态码为 4xx/5xx，或返回网页/JSON 报错文本，判定为无效音频流
                if (code >= 400 || contentType.contains("text/html") || contentType.contains("application/json")) {
                    Log.d(TAG, "Audio probe rejected URL: status=$code, contentType=$contentType for $rawUrl")
                    return@withContext null
                }

                // 检查响应体大小，若小于 512 字节且不是分段音频，判定为无效
                val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                if (code == 200 && contentLength in 0..512) {
                    Log.d(TAG, "Audio probe rejected URL: file too small ($contentLength bytes) for $rawUrl")
                    return@withContext null
                }

                if (resp.isSuccessful) {
                    return@withContext finalUrl
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Audio probe exception for $rawUrl: ${e.message}")
            null
        }
    }

    /**
     * 异步解析在线单曲的真实音频直链 (支持跨平台多源自动降级与智能轮询)
     */
    suspend fun resolvePlayableUrl(
        platform: OnlinePlatform,
        songId: String,
        title: String,
        artist: String,
        album: String
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${platform.id}:$songId"
        val now = System.currentTimeMillis()

        // 1. 查内存缓存
        val cached = memoryUrlCache.get(cacheKey)
        if (cached != null && cached.expireAtMs > now) {
            Log.d(TAG, "Hit memory cache for $title: ${cached.url}")
            return@withContext cached.url
        }

        var resolvedUrl: String? = null
        val engine = lxEngine
        val prefQuality = SourceScriptManager.getInstance(context).preferredQuality.value
        val qualityTryList = when (prefQuality) {
            "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
            "flac" -> listOf("flac", "320k", "128k")
            "320k" -> listOf("320k", "128k")
            else -> listOf("128k", "320k", "flac")
        }

        // 2. 跨平台轮询策略：优先尝试当前广场，若失败则轮询尝试其余所有广场
        val candidatePlatforms = listOf(platform) + OnlinePlatform.values().filter { it != platform }

        for (targetPlatform in candidatePlatforms) {
            val isOriginal = targetPlatform == platform
            var targetSongId = if (isOriginal) songId else ""
            var targetHash = if (isOriginal) songId else ""

            if (!isOriginal) {
                val match = searchMatchedSongOnPlatform(targetPlatform, title, artist)
                if (match != null) {
                    targetSongId = match.songId
                    targetHash = match.hash ?: match.songId
                }
            }

            val sourceKey = when (targetPlatform) {
                OnlinePlatform.NETEASE -> "wy"
                OnlinePlatform.QQ -> "tx"
                OnlinePlatform.KUGOU -> "kg"
                OnlinePlatform.KUWO -> "kw"
                OnlinePlatform.MIGU -> "mg"
            }

            // 尝试通过音源脚本引擎解析
            if (engine != null) {
                val effectiveId = targetSongId.ifEmpty { songId }
                val effectiveHash = targetHash.ifEmpty { songId }
                val musicInfo = JSONObject().apply {
                    put("name", title)
                    put("singer", artist)
                    put("albumName", album)
                    put("songmid", effectiveId)
                    put("id", effectiveId)
                    put("hash", effectiveHash)
                    put("copyrightId", effectiveId)
                    put("source", sourceKey)
                    put("types", JSONArray().apply {
                        put(JSONObject().put("type", "128k"))
                        put(JSONObject().put("type", "320k"))
                        put(JSONObject().put("type", "flac"))
                        put(JSONObject().put("type", "flac24bit"))
                    })
                }

                for (q in qualityTryList) {
                    val url = engine.resolveMusicUrl(sourceKey, musicInfo, q, timeoutMs = 3500L)
                    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                        val validUrl = probeAndValidateAudioUrl(url)
                        if (validUrl != null) {
                            resolvedUrl = validUrl
                            Log.i(TAG, "Successfully resolved & verified $title ($q) via ${targetPlatform.displayName} (${if (isOriginal) "当前广场" else "跨广场备用源"}): $validUrl")
                            break
                        }
                    }
                }
            }

            // 针对网易云官方 outer 兜底直链
            if (resolvedUrl.isNullOrBlank() && targetPlatform == OnlinePlatform.NETEASE) {
                val neteaseId = targetSongId.ifEmpty { if (isOriginal) songId else "" }
                if (neteaseId.isNotEmpty() && neteaseId.matches(Regex("^\\d+$"))) {
                    val outerUrl = "https://music.163.com/song/media/outer/url?id=$neteaseId.mp3"
                    val validUrl = probeAndValidateAudioUrl(outerUrl)
                    if (validUrl != null) {
                        resolvedUrl = validUrl
                        Log.i(TAG, "Fallback to verified Netease outer URL for $title via ${targetPlatform.displayName}: $resolvedUrl")
                    }
                }
            }

            // 针对咪咕音乐官方免费直链兜底
            if (resolvedUrl.isNullOrBlank() && targetPlatform == OnlinePlatform.MIGU) {
                val miguId = targetSongId.ifEmpty { if (isOriginal) songId else "" }
                if (miguId.isNotEmpty()) {
                    val miguUrl = resolveMiguDirectPlayUrl(miguId)
                    if (!miguUrl.isNullOrBlank()) {
                        val validUrl = probeAndValidateAudioUrl(miguUrl)
                        if (validUrl != null) {
                            resolvedUrl = validUrl
                            Log.i(TAG, "Fallback to verified Migu official direct URL for $title: $resolvedUrl")
                        }
                    }
                }
            }

            // 一旦成功获取并通过检验的有效直链，立即结束遍历
            if (!resolvedUrl.isNullOrBlank()) {
                break
            }
        }

        if (resolvedUrl.isNullOrBlank()) {
            if (engine == null) {
                Log.w(TAG, "No active third-party source script loaded for resolving $title across all platforms")
            } else {
                Log.w(TAG, "All online source platforms failed to resolve valid audio for $title")
            }
        } else {
            // 写入缓存 (有效期 1 小时)
            val expireAt = now + 3600_000L
            memoryUrlCache.put(cacheKey, CachedUrl(resolvedUrl, expireAt))
        }

        resolvedUrl
    }

    /**
     * 将 OnlineSongItem 转换为标准的 Player Song 对象
     */
    fun toSong(item: OnlineSongItem, directPlayableUrl: String? = null): Song {
        val virtualId = generateVirtualSongId(item.platform, item.id)
        val path = directPlayableUrl ?: "online://${item.platform.id}/${item.id}"
        return Song(
            id = virtualId,
            title = item.title,
            artist = item.artist,
            album = item.album,
            albumId = virtualId,
            durationMs = item.durationMs,
            path = path,
            size = 0L,
            albumArtUri = item.coverUrl,
            folderPath = "在线歌单 - ${item.platform.displayName}",
            mimeType = "audio/mpeg"
        )
    }

    /**
     * 批量转换在线歌单为 Song 列表
     */
    fun toSongList(items: List<OnlineSongItem>): List<Song> {
        return items.map { toSong(it) }
    }
}
