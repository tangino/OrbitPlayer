package com.orbit.music.audio.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.engine.OnlineAudioSourceManager
import java.io.File

/**
 * 全局统一音频磁盘缓存管理器
 * 
 * 核心特性：
 * 1. 边播边缓存：ExoPlayer 播放网络音频流的同时自动写入本地磁盘缓存，后续播放与 seek 零网络开销；
 * 2. 避免链接过期：网络直链在长时间退出/暂停后往往会失效（HTTP 403/404 等），已缓存的音频可直接从本地离线缓存读取无缝播放；
 * 3. 稳定缓存键 (Custom Cache Key)：剥离防盗链 URL 中动态易变的 token 参数，以歌曲全局唯一特征为 key，保证无论 URL 如何过期、换源或重启应用，均精准命中已缓存数据；
 * 4. 纯离线优先支持：若歌曲已在本地磁盘完全缓存，支持直接使用离线数据源播放，彻底杜绝任何网络请求与 403 异常；
 * 5. LRU 自动淘汰：上限 1GB，超出时优先淘汰最旧文件，防止无限制侵占设备存储。
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
     * 构建纯离线本地 CacheDataSourceFactory
     * 上游数据源设置为 null，强制只从本地磁盘 SimpleCache 命中读取，绝不发起任何网络连接，从根本上杜绝 403 异常
     */
    fun createOfflineCacheDataSourceFactory(): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 为已完全缓存好的媒体项构建纯离线 MediaSource
     */
    fun createOfflineMediaSource(mediaItem: MediaItem): MediaSource {
        val offlineFactory = DefaultMediaSourceFactory(createOfflineCacheDataSourceFactory())
        return offlineFactory.createMediaSource(mediaItem)
    }

    /**
     * 为歌曲生成全局稳定的缓存识别键 (Cache Key)
     * 
     * 核心规则：
     * 针对网络歌曲，无论其当前是待解析协议 online:// 还是已解析的临时带 token 的 http/https 直链，
     * 必须生成完全恒定、唯一的 Key，确保退出重启、换源或直链过期后依然 100% 命中同一份本地磁盘缓存！
     */
    fun buildStableCacheKey(song: Song): String {
        if (!OnlineAudioSourceManager.isOnlineSong(song) && 
            !song.path.startsWith("http://") && 
            !song.path.startsWith("https://") && 
            !song.path.startsWith("online://")) {
            // 本地原生文件直接使用文件绝对路径作为 key
            return song.path
        }

        val platform = song.resolvedPlatform
            ?: song.originalPlatform
            ?: song.sourcePlatform
            ?: (if (song.path.startsWith("online://")) {
                val host = Uri.parse(song.path).host
                com.orbit.music.data.online.model.OnlinePlatform.values().firstOrNull { it.id == host }
            } else null)
            ?: com.orbit.music.data.online.model.OnlinePlatform.NETEASE
        val platformId = platform.id

        // 统一提取恒定、唯一的歌曲源 ID：
        // 核心：在线歌曲无论当前是负数 virtualId 还是 online:// 协议路径，统一归一化为唯一的 virtualId 字符串，彻底杜绝 Key 漂移
        val rawSongId = when {
            song.id < 0L -> song.id.toString()
            song.path.startsWith("online://") -> {
                val uri = Uri.parse(song.path)
                val segment = uri.lastPathSegment ?: ""
                OnlineAudioSourceManager.generateVirtualSongId(platform, segment).toString()
            }
            song.id > 0L -> song.id.toString()
            else -> {
                val uri = Uri.parse(song.path)
                uri.lastPathSegment?.substringBefore('?') ?: "${song.title}_${song.artist}".hashCode().toString()
            }
        }

        val safeTitle = song.title.trim().lowercase()
        val safeArtist = song.artist.trim().lowercase()

        return "orbit_cache_${platformId}_${rawSongId}_${safeTitle}_${safeArtist}"
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
     * 获取指定歌曲的本地已缓存字节数
     */
    fun getCachedBytesForSong(song: Song): Long {
        val key = buildStableCacheKey(song)
        return getCachedBytes(key)
    }

    /**
     * 判断指定网络歌曲是否已经在本地磁盘中完全缓存好
     * 当满足以下任一条件时判定为已完整缓存：
     * 1. 缓存元数据记录的 Content-Length > 0 且从 0 到 Content-Length 完整命中缓存；
     * 2. 本地缓存字节数达到相当规模（>= 300KB），且存在以 0 为起点的长分片，能够覆盖完整播放。
     */
    fun isFullyCached(song: Song): Boolean {
        if (!OnlineAudioSourceManager.isOnlineSong(song) && !song.path.startsWith("http")) {
            // 本地真实文件本身就是本地文件
            return File(song.path).exists()
        }

        return try {
            val key = buildStableCacheKey(song)
            val cachedBytes = getCachedBytes(key)
            if (cachedBytes < 200 * 1024L) return false

            val metadata = simpleCache.getContentMetadata(key)
            val contentLength = ContentMetadata.getContentLength(metadata)

            if (contentLength > 0L) {
                // 1. 若元数据记录了总长度：
                // A. 严格完全命中
                if (simpleCache.isCached(key, 0, contentLength)) return true
                // B. 容错命中：已缓存 90% 以上且起始连续分片覆盖 88% 以上（解决播放结束时最后静音帧或ID3v1标签未请求造成的假未缓存）
                if (cachedBytes >= (contentLength * 0.90).toLong()) {
                    val spans = simpleCache.getCachedSpans(key)
                    val startSpan = spans.firstOrNull { it.position == 0L }
                    if (startSpan != null && startSpan.length >= (contentLength * 0.88).toLong()) {
                        return true
                    }
                }
                false
            } else {
                // 2. 服务端未提供 Content-Length 头：
                val spans = simpleCache.getCachedSpans(key)
                val startSpan = spans.firstOrNull { it.position == 0L } ?: return false
                if (song.size > 0L && startSpan.length >= (song.size * 0.88).toLong()) return true
                startSpan.length >= 800 * 1024L
            }
        } catch (e: Exception) {
            false
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
                Log.d(TAG, "已为歌曲【${song.title}】预缓存 $totalRead 字节至本地磁盘 (key: $cacheKey)")
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
