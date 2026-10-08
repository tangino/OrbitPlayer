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
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
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

    data class ResolvedAudioSource(
        val url: String,
        val platform: OnlinePlatform,
        val sourceName: String,
        val quality: String? = null,
        val durationMs: Long = 0L,
        val isFallback: Boolean = false,
        val fallbackReason: String? = null
    )

    /**
     * 针对音频直链进行快速探活、流长度与实际时间长度完整性校验 (分级高速通道)
     * 1. 优先通过 Range 响应头秒级确认文件大小与音频流格式 (大文件可在 50ms 内直接放行)
     * 2. 仅对小体积或可疑流启动 MediaMetadataRetriever 时长校验，杜绝截断与试听短流
     */
    private suspend fun probeAndValidateAudioUrl(
        rawUrl: String,
        expectedDurationMs: Long = 0L
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext null
        if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }
        try {
            // 1. 使用 Range 请求探测前 8KB 数据与响应头 (快速、低流量)
            val req = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-8192")
                .header("Accept", "*/*")
                .build()

            val finalUrl: String?
            var totalContentLength = -1L
            val contentType: String

            okHttpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                finalUrl = resp.request.url.toString()
                contentType = resp.header("Content-Type", "")?.lowercase() ?: ""

                // 若状态码为 4xx/5xx，或返回网页/JSON 报错文本，判定为无效音频流
                if (code >= 400 || contentType.contains("text/html") || contentType.contains("application/json")) {
                    Log.d(TAG, "Audio probe rejected URL: status=$code, contentType=$contentType for $rawUrl")
                    return@withContext null
                }

                if (!resp.isSuccessful && code != 206) {
                    return@withContext null
                }

                // 提取总流大小 (针对 206 Partial Content 从 Content-Range 提取总大小，针对 200 从 Content-Length 提取)
                val contentRange = resp.header("Content-Range")
                if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                    val totalStr = contentRange.substringAfterLast("/").trim()
                    totalContentLength = totalStr.toLongOrNull() ?: -1L
                }
                if (totalContentLength <= 0L) {
                    totalContentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                }

                // 检查响应体大小，若小于 1024 字节且不是分段音频，判定为无效
                if (code == 200 && totalContentLength in 0..1024) {
                    Log.d(TAG, "Audio probe rejected URL: file too small ($totalContentLength bytes) for $rawUrl")
                    return@withContext null
                }

                // 流字节数长度与预期时间长度比对检验
                if (expectedDurationMs >= 45000L && totalContentLength > 0L) {
                    val minExpectedBytes = (expectedDurationMs / 1000L) * 3500L // 即使按极低 28kbps 算每秒至少 3.5KB
                    if (totalContentLength < minExpectedBytes) {
                        Log.w(TAG, "Audio probe rejected URL: stream length too short ($totalContentLength bytes, expected >= $minExpectedBytes for ${expectedDurationMs}ms) for $rawUrl")
                        return@withContext null
                    }
                }
            }

            val validUrl = finalUrl ?: return@withContext null

            // 2. 🚀 高速免检通道 (Fast-Path)：
            // 典型 30 秒试听截断片段大小通常仅为 200KB~600KB。
            // 当探测到的真实文件大小 >= 1.5MB（或预期时长存在且文件大小完全达到完整音轨基准），
            // 且内容类型为音频流或默认二进制流时，可在 50ms 内直接判定为有效完整音频，跳过耗时的 MediaMetadataRetriever！
            val isKnownLargeCompleteFile = totalContentLength >= 1_500_000L ||
                    (expectedDurationMs >= 45000L && totalContentLength >= (expectedDurationMs / 1000L) * 11_000L)
            if (isKnownLargeCompleteFile) {
                return@withContext Pair(validUrl, expectedDurationMs)
            }

            // 3. 针对可疑较小文件（或无法得知大小的流），启动轻量时长检验 (超时从 2200ms 压缩至 1200ms)
            var detectedDurationMs = 0L
            try {
                kotlinx.coroutines.withTimeoutOrNull(1200L) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        val headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36"
                        )
                        retriever.setDataSource(validUrl, headers)
                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        detectedDurationMs = durStr?.toLongOrNull() ?: 0L
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "MediaMetadataRetriever probe skipped for $validUrl: ${e.message}")
            }

            // 若探测到实际时长，且歌曲预期时长较长（如正规歌曲），校验时长是否严重偏短（如 30s 试听截断片段）
            if (expectedDurationMs >= 45000L && detectedDurationMs > 0L) {
                if (detectedDurationMs < 60000L && expectedDurationMs >= 90000L) {
                    Log.w(TAG, "Audio probe rejected URL: preview duration too short ($detectedDurationMs ms vs expected $expectedDurationMs ms) for $validUrl")
                    return@withContext null
                }
                if (detectedDurationMs < (expectedDurationMs * 0.60f).toLong()) {
                    Log.w(TAG, "Audio probe rejected URL: actual duration incomplete ($detectedDurationMs ms vs expected $expectedDurationMs ms) for $validUrl")
                    return@withContext null
                }
            }

            Pair(validUrl, if (detectedDurationMs > 0L) detectedDurationMs else expectedDurationMs)
        } catch (e: Exception) {
            Log.d(TAG, "Audio probe exception for $rawUrl: ${e.message}")
            null
        }
    }

    /**
     * 针对单个平台执行音频解析 (脚本解析 + 官方直链兜底)
     */
    private suspend fun resolveSinglePlatformSource(
        targetPlatform: OnlinePlatform,
        songId: String,
        title: String,
        artist: String,
        album: String,
        expectedDurationMs: Long,
        isOriginal: Boolean,
        engine: LxSourceEngine?,
        scriptName: String,
        qualityTryList: List<String>
    ): ResolvedAudioSource? = withContext(Dispatchers.IO) {
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

        var resolvedResult: ResolvedAudioSource? = null

        // 1. 尝试通过音源脚本引擎解析 (超时优化为 1800ms)
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
                val url = engine.resolveMusicUrl(sourceKey, musicInfo, q, timeoutMs = 1800L)
                if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                    val probePair = probeAndValidateAudioUrl(url, expectedDurationMs)
                    if (probePair != null) {
                        val (validUrl, detectedDur) = probePair
                        val finalDur = if (detectedDur > 0L) detectedDur else expectedDurationMs
                        resolvedResult = ResolvedAudioSource(
                            url = validUrl,
                            platform = targetPlatform,
                            sourceName = scriptName,
                            quality = q,
                            durationMs = finalDur,
                            isFallback = !isOriginal,
                            fallbackReason = if (!isOriginal) "原平台音频缺失或试听短流，自动降级切换至 ${targetPlatform.displayName}" else null
                        )
                        Log.i(TAG, "Successfully resolved & verified $title ($q) via ${targetPlatform.displayName} (${if (isOriginal) "当前音源" else "跨平台备用源"}): $validUrl (duration: ${finalDur}ms)")
                        return@withContext resolvedResult
                    }
                }
            }
        }

        // 2. 针对网易云官方 outer 兜底直链
        if (targetPlatform == OnlinePlatform.NETEASE) {
            val neteaseId = targetSongId.ifEmpty { if (isOriginal) songId else "" }
            if (neteaseId.isNotEmpty() && neteaseId.matches(Regex("^\\d+$"))) {
                val outerUrl = "https://music.163.com/song/media/outer/url?id=$neteaseId.mp3"
                val probePair = probeAndValidateAudioUrl(outerUrl, expectedDurationMs)
                if (probePair != null) {
                    val (validUrl, detectedDur) = probePair
                    val finalDur = if (detectedDur > 0L) detectedDur else expectedDurationMs
                    resolvedResult = ResolvedAudioSource(
                        url = validUrl,
                        platform = targetPlatform,
                        sourceName = "网易云官方",
                        quality = "128k",
                        durationMs = finalDur,
                        isFallback = !isOriginal,
                        fallbackReason = if (!isOriginal) "原音源未匹配，自动降级至网易云官方音频" else null
                    )
                    Log.i(TAG, "Fallback to verified Netease outer URL for $title via ${targetPlatform.displayName}: $validUrl")
                    return@withContext resolvedResult
                }
            }
        }

        // 3. 针对咪咕音乐官方免费直链兜底
        if (targetPlatform == OnlinePlatform.MIGU) {
            val miguId = targetSongId.ifEmpty { if (isOriginal) songId else "" }
            if (miguId.isNotEmpty()) {
                val miguUrl = resolveMiguDirectPlayUrl(miguId)
                if (!miguUrl.isNullOrBlank()) {
                    val probePair = probeAndValidateAudioUrl(miguUrl, expectedDurationMs)
                    if (probePair != null) {
                        val (validUrl, detectedDur) = probePair
                        val finalDur = if (detectedDur > 0L) detectedDur else expectedDurationMs
                        resolvedResult = ResolvedAudioSource(
                            url = validUrl,
                            platform = targetPlatform,
                            sourceName = "咪咕官方",
                            quality = "HQ",
                            durationMs = finalDur,
                            isFallback = !isOriginal,
                            fallbackReason = if (!isOriginal) "原音源未匹配，自动降级至咪咕官方直链" else null
                        )
                        Log.i(TAG, "Fallback to verified Migu official direct URL for $title: $validUrl")
                        return@withContext resolvedResult
                    }
                }
            }
        }

        null
    }

    /**
     * 异步解析在线单曲的真实音频直链与音源信息 (支持流长度/时间完整性校验与跨平台多源自动降级寻址)
     */
    suspend fun resolvePlayableSource(
        platform: OnlinePlatform,
        songId: String,
        title: String,
        artist: String,
        album: String,
        expectedDurationMs: Long = 0L,
        explicitQuality: String? = null
    ): ResolvedAudioSource? = withContext(Dispatchers.IO) {
        val cacheKey = if (explicitQuality != null) "${platform.id}:$songId:$explicitQuality" else "${platform.id}:$songId"
        val now = System.currentTimeMillis()

        // 1. 查内存缓存 (0ms 秒开)
        val cached = memoryUrlCache.get(cacheKey)
        if (cached != null && cached.expireAtMs > now) {
            Log.d(TAG, "Hit memory cache for $title: ${cached.url}")
            return@withContext ResolvedAudioSource(
                url = cached.url,
                platform = platform,
                sourceName = platform.displayName,
                durationMs = expectedDurationMs,
                quality = explicitQuality
            )
        }

        val engine = lxEngine
        val scriptName = getCustomScriptName() ?: "音源脚本"
        val prefQuality = explicitQuality ?: SourceScriptManager.getInstance(context).preferredQuality.value
        val qualityTryList = when (prefQuality) {
            "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
            "flac" -> listOf("flac", "320k", "128k")
            "320k" -> listOf("320k", "128k")
            else -> listOf("128k", "320k", "flac")
        }

        // 2. 优先尝试当前所属广场平台 (原平台优先通道)
        var resolvedResult = resolveSinglePlatformSource(
            targetPlatform = platform,
            songId = songId,
            title = title,
            artist = artist,
            album = album,
            expectedDurationMs = expectedDurationMs,
            isOriginal = true,
            engine = engine,
            scriptName = scriptName,
            qualityTryList = qualityTryList
        )

        // 3. 🚀 跨平台并发竞速寻源 (若原平台无源，对其余 4 大平台发起并行搜索与解析，最快成功的立即返回)
        if (resolvedResult == null) {
            val fallbackPlatforms = OnlinePlatform.values().filter { it != platform }
            val channel = Channel<ResolvedAudioSource?>(fallbackPlatforms.size)

            coroutineScope {
                val jobs = fallbackPlatforms.map { targetPlatform ->
                    launch(Dispatchers.IO) {
                        val result = resolveSinglePlatformSource(
                            targetPlatform = targetPlatform,
                            songId = songId,
                            title = title,
                            artist = artist,
                            album = album,
                            expectedDurationMs = expectedDurationMs,
                            isOriginal = false,
                            engine = engine,
                            scriptName = scriptName,
                            qualityTryList = qualityTryList
                        )
                        channel.send(result)
                    }
                }

                var finishedCount = 0
                while (finishedCount < fallbackPlatforms.size) {
                    val candidate = channel.receive()
                    finishedCount++
                    if (candidate != null) {
                        resolvedResult = candidate
                        jobs.forEach { job -> job.cancel() }
                        break
                    }
                }
            }
        }

        val finalResult = resolvedResult
        if (finalResult == null) {
            if (engine == null) {
                Log.w(TAG, "No active third-party source script loaded for resolving $title across all platforms")
            } else {
                Log.w(TAG, "All online source platforms failed to resolve valid complete audio for $title")
            }
        } else {
            // 写入缓存 (有效期 1 小时)
            val expireAt = now + 3600_000L
            memoryUrlCache.put(cacheKey, CachedUrl(finalResult.url, expireAt))
        }

        finalResult
    }

    /**
     * 异步解析在线单曲的真实音频直链 (兼容旧接口)
     */
    suspend fun resolvePlayableUrl(
        platform: OnlinePlatform,
        songId: String,
        title: String,
        artist: String,
        album: String,
        expectedDurationMs: Long = 0L
    ): String? {
        return resolvePlayableSource(platform, songId, title, artist, album, expectedDurationMs)?.url
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
            mimeType = "audio/mpeg",
            sourcePlatform = item.platform,
            sourceTag = item.platform.displayName,
            originalPlatform = item.platform
        )
    }

    /**
     * 批量转换在线歌单为 Song 列表
     */
    fun toSongList(items: List<OnlineSongItem>): List<Song> {
        return items.map { toSong(it) }
    }
}
