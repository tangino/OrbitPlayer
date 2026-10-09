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

    data class ScriptEngineHolder(
        val id: String,
        val name: String,
        val isPrimary: Boolean,
        val engine: LxSourceEngine
    )
    private val engineHolders = mutableListOf<ScriptEngineHolder>()

    // 内存 LRU 缓存：Key = "$platform:$songId", Value = CachedUrl(url, expireAtMs)
    private data class CachedUrl(val url: String, val expireAtMs: Long)
    private val memoryUrlCache = LruCache<String, CachedUrl>(200)

    init {
        reloadScriptEngines()
    }

    /**
     * 重新装载所有已启用的音源引擎池 (主音源优先排在第一位，其余作为备用协同源)
     */
    @Synchronized
    fun reloadScriptEngines() {
        // 销毁旧引擎
        engineHolders.forEach { it.engine.destroy() }
        engineHolders.clear()
        memoryUrlCache.evictAll()

        val sourceManager = SourceScriptManager.getInstance(context)
        val enabledList = sourceManager.enabledScripts.value
        val primaryItem = sourceManager.activeScript.value

        if (enabledList.isNotEmpty()) {
            val sorted = enabledList.sortedByDescending { it.id == primaryItem?.id || it.isPrimary }
            for (item in sorted) {
                if (item.scriptContent.isNotBlank()) {
                    val isPrimary = (item.id == primaryItem?.id || item.isPrimary)
                    val engine = LxSourceEngine(context, item.scriptContent, item.id, item.name)
                    engineHolders.add(ScriptEngineHolder(item.id, item.name, isPrimary, engine))
                    Log.i(TAG, "Loaded engine in pool: ${item.name} (isPrimary=$isPrimary)")
                }
            }
        } else {
            val script = prefs.getString(KEY_CUSTOM_SCRIPT, null)
            val scriptName = prefs.getString(KEY_CUSTOM_SCRIPT_NAME, "用户自定义源") ?: "用户自定义源"
            if (!script.isNullOrBlank()) {
                val engine = LxSourceEngine(context, script, "user_script", scriptName)
                engineHolders.add(ScriptEngineHolder("user_script", scriptName, true, engine))
                Log.i(TAG, "Loaded legacy custom LX source script: $scriptName")
            }
        }
    }

    /**
     * 加载已保存的用户自定义洛雪源脚本 (兼容旧调用)
     */
    fun loadCustomScriptEngine() {
        reloadScriptEngines()
    }

    /**
     * 热加载指定脚本内容
     */
    fun loadCustomScript(scriptContent: String) {
        reloadScriptEngines()
    }

    /**
     * 保存并激活新的洛雪源脚本
     */
    fun saveCustomScript(name: String, scriptContent: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_SCRIPT, scriptContent)
            .putString(KEY_CUSTOM_SCRIPT_NAME, name)
            .apply()
        reloadScriptEngines()
    }

    /**
     * 清除自定义源脚本
     */
    fun clearCustomScript() {
        prefs.edit().remove(KEY_CUSTOM_SCRIPT).remove(KEY_CUSTOM_SCRIPT_NAME).apply()
        engineHolders.forEach { it.engine.destroy() }
        engineHolders.clear()
        memoryUrlCache.evictAll()
        Log.i(TAG, "Cleared custom LX source scripts")
    }

    fun hasCustomScript(): Boolean {
        return engineHolders.isNotEmpty() || SourceScriptManager.getInstance(context).enabledScripts.value.isNotEmpty()
    }

    fun getCustomScriptName(): String? {
        val primary = engineHolders.firstOrNull { it.isPrimary } ?: engineHolders.firstOrNull()
        return primary?.name ?: SourceScriptManager.getInstance(context).activeScript.value?.name
            ?: prefs.getString(KEY_CUSTOM_SCRIPT_NAME, null)
    }

    fun getActiveEnginesCount(): Int = engineHolders.size

    /**
     * 主动驱逐指定单曲的内存 URL 缓存 (当播放异常/403/过期时调用)
     */
    fun evictUrlCache(platform: OnlinePlatform, songId: String) {
        val baseKey = "${platform.id}:$songId"
        memoryUrlCache.remove(baseKey)
        listOf("128k", "320k", "flac", "flac24bit").forEach { q ->
            memoryUrlCache.remove("$baseKey:$q")
        }
        Log.d(TAG, "Evicted URL cache for ${platform.displayName}:$songId")
    }

    /**
     * 预热与预加载音频流 (后台提前建立连接与首块数据缓冲)
     */
    suspend fun preloadAudioStream(url: String, platform: OnlinePlatform? = null): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return@withContext false
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-65536") // 预读前 64KB
            if (platform != null) {
                when (platform) {
                    OnlinePlatform.QQ -> reqBuilder.header("Referer", "https://y.qq.com/")
                    OnlinePlatform.NETEASE -> reqBuilder.header("Referer", "https://music.163.com/")
                    OnlinePlatform.KUGOU -> reqBuilder.header("Referer", "https://www.kugou.com/")
                    OnlinePlatform.KUWO -> reqBuilder.header("Referer", "https://www.kuwo.cn/")
                    OnlinePlatform.MIGU -> reqBuilder.header("Referer", "https://m.music.migu.cn/")
                }
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        } catch (_: Exception) {
            false
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 歌曲智能匹配与归一化工具类
     * 严格遵守匹配规则：
     * 1. 歌曲名称一致（支持繁简/标点归一化、去除副标题影视信息对比、区分特殊版本标签如 Live/伴奏/Remix 等）
     * 2. 歌曲作者一致（支持多歌手拆分包含与核心歌手匹配）
     * 3. 时间误差不超过 5 秒（当原歌曲与候选歌曲均有时长信息时）
     */
    private object SongMatcher {
        private val SPECIAL_TAGS = listOf(
            "live", "伴奏", "instrumental", "inst", "remix", "cover", "翻唱", "dj", "纯音乐",
            "demo", "片段", "铃声", "ringtone", "慢摇", "变奏", "钢琴版", "吉他版", "八音盒",
            "试听", "试听版", "试听片段", "截取", "audition", "preview", "trial", "sample"
        )

        private val HTML_TAG_REGEX = Regex("<[^>]+>")

        fun cleanText(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.replace(HTML_TAG_REGEX, "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .trim()
        }

        fun normalizeForCompare(str: String): String {
            return cleanText(str)
                .lowercase()
                .replace("（", "(")
                .replace("）", ")")
                .replace("【", "[")
                .replace("】", "]")
                .replace("［", "[")
                .replace("］", "]")
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .replace("·", "")
                .replace("•", "")
                .replace("'", "")
                .replace("\"", "")
                .replace(",", "")
                .replace("，", "")
                .replace(".", "")
                .replace("。", "")
        }

        /**
         * 提取歌曲中的核心歌名（去除括号说明与影视剧副标题）
         */
        fun extractCoreTitle(title: String): String {
            val cleaned = cleanText(title)
                .replace("（", "(")
                .replace("）", ")")
                .replace("【", "(")
                .replace("】", ")")
                .replace("［", "(")
                .replace("］", ")")
                .replace("[", "(")
                .replace("]", ")")
            val withoutBracket = cleaned.replace(Regex("\\([^)]*\\)"), "").trim()
            val base = if (withoutBracket.isNotBlank()) withoutBracket else cleaned
            return base.substringBefore(" - ").substringBefore(" — ").trim()
        }

        /**
         * 提取文本中的特殊版本标签
         */
        private fun extractTags(text: String): Set<String> {
            val lower = text.lowercase()
            return SPECIAL_TAGS.filter { lower.contains(it) }.toSet()
        }

        /**
         * 判断歌曲名称是否一致
         */
        fun isTitleMatched(targetTitle: String, candidateTitle: String): Boolean {
            val normTarget = normalizeForCompare(targetTitle)
            val normCandidate = normalizeForCompare(candidateTitle)
            if (normTarget.isEmpty() || normCandidate.isEmpty()) return false

            // 1. 完全一致
            if (normTarget == normCandidate) return true

            // 2. 特殊版本标签必须一致（如原歌不是 Live 版，候选歌曲带 Live 则不匹配）
            val targetTags = extractTags(targetTitle)
            val candidateTags = extractTags(candidateTitle)
            if (targetTags != candidateTags) {
                return false
            }

            // 3. 核心歌名比对
            val coreTarget = normalizeForCompare(extractCoreTitle(targetTitle))
            val coreCandidate = normalizeForCompare(extractCoreTitle(candidateTitle))
            if (coreTarget.isNotEmpty() && coreTarget == coreCandidate) {
                return true
            }

            return false
        }

        /**
         * 解析歌手列表（支持 / , 、 & 等多歌手分割）
         */
        fun splitArtists(artistStr: String): List<String> {
            val cleaned = cleanText(artistStr)
                .replace("（", "(")
                .replace("）", ")")
            val withoutBracket = cleaned.replace(Regex("\\([^)]*\\)"), "").trim()
            val base = if (withoutBracket.isNotBlank()) withoutBracket else cleaned

            return base.split('/', '\\', ',', '，', '、', '&', '|', '+')
                .map { normalizeForCompare(it) }
                .filter { it.isNotBlank() }
        }

        /**
         * 判断歌手名称是否一致
         */
        fun isArtistMatched(targetArtist: String, candidateArtist: String): Boolean {
            val normTarget = normalizeForCompare(targetArtist)
            val normCandidate = normalizeForCompare(candidateArtist)

            if (normTarget.isEmpty() || normCandidate.isEmpty()) return true
            if (normTarget == normCandidate) return true

            if (normTarget in listOf("未知歌手", "群星", "variousartists", "unknown") ||
                normCandidate in listOf("未知歌手", "群星", "variousartists", "unknown")) {
                return true
            }

            val targetList = splitArtists(targetArtist)
            val candidateList = splitArtists(candidateArtist)

            if (targetList.isEmpty() || candidateList.isEmpty()) {
                return normTarget.contains(normCandidate) || normCandidate.contains(normTarget)
            }

            val hasCommon = targetList.any { t ->
                candidateList.any { c ->
                    t == c || t.contains(c) || c.contains(t)
                }
            }
            if (hasCommon) return true

            return normTarget.contains(normCandidate) || normCandidate.contains(normTarget)
        }

        /**
         * 严格综合匹配判定：
         * 1. 歌名一致
         * 2. 作者一致
         * 3. 时间误差不超过 5 秒 (5000ms)
         */
        fun isMatched(
            targetTitle: String,
            targetArtist: String,
            candidateTitle: String,
            candidateArtist: String,
            targetDurationMs: Long,
            candidateDurationMs: Long
        ): Boolean {
            // 1. 歌名判定
            if (!isTitleMatched(targetTitle, candidateTitle)) {
                return false
            }

            // 2. 作者判定
            if (!isArtistMatched(targetArtist, candidateArtist)) {
                return false
            }

            // 3. 时间误差判定：当两者均有有效时长（>5秒）时，时间误差不得超过 5 秒 (5000ms)
            if (targetDurationMs > 5000L && candidateDurationMs > 5000L) {
                val diffMs = kotlin.math.abs(targetDurationMs - candidateDurationMs)
                if (diffMs > 5000L) {
                    return false
                }
            }

            return true
        }

        /**
         * 计算匹配优选度得分 (用于候选结果排序)
         */
        fun calculateMatchScore(
            targetTitle: String,
            targetArtist: String,
            candidateTitle: String,
            candidateArtist: String,
            targetDurationMs: Long,
            candidateDurationMs: Long
        ): Int {
            var score = 0
            val normTargetTitle = normalizeForCompare(targetTitle)
            val normCandidateTitle = normalizeForCompare(candidateTitle)
            if (normTargetTitle == normCandidateTitle) score += 50 else score += 30

            val normTargetArtist = normalizeForCompare(targetArtist)
            val normCandidateArtist = normalizeForCompare(candidateArtist)
            if (normTargetArtist == normCandidateArtist) score += 30 else score += 15

            if (targetDurationMs > 5000L && candidateDurationMs > 5000L) {
                val diffMs = kotlin.math.abs(targetDurationMs - candidateDurationMs)
                if (diffMs <= 1000L) score += 20
                else if (diffMs <= 3000L) score += 12
                else if (diffMs <= 5000L) score += 6
            } else {
                score += 10
            }
            return score
        }
    }

    private data class MatchedPlatformSong(
        val platform: OnlinePlatform,
        val songId: String,
        val hash: String? = null,
        val matchTitle: String? = null,
        val matchArtist: String? = null,
        val durationMs: Long = 0L,
        val matchScore: Int = 0
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
     * 跨平台在目标平台上搜索并筛选所有满足匹配条件的候选单曲（按匹配得分降序排列）
     */
    private suspend fun searchMatchedSongsOnPlatform(
        targetPlatform: OnlinePlatform,
        title: String,
        artist: String,
        expectedDurationMs: Long = 0L
    ): List<MatchedPlatformSong> = withContext(Dispatchers.IO) {
        val keyword = "$title $artist".trim()
        val matchedList = mutableListOf<MatchedPlatformSong>()
        try {
            when (targetPlatform) {
                OnlinePlatform.NETEASE -> {
                    val postData = FormBody.Builder()
                        .add("s", keyword)
                        .add("type", "1")
                        .add("offset", "0")
                        .add("limit", "10")
                        .add("total", "true")
                        .build()
                    val req = Request.Builder()
                        .url("https://music.163.com/api/search/get/web?csrf_token=")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://music.163.com/")
                        .post(postData)
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string()
                    if (!res.isNullOrBlank()) {
                        val songsArr = JSONObject(res).optJSONObject("result")?.optJSONArray("songs")
                        if (songsArr != null) {
                            for (i in 0 until songsArr.length()) {
                                val obj = songsArr.optJSONObject(i) ?: continue
                                val id = obj.optLong("id").toString()
                                if (id.isBlank() || id == "0") continue
                                val candTitle = SongMatcher.cleanText(obj.optString("name"))
                                val artistsArr = obj.optJSONArray("artists")
                                val candArtist = (0 until (artistsArr?.length() ?: 0))
                                    .mapNotNull { artistsArr?.optJSONObject(it)?.optString("name") }
                                    .joinToString(" / ")
                                val candDur = obj.optLong("duration", 0L)

                                if (SongMatcher.isMatched(title, artist, candTitle, candArtist, expectedDurationMs, candDur)) {
                                    val score = SongMatcher.calculateMatchScore(title, artist, candTitle, candArtist, expectedDurationMs, candDur)
                                    matchedList.add(MatchedPlatformSong(targetPlatform, id, matchTitle = candTitle, matchArtist = candArtist, durationMs = candDur, matchScore = score))
                                }
                            }
                        }
                    }
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
                                put("num_per_page", 10)
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
                    val res = okHttpClient.newCall(req).execute().body?.string()
                    if (!res.isNullOrBlank()) {
                        val list = JSONObject(res).optJSONObject("req")?.optJSONObject("data")?.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
                        if (list != null) {
                            for (i in 0 until list.length()) {
                                val first = list.optJSONObject(i) ?: continue
                                val mid = first.optString("mid").ifEmpty { first.optString("songmid") }
                                if (mid.isBlank()) continue
                                val candTitle = SongMatcher.cleanText(first.optString("name").ifEmpty { first.optString("title") })
                                val singerArr = first.optJSONArray("singer")
                                val candArtist = (0 until (singerArr?.length() ?: 0))
                                    .mapNotNull { singerArr?.optJSONObject(it)?.optString("name") }
                                    .joinToString(" / ")
                                val candDur = first.optLong("interval", 0L) * 1000L

                                if (SongMatcher.isMatched(title, artist, candTitle, candArtist, expectedDurationMs, candDur)) {
                                    val score = SongMatcher.calculateMatchScore(title, artist, candTitle, candArtist, expectedDurationMs, candDur)
                                    matchedList.add(MatchedPlatformSong(targetPlatform, mid, matchTitle = candTitle, matchArtist = candArtist, durationMs = candDur, matchScore = score))
                                }
                            }
                        }
                    }
                }
                OnlinePlatform.KUGOU -> {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val url = "http://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=1&pagesize=10&platform=WebFilter"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string()
                    if (!res.isNullOrBlank()) {
                        val list = JSONObject(res).optJSONObject("data")?.optJSONArray("lists")
                        if (list != null) {
                            for (i in 0 until list.length()) {
                                val first = list.optJSONObject(i) ?: continue
                                val hash = first.optString("FileHash").ifEmpty { first.optString("HQFileHash").ifEmpty { first.optString("SQFileHash") } }
                                val id = first.optString("Audioid").ifEmpty { first.optString("Scid") }
                                if (hash.isBlank() && id.isBlank()) continue
                                val candTitle = SongMatcher.cleanText(first.optString("SongName"))
                                val candArtist = SongMatcher.cleanText(first.optString("SingerName"))
                                val candDur = first.optLong("Duration", 0L) * 1000L

                                if (SongMatcher.isMatched(title, artist, candTitle, candArtist, expectedDurationMs, candDur)) {
                                    val score = SongMatcher.calculateMatchScore(title, artist, candTitle, candArtist, expectedDurationMs, candDur)
                                    matchedList.add(MatchedPlatformSong(targetPlatform, if (id.isNotEmpty()) id else hash, hash = hash, matchTitle = candTitle, matchArtist = candArtist, durationMs = candDur, matchScore = score))
                                }
                            }
                        }
                    }
                }
                OnlinePlatform.KUWO -> {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val url = "http://search.kuwo.cn/r.s?all=$encoded&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val raw = okHttpClient.newCall(req).execute().body?.string()
                    if (!raw.isNullOrBlank()) {
                        val fixed = raw.replace('\'', '"')
                        val list = JSONObject(fixed).optJSONArray("abslist")
                        if (list != null) {
                            for (i in 0 until list.length()) {
                                val first = list.optJSONObject(i) ?: continue
                                val id = first.optString("MUSICRID").replace("MUSIC_", "").ifEmpty { first.optString("id") }
                                if (id.isBlank()) continue
                                val candTitle = SongMatcher.cleanText(first.optString("SONGNAME").ifEmpty { first.optString("name") })
                                val candArtist = SongMatcher.cleanText(first.optString("ARTIST").ifEmpty { first.optString("artist") })
                                val candDur = first.optLong("DURATION", 0L) * 1000L

                                if (SongMatcher.isMatched(title, artist, candTitle, candArtist, expectedDurationMs, candDur)) {
                                    val score = SongMatcher.calculateMatchScore(title, artist, candTitle, candArtist, expectedDurationMs, candDur)
                                    matchedList.add(MatchedPlatformSong(targetPlatform, id, matchTitle = candTitle, matchArtist = candArtist, durationMs = candDur, matchScore = score))
                                }
                            }
                        }
                    }
                }
                OnlinePlatform.MIGU -> {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val switchJson = URLEncoder.encode("{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":0,\"mvSong\":0,\"songlist\":0,\"bestShow\":0}", "UTF-8")
                    val url = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?text=$encoded&pageNo=1&pageSize=10&searchSwitch=$switchJson"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://m.music.migu.cn")
                        .build()
                    val res = okHttpClient.newCall(req).execute().body?.string()
                    if (!res.isNullOrBlank()) {
                        val list = JSONObject(res).optJSONObject("songResultData")?.optJSONArray("result")
                        if (list != null) {
                            for (i in 0 until list.length()) {
                                val first = list.optJSONObject(i) ?: continue
                                val id = first.optString("copyrightId").ifEmpty { first.optString("id") }
                                if (id.isBlank()) continue
                                val candTitle = SongMatcher.cleanText(first.optString("name"))
                                val singersArr = first.optJSONArray("singers")
                                val candArtist = if (singersArr != null) {
                                    (0 until singersArr.length()).mapNotNull { singersArr.optJSONObject(it)?.optString("name") }.joinToString(" / ")
                                } else {
                                    SongMatcher.cleanText(first.optString("singer"))
                                }
                                val lengthStr = first.optString("length", "")
                                val candDur = if (lengthStr.contains(":")) {
                                    val parts = lengthStr.split(":")
                                    val m = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                                    val s = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                                    (m * 60L + s) * 1000L
                                } else {
                                    lengthStr.toLongOrNull()?.let { if (it < 1000) it * 1000L else it } ?: 0L
                                }

                                if (SongMatcher.isMatched(title, artist, candTitle, candArtist, expectedDurationMs, candDur)) {
                                    val score = SongMatcher.calculateMatchScore(title, artist, candTitle, candArtist, expectedDurationMs, candDur)
                                    matchedList.add(MatchedPlatformSong(targetPlatform, id, matchTitle = candTitle, matchArtist = candArtist, durationMs = candDur, matchScore = score))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Search match error on ${targetPlatform.displayName} for $keyword: ${e.message}")
        }
        matchedList.sortedByDescending { it.matchScore }
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
     * 针对音频直链进行快速探活、流长度与实际时间长度完整性校验
     * 严格杜绝试听截断短流 (30s/45s 试听片段)、网页报错流及无效防盗链响应
     */
    private suspend fun probeAndValidateAudioUrl(
        rawUrl: String,
        expectedDurationMs: Long = 0L,
        targetPlatform: OnlinePlatform? = null
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext null
        if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }

        // 1. URL 关键字检测：识别各大平台试听/预览标记
        val lowerUrl = rawUrl.lowercase()
        if (lowerUrl.contains("audition") || lowerUrl.contains("preview") || lowerUrl.contains("trial") ||
            lowerUrl.contains("sample") || lowerUrl.contains("listen_part") || lowerUrl.contains("/short/")
        ) {
            Log.w(TAG, "Audio probe rejected URL: URL contains preview/audition keyword for $rawUrl")
            return@withContext null
        }

        try {
            // 2. 使用 Range 请求探测前 8KB 数据与响应头 (附加各平台合规防盗链 Header)
            val reqBuilder = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-8192")
                .header("Accept", "*/*")

            if (targetPlatform != null) {
                when (targetPlatform) {
                    OnlinePlatform.QQ -> reqBuilder.header("Referer", "https://y.qq.com/")
                    OnlinePlatform.NETEASE -> reqBuilder.header("Referer", "https://music.163.com/")
                    OnlinePlatform.KUGOU -> reqBuilder.header("Referer", "https://www.kugou.com/")
                    OnlinePlatform.KUWO -> reqBuilder.header("Referer", "https://www.kuwo.cn/")
                    OnlinePlatform.MIGU -> reqBuilder.header("Referer", "https://m.music.migu.cn/")
                }
            }

            var finalUrl: String? = null
            var totalContentLength = -1L
            var contentType = ""
            var httpSuccess = false

            try {
                okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val code = resp.code
                    finalUrl = resp.request.url.toString()
                    contentType = resp.header("Content-Type", "")?.lowercase() ?: ""

                    // 若返回网页报错或 JSON 错误文本，判定为无效音频直链
                    if (code >= 400 || contentType.contains("text/html") || contentType.contains("application/json")) {
                        Log.d(TAG, "Audio probe rejected URL: status=$code, contentType=$contentType for $rawUrl")
                        return@withContext null
                    }

                    if (resp.isSuccessful || code == 206) {
                        httpSuccess = true
                        val contentRange = resp.header("Content-Range")
                        if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                            val totalStr = contentRange.substringAfterLast("/").trim()
                            totalContentLength = totalStr.toLongOrNull() ?: -1L
                        }
                        if (totalContentLength <= 0L) {
                            totalContentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                        }

                        // 文件过小 (如小于 2KB 的假响应体)
                        if (code == 200 && totalContentLength in 0..2048) {
                            return@withContext null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Range probe exception for $rawUrl: ${e.message}")
            }

            // 若 Range 请求因 CDN 限制返回非成功，尝试轻量 HEAD 确认连通性
            if (!httpSuccess) {
                try {
                    val headReqBuilder = Request.Builder()
                        .url(rawUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
                        .head()
                    if (targetPlatform != null) {
                        when (targetPlatform) {
                            OnlinePlatform.QQ -> headReqBuilder.header("Referer", "https://y.qq.com/")
                            OnlinePlatform.NETEASE -> headReqBuilder.header("Referer", "https://music.163.com/")
                            OnlinePlatform.KUGOU -> headReqBuilder.header("Referer", "https://www.kugou.com/")
                            OnlinePlatform.KUWO -> headReqBuilder.header("Referer", "https://www.kuwo.cn/")
                            OnlinePlatform.MIGU -> headReqBuilder.header("Referer", "https://m.music.migu.cn/")
                        }
                    }
                    okHttpClient.newCall(headReqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            finalUrl = resp.request.url.toString()
                            contentType = resp.header("Content-Type", "")?.lowercase() ?: ""
                            totalContentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                            httpSuccess = true
                        }
                    }
                } catch (_: Exception) {}
            }

            val validUrl = finalUrl ?: rawUrl

            // 3. 严格流长度比对检验：根据预期时间推算合法最小流字节数 (按 64kbps 极限低码率，每秒至少 8KB)
            if (expectedDurationMs >= 30_000L && totalContentLength > 0L) {
                val minExpectedBytes = (expectedDurationMs / 1000L) * 8_000L
                if (totalContentLength < minExpectedBytes) {
                    Log.w(TAG, "Audio probe rejected URL: stream length too short ($totalContentLength bytes, min required $minExpectedBytes bytes for ${expectedDurationMs}ms) for $validUrl")
                    return@withContext null
                }
            }

            // 4. 启动 MediaMetadataRetriever 探测真实时间长度 (超时设为 2500ms)
            var detectedDurationMs = 0L
            try {
                kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        val headers = mutableMapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile)")
                        if (targetPlatform != null) {
                            when (targetPlatform) {
                                OnlinePlatform.QQ -> headers["Referer"] = "https://y.qq.com/"
                                OnlinePlatform.NETEASE -> headers["Referer"] = "https://music.163.com/"
                                OnlinePlatform.KUGOU -> headers["Referer"] = "https://www.kugou.com/"
                                OnlinePlatform.KUWO -> headers["Referer"] = "https://www.kuwo.cn/"
                                OnlinePlatform.MIGU -> headers["Referer"] = "https://m.music.migu.cn/"
                            }
                        }
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

            // 5. 严格拦截 30秒/45秒 试听短流与严重残缺截断音频
            if (detectedDurationMs > 0L) {
                // 若实际探测时长小于等于 45 秒，但预期时长大于等于 60 秒或时长未知，判定为试听截断短流
                if (detectedDurationMs <= 45_000L && (expectedDurationMs >= 60_000L || expectedDurationMs <= 0L)) {
                    Log.w(TAG, "Audio probe rejected URL: preview snippet detected ($detectedDurationMs ms) for $validUrl")
                    return@withContext null
                }
                // 若实际探测时长小于 60 秒，而预期歌曲大于 90 秒
                if (detectedDurationMs < 60_000L && expectedDurationMs >= 90_000L) {
                    Log.w(TAG, "Audio probe rejected URL: preview duration too short ($detectedDurationMs ms vs expected $expectedDurationMs ms) for $validUrl")
                    return@withContext null
                }
                // 若实际时长短于预期时长的 70%，判定为不完整流
                if (expectedDurationMs >= 45_000L && detectedDurationMs < (expectedDurationMs * 0.70f).toLong()) {
                    Log.w(TAG, "Audio probe rejected URL: incomplete audio duration ($detectedDurationMs ms vs expected $expectedDurationMs ms) for $validUrl")
                    return@withContext null
                }
            } else {
                // 若 MediaMetadataRetriever 超时或未解析出时长，且已知文件流偏小 (如未知时长但流小于 1MB)，杜绝试听音频混入
                if (expectedDurationMs <= 0L && totalContentLength in 1..1_000_000L) {
                    Log.w(TAG, "Audio probe rejected suspicious small stream without duration verification ($totalContentLength bytes) for $validUrl")
                    return@withContext null
                }
            }

            val finalDuration = if (detectedDurationMs > 0L) detectedDurationMs else expectedDurationMs
            Pair(validUrl, finalDuration)
        } catch (e: Exception) {
            Log.d(TAG, "Audio probe exception for $rawUrl: ${e.message}")
            null
        }
    }

    /**
     * 针对单个平台执行音频解析 (脚本引擎解析 + 官方直链兜底)
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
        val candidates = mutableListOf<MatchedPlatformSong>()

        if (isOriginal && songId.isNotBlank()) {
            candidates.add(MatchedPlatformSong(targetPlatform, songId, hash = songId, matchTitle = title, matchArtist = artist, durationMs = expectedDurationMs))
        }

        // 跨平台寻源或原平台初步失败时，搜索匹配符合严格规则的候选集
        if (!isOriginal || candidates.isEmpty()) {
            val searched = searchMatchedSongsOnPlatform(targetPlatform, title, artist, expectedDurationMs)
            candidates.addAll(searched)
        }

        if (candidates.isEmpty()) {
            return@withContext null
        }

        val sourceKey = when (targetPlatform) {
            OnlinePlatform.NETEASE -> "wy"
            OnlinePlatform.QQ -> "tx"
            OnlinePlatform.KUGOU -> "kg"
            OnlinePlatform.KUWO -> "kw"
            OnlinePlatform.MIGU -> "mg"
        }

        // 遍历所有满足匹配规则的候选曲目进行解析
        for (cand in candidates) {
            val effectiveId = cand.songId
            val effectiveHash = cand.hash ?: cand.songId

            // 1. 尝试通过音源脚本引擎解析 (给予充足超时时间：首选 3500ms，备选 2500ms)
            if (engine != null) {
                val musicInfo = JSONObject().apply {
                    put("name", cand.matchTitle ?: title)
                    put("singer", cand.matchArtist ?: artist)
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
                    val timeout = if (q == qualityTryList.first()) 3500L else 2500L
                    val url = engine.resolveMusicUrl(sourceKey, musicInfo, q, timeoutMs = timeout)
                    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                        val probePair = probeAndValidateAudioUrl(url, expectedDurationMs, targetPlatform)
                        if (probePair != null) {
                            val (validUrl, detectedDur) = probePair
                            val finalDur = if (detectedDur > 0L) detectedDur else if (cand.durationMs > 0L) cand.durationMs else expectedDurationMs
                            val res = ResolvedAudioSource(
                                url = validUrl,
                                platform = targetPlatform,
                                sourceName = scriptName,
                                quality = q,
                                durationMs = finalDur,
                                isFallback = !isOriginal,
                                fallbackReason = if (!isOriginal) "原平台音频缺失，已精准匹配切换至 ${targetPlatform.displayName}" else null
                            )
                            Log.i(TAG, "Successfully resolved & verified $title ($q) via ${targetPlatform.displayName} (${if (isOriginal) "当前音源" else "跨平台备用源"}): $validUrl (duration: ${finalDur}ms)")
                            return@withContext res
                        }
                    }
                }
            }

            // 2. 针对网易云官方 outer 兜底直链
            if (targetPlatform == OnlinePlatform.NETEASE) {
                if (effectiveId.isNotEmpty() && effectiveId.matches(Regex("^\\d+$"))) {
                    val outerUrl = "https://music.163.com/song/media/outer/url?id=$effectiveId.mp3"
                    val probePair = probeAndValidateAudioUrl(outerUrl, expectedDurationMs, targetPlatform)
                    if (probePair != null) {
                        val (validUrl, detectedDur) = probePair
                        val finalDur = if (detectedDur > 0L) detectedDur else if (cand.durationMs > 0L) cand.durationMs else expectedDurationMs
                        val res = ResolvedAudioSource(
                            url = validUrl,
                            platform = targetPlatform,
                            sourceName = "网易云官方",
                            quality = "128k",
                            durationMs = finalDur,
                            isFallback = !isOriginal,
                            fallbackReason = if (!isOriginal) "原音源未匹配，自动降级至网易云官方音频" else null
                        )
                        Log.i(TAG, "Fallback to verified Netease outer URL for $title via ${targetPlatform.displayName}: $validUrl")
                        return@withContext res
                    }
                }
            }

            // 3. 针对咪咕音乐官方免费直链兜底
            if (targetPlatform == OnlinePlatform.MIGU) {
                if (effectiveId.isNotEmpty()) {
                    val miguUrl = resolveMiguDirectPlayUrl(effectiveId)
                    if (!miguUrl.isNullOrBlank()) {
                        val probePair = probeAndValidateAudioUrl(miguUrl, expectedDurationMs, targetPlatform)
                        if (probePair != null) {
                            val (validUrl, detectedDur) = probePair
                            val finalDur = if (detectedDur > 0L) detectedDur else if (cand.durationMs > 0L) cand.durationMs else expectedDurationMs
                            val res = ResolvedAudioSource(
                                url = validUrl,
                                platform = targetPlatform,
                                sourceName = "咪咕官方",
                                quality = "HQ",
                                durationMs = finalDur,
                                isFallback = !isOriginal,
                                fallbackReason = if (!isOriginal) "原音源未匹配，自动降级至咪咕官方直链" else null
                            )
                            Log.i(TAG, "Fallback to verified Migu official direct URL for $title: $validUrl")
                            return@withContext res
                        }
                    }
                }
            }
        }

        null
    }

    /**
     * 0.3.0 经典多源轮询模式 (可选项)
     * 特性：
     * 1. 严格保留 0.3.0 版本的平台顺序遍历 (原平台优先 -> 其余平台依次轮询)
     * 2. 在每个平台上，对所有已启用的音源脚本池 (主源 -> 备用源1 -> 备用源2) 依次轮询尝试
     * 3. 严格流长度与时长比对防试听、防截断短流
     * 4. 平台官方兜底直链兜底
     * 5. 无乱序并发抢占，稳定性极佳
     */
    suspend fun resolvePlayableSourceClassicPolling(
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

        // 1. 查内存缓存 (15分钟有效期)
        val cached = memoryUrlCache.get(cacheKey)
        if (cached != null && cached.expireAtMs > now) {
            Log.d(TAG, "[ClassicPolling] Hit memory cache for $title: ${cached.url}")
            return@withContext ResolvedAudioSource(
                url = cached.url,
                platform = platform,
                sourceName = platform.displayName,
                durationMs = expectedDurationMs,
                quality = explicitQuality
            )
        }

        val prefQuality = explicitQuality ?: SourceScriptManager.getInstance(context).preferredQuality.value
        val qualityTryList = when (prefQuality) {
            "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
            "flac" -> listOf("flac", "320k", "128k")
            "320k" -> listOf("320k", "128k")
            else -> listOf("128k", "320k", "flac")
        }

        // 2. 候选平台列表：当前平台优先，其余平台按序轮询
        val candidatePlatforms = listOf(platform) + OnlinePlatform.values().filter { it != platform }

        // 3. 所有可用音源列表：主音源优先，备用音源依次轮询
        val primaryHolder = engineHolders.firstOrNull { it.isPrimary } ?: engineHolders.firstOrNull()
        val orderedEngines = if (primaryHolder != null) {
            listOf(primaryHolder) + engineHolders.filter { it != primaryHolder }
        } else {
            engineHolders.toList()
        }

        var resolvedResult: ResolvedAudioSource? = null

        for (targetPlatform in candidatePlatforms) {
            val isOriginal = (targetPlatform == platform)
            var targetSongId = if (isOriginal) songId else ""
            var targetHash = if (isOriginal) songId else ""

            if (!isOriginal) {
                val match = searchMatchedSongsOnPlatform(targetPlatform, title, artist, expectedDurationMs).firstOrNull()
                if (match != null) {
                    targetSongId = match.songId
                    targetHash = match.hash ?: match.songId
                } else {
                    // 该平台无匹配歌曲，跳到下一平台
                    continue
                }
            }

            val sourceKey = when (targetPlatform) {
                OnlinePlatform.NETEASE -> "wy"
                OnlinePlatform.QQ -> "tx"
                OnlinePlatform.KUGOU -> "kg"
                OnlinePlatform.KUWO -> "kw"
                OnlinePlatform.MIGU -> "mg"
            }

            val effectiveId = targetSongId.ifEmpty { songId }
            val effectiveHash = targetHash.ifEmpty { songId }

            // 4. 在当前平台上依次轮询各个音源脚本
            for (holder in orderedEngines) {
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
                    val url = holder.engine.resolveMusicUrl(sourceKey, musicInfo, q, timeoutMs = 3500L)
                    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                        val probePair = probeAndValidateAudioUrl(url, expectedDurationMs, targetPlatform)
                        if (probePair != null) {
                            val (validUrl, detectedDur) = probePair
                            val finalDur = if (detectedDur > 0L) detectedDur else expectedDurationMs
                            resolvedResult = ResolvedAudioSource(
                                url = validUrl,
                                platform = targetPlatform,
                                sourceName = "${targetPlatform.displayName} (${holder.name})",
                                quality = q,
                                durationMs = finalDur,
                                isFallback = !isOriginal || !holder.isPrimary,
                                fallbackReason = if (!isOriginal) "原平台音频缺失，由「${holder.name}」切换至 ${targetPlatform.displayName}" else null
                            )
                            Log.i(TAG, "[ClassicPolling] Successfully resolved $title ($q) via ${holder.name} on ${targetPlatform.displayName}: $validUrl (duration: ${finalDur}ms)")
                            break
                        }
                    }
                }
                if (resolvedResult != null) break
            }

            // 5. 针对网易云官方 outer 兜底直链
            if (resolvedResult == null && targetPlatform == OnlinePlatform.NETEASE) {
                val neteaseId = targetSongId.ifEmpty { if (isOriginal) songId else "" }
                if (neteaseId.isNotEmpty() && neteaseId.matches(Regex("^\\d+$"))) {
                    val outerUrl = "https://music.163.com/song/media/outer/url?id=$neteaseId.mp3"
                    val probePair = probeAndValidateAudioUrl(outerUrl, expectedDurationMs, targetPlatform)
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
                        Log.i(TAG, "[ClassicPolling] Fallback to verified Netease outer URL for $title: $validUrl")
                    }
                }
            }

            // 6. 针对咪咕音乐官方免费直链兜底
            if (resolvedResult == null && targetPlatform == OnlinePlatform.MIGU) {
                val miguId = targetSongId.ifEmpty { if (isOriginal) songId else "" }
                if (miguId.isNotEmpty()) {
                    val miguUrl = resolveMiguDirectPlayUrl(miguId)
                    if (!miguUrl.isNullOrBlank()) {
                        val probePair = probeAndValidateAudioUrl(miguUrl, expectedDurationMs, targetPlatform)
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
                            Log.i(TAG, "[ClassicPolling] Fallback to verified Migu official direct URL for $title: $validUrl")
                        }
                    }
                }
            }

            // 一旦成功获取并通过流长度与时长检验的有效直链，立即结束遍历
            if (resolvedResult != null) {
                break
            }
        }

        if (resolvedResult != null) {
            val expireAt = now + 15 * 60 * 1000L // 15分钟有效期，防止过期防盗链
            memoryUrlCache.put(cacheKey, CachedUrl(resolvedResult.url, expireAt))
        }

        resolvedResult
    }

    /**
     * 异步解析在线单曲的真实音频直链与音源信息 (支持多寻源策略分发、流长度/时间完整性校验与跨平台多源降级)
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
        val currentStrategy = SourceScriptManager.getInstance(context).resolveStrategy.value
        if (currentStrategy == ResolveStrategy.CLASSIC_POLLING) {
            return@withContext resolvePlayableSourceClassicPolling(
                platform = platform,
                songId = songId,
                title = title,
                artist = artist,
                album = album,
                expectedDurationMs = expectedDurationMs,
                explicitQuality = explicitQuality
            )
        }

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

        // 2. 确定主音源与备用音源列表
        val primaryHolder = engineHolders.firstOrNull { it.isPrimary } ?: engineHolders.firstOrNull()
        val backupHolders = engineHolders.filter { it != primaryHolder }

        val primaryEngine = primaryHolder?.engine
        val primaryName = primaryHolder?.name ?: (getCustomScriptName() ?: "主音源")

        val prefQuality = explicitQuality ?: SourceScriptManager.getInstance(context).preferredQuality.value
        val qualityTryList = when (prefQuality) {
            "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
            "flac" -> listOf("flac", "320k", "128k")
            "320k" -> listOf("320k", "128k")
            else -> listOf("128k", "320k", "flac")
        }

        // 3. 第一阶段：主音源原平台优先通道
        var resolvedResult = resolveSinglePlatformSource(
            targetPlatform = platform,
            songId = songId,
            title = title,
            artist = artist,
            album = album,
            expectedDurationMs = expectedDurationMs,
            isOriginal = true,
            engine = primaryEngine,
            scriptName = primaryName,
            qualityTryList = qualityTryList
        )

        // 4. 第二阶段：主音源跨平台并发搜救寻源 (原平台无源时，对其余平台发起精准匹配与并发解析)
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
                            engine = primaryEngine,
                            scriptName = primaryName,
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

        // 5. 🚀 第三阶段：备用多音源并发竞速寻源 (若主音源全平台均未命中，唤醒全部备用音源池并发搜救)
        if (resolvedResult == null && backupHolders.isNotEmpty()) {
            val allPlatforms = OnlinePlatform.values().toList()
            val totalRequests = backupHolders.size * allPlatforms.size
            val backupChannel = Channel<ResolvedAudioSource?>(totalRequests)

            coroutineScope {
                val backupJobs = backupHolders.flatMap { holder ->
                    allPlatforms.map { targetPlatform ->
                        launch(Dispatchers.IO) {
                            val isOrig = (targetPlatform == platform)
                            val res = resolveSinglePlatformSource(
                                targetPlatform = targetPlatform,
                                songId = songId,
                                title = title,
                                artist = artist,
                                album = album,
                                expectedDurationMs = expectedDurationMs,
                                isOriginal = isOrig,
                                engine = holder.engine,
                                scriptName = holder.name,
                                qualityTryList = qualityTryList
                            )?.let { successSource ->
                                successSource.copy(
                                    sourceName = "${successSource.platform.displayName} (${holder.name})",
                                    isFallback = true,
                                    fallbackReason = "主音源全平台无有效音源，由备用源「${holder.name}」成功解析"
                                )
                            }
                            backupChannel.send(res)
                        }
                    }
                }

                var finishedCount = 0
                while (finishedCount < totalRequests) {
                    val candidate = backupChannel.receive()
                    finishedCount++
                    if (candidate != null) {
                        resolvedResult = candidate
                        backupJobs.forEach { job -> job.cancel() }
                        Log.i(TAG, "Multi-source rescue success for $title via backup script: ${candidate.sourceName}")
                        break
                    }
                }
            }
        }

        val finalResult = resolvedResult
        if (finalResult == null) {
            if (engineHolders.isEmpty()) {
                Log.w(TAG, "No active third-party source scripts loaded for resolving $title across all platforms")
            } else {
                Log.w(TAG, "All active ${engineHolders.size} source engines failed to resolve valid complete audio for $title across all platforms")
            }
        } else {
            // 写入缓存 (有效期 15 分钟，防止防盗链 token 过期导致 403 播放失败)
            val expireAt = now + 15 * 60 * 1000L
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
