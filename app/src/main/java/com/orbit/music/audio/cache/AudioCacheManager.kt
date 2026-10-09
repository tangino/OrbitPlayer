package com.orbit.music.audio.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.engine.OnlineAudioSourceManager
import java.io.File

/**
 * 全局统一音频磁盘缓存管理器
 * 
 * 核心特性：
 * 1. 边播边缓存：ExoPlayer 播放网络音频流的同时自动写入本地磁盘缓存，后续播放与 seek 零网络开销；
 * 2. 避免链接过期：网络直链在长时间暂停后往往会失效（HTTP 403/404 等），已缓存的音频可直接从本地离线分片读取无缝续播；
 * 3. 稳定缓存键 (Custom Cache Key)：剥离防盗链 URL 中动态易变的 token 参数，以歌曲全局唯一特征为 key，保证换源或重新获取 token 后依然精准命中已缓存数据；
 * 4. LRU 自动淘汰：上限 1GB，超出时优先淘汰最旧文件，防止无限制侵占设备存储。
 */
class AudioCacheManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AudioCacheManager"
        private const val MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L // 1GB 磁盘缓存上限

        @Volatile
        private var instance: AudioCacheManager? = null

        fun getInstance(context: Context): AudioCacheManager {
            return instance ?: synchronized(this) {
                instance ?: AudioCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val cacheDir = File(context.cacheDir, "audio_stream_cache")
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)

    val simpleCache: SimpleCache by lazy {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * 基础 HTTP 上游数据源工场 (带合规 User-Agent 与超时配置)
     */
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)
        .setKeepPostFor302Redirects(true)

    /**
     * 构建具备边播边缓存能力的全局 DataSource.Factory
     */
    fun createCacheDataSourceFactory(): DataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(simpleCache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
            // 当缓存写入发生磁盘异常时不阻断播放，平滑降级为纯流式播放
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 为歌曲生成全局稳定的缓存识别键 (Cache Key)
     * 
     * 针对网络歌曲，剥离 URL 中随时间变化的 token/auth 动态参数，
     * 优先采用原平台 + 歌曲 ID 作为永久缓存键。
     */
    fun buildStableCacheKey(song: Song): String {
        return when {
            song.path.startsWith("online://") -> {
                val uri = Uri.parse(song.path)
                val platformId = uri.host ?: "unknown"
                val songId = uri.lastPathSegment ?: song.id.toString()
                "online_${platformId}_${songId}"
            }
            OnlineAudioSourceManager.isOnlineSong(song) || song.path.startsWith("http://") || song.path.startsWith("https://") -> {
                val platformId = song.originalPlatform?.id ?: song.sourcePlatform?.id ?: "web"
                val uri = Uri.parse(song.path)
                val songId = uri.lastPathSegment?.substringBefore('?') ?: song.id.toString()
                "online_${platformId}_${song.title.trim()}_${song.artist.trim()}_$songId"
            }
            else -> {
                // 本地歌曲
                song.path
            }
        }
    }

    /**
     * 检查某个缓存键是否已缓存在本地
     */
    fun getCachedBytes(cacheKey: String): Long {
        return try {
            simpleCache.getCachedBytes(cacheKey, 0, Long.MAX_VALUE)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 清空全部音频流缓存 (可供设置界面清理缓存使用)
     */
    fun clearCache() {
        try {
            val keys = simpleCache.keys
            for (key in keys) {
                simpleCache.removeResource(key)
            }
            Log.i(TAG, "Audio cache cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear audio cache", e)
        }
    }

    /**
     * 将预加载歌曲的首块音频流直接拉取并写入 SimpleCache 磁盘缓存
     * 保证切歌时无缝从本地缓存 0 延时读取
     */
    fun preloadToCache(song: Song, directUrl: String, preloadBytes: Long = 128 * 1024L) {
        if (directUrl.isBlank() || directUrl.startsWith("online://")) return
        try {
            val cacheKey = buildStableCacheKey(song)
            val uri = Uri.parse(directUrl)
            val dataSpec = androidx.media3.datasource.DataSpec.Builder()
                .setUri(uri)
                .setKey(cacheKey)
                .setLength(preloadBytes)
                .build()
            val dataSource = createCacheDataSourceFactory().createDataSource()
            try {
                dataSource.open(dataSpec)
                val buffer = ByteArray(16 * 1024)
                var totalRead = 0L
                while (totalRead < preloadBytes) {
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    totalRead += read
                }
                Log.d(TAG, "已为歌曲【${song.title}】预缓存 $totalRead 字节至本地磁盘")
            } finally {
                runCatching { dataSource.close() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "预缓存至 SimpleCache 失败: ${e.message}")
        }
    }

    /**
     * 获取当前音频流缓存占用的总字节数
     */
    fun getCacheSizeBytes(): Long {
        return try {
            simpleCache.cacheSpace
        } catch (e: Exception) {
            0L
        }
    }
}
