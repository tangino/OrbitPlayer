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
import org.json.JSONArray
import org.json.JSONObject

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

    /**
     * 异步解析在线单曲的真实音频直链 (带内存与持久化多级缓存)
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

        // 2. 若存在洛雪源引擎，优先使用洛雪源脚本解析 (支持按用户音质偏好自动降级)
        val engine = lxEngine
        if (engine != null) {
            val sourceKey = when (platform) {
                OnlinePlatform.NETEASE -> "wy"
                OnlinePlatform.QQ -> "tx"
                OnlinePlatform.KUGOU -> "kg"
                OnlinePlatform.KUWO -> "kw"
                OnlinePlatform.MIGU -> "mg"
            }
            val musicInfo = JSONObject().apply {
                put("name", title)
                put("singer", artist)
                put("albumName", album)
                put("songmid", songId)
                put("id", songId)
                put("hash", songId)
                put("copyrightId", songId)
                put("source", sourceKey)
                put("types", JSONArray().apply {
                    put(JSONObject().put("type", "128k"))
                    put(JSONObject().put("type", "320k"))
                    put(JSONObject().put("type", "flac"))
                    put(JSONObject().put("type", "flac24bit"))
                })
            }

            val prefQuality = SourceScriptManager.getInstance(context).preferredQuality.value
            val qualityTryList = when (prefQuality) {
                "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
                "flac" -> listOf("flac", "320k", "128k")
                "320k" -> listOf("320k", "128k")
                else -> listOf("128k", "320k", "flac")
            }

            for (q in qualityTryList) {
                val url = engine.resolveMusicUrl(sourceKey, musicInfo, q, timeoutMs = 4500L)
                if (!url.isNullOrBlank()) {
                    resolvedUrl = url
                    Log.i(TAG, "Resolved url via LX engine for $title ($q): $url")
                    break
                }
            }
        }

        if (resolvedUrl.isNullOrBlank()) {
            if (engine == null) {
                Log.w(TAG, "No active third-party source script loaded for resolving $title on ${platform.displayName}")
            } else {
                Log.w(TAG, "Third-party source script failed to resolve URL for $title on ${platform.displayName}")
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
