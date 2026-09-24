package com.orbit.music.data.online.repository

import android.util.LruCache
import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.source.IOnlineMusicSource
import com.orbit.music.data.online.source.netease.NeteaseMusicSource
import com.orbit.music.data.online.source.qq.QQMusicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 在线公共歌单数据仓库（单例）
 * 负责音源管理、多平台路由分发、内存 LRU 缓存与防抖
 */
class OnlineMusicRepository private constructor() {

    companion object {
        @Volatile
        private var instance: OnlineMusicRepository? = null

        fun getInstance(): OnlineMusicRepository {
            return instance ?: synchronized(this) {
                instance ?: OnlineMusicRepository().also { instance = it }
            }
        }
    }

    private val neteaseSource = NeteaseMusicSource()
    private val qqSource = QQMusicSource()

    // 平台实例映射表
    private val sources = mapOf<OnlinePlatform, IOnlineMusicSource>(
        OnlinePlatform.NETEASE to neteaseSource,
        OnlinePlatform.QQ to qqSource
    )

    // 当前选中的平台
    private val _currentPlatform = MutableStateFlow(OnlinePlatform.NETEASE)
    val currentPlatform: StateFlow<OnlinePlatform> = _currentPlatform.asStateFlow()

    // 内存 LRU 缓存：歌单详情 (key: "platform_playlistId")
    private val detailCache = LruCache<String, Pair<OnlinePlaylist, List<OnlineSongItem>>>(50)
    // 内存缓存：分类标签 (key: platform)
    private val tagsCache = mutableMapOf<OnlinePlatform, List<OnlinePlaylistTag>>()
    // 内存缓存：排行榜 (key: platform)
    private val leaderboardCache = mutableMapOf<OnlinePlatform, List<OnlineLeaderboard>>()

    fun switchPlatform(platform: OnlinePlatform) {
        _currentPlatform.value = platform
    }

    private fun getSource(platform: OnlinePlatform = _currentPlatform.value): IOnlineMusicSource {
        return sources[platform] ?: neteaseSource
    }

    /**
     * 获取指定平台的分类标签
     */
    suspend fun getTags(platform: OnlinePlatform = _currentPlatform.value): Result<List<OnlinePlaylistTag>> {
        return runCatching {
            tagsCache[platform]?.let { return@runCatching it }
            val tags = getSource(platform).getTags()
            tagsCache[platform] = tags
            tags
        }
    }

    /**
     * 获取指定平台的歌单列表
     */
    suspend fun getPlaylists(
        tagId: String = "全部",
        page: Int = 1,
        pageSize: Int = 30,
        platform: OnlinePlatform = _currentPlatform.value
    ): Result<List<OnlinePlaylist>> {
        return runCatching {
            getSource(platform).getPlaylists(tagId, page, pageSize)
        }
    }

    /**
     * 获取官方排行榜
     */
    suspend fun getLeaderboards(platform: OnlinePlatform = _currentPlatform.value): Result<List<OnlineLeaderboard>> {
        return runCatching {
            leaderboardCache[platform]?.let { return@runCatching it }
            val list = getSource(platform).getLeaderboards()
            leaderboardCache[platform] = list
            list
        }
    }

    /**
     * 获取歌单详情（优先读缓存）
     */
    suspend fun getPlaylistDetail(
        playlistId: String,
        platform: OnlinePlatform = _currentPlatform.value,
        forceRefresh: Boolean = false
    ): Result<Pair<OnlinePlaylist, List<OnlineSongItem>>> {
        val cacheKey = "${platform.id}_$playlistId"
        if (!forceRefresh) {
            detailCache.get(cacheKey)?.let {
                return Result.success(it)
            }
        }
        return runCatching {
            val detail = getSource(platform).getPlaylistDetail(playlistId)
            detailCache.put(cacheKey, detail)
            detail
        }
    }

    /**
     * 搜索歌单
     */
    suspend fun searchPlaylists(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        platform: OnlinePlatform = _currentPlatform.value
    ): Result<List<OnlinePlaylist>> {
        return runCatching {
            getSource(platform).searchPlaylists(keyword, page, pageSize)
        }
    }

    /**
     * 尝试从链接或输入文本中自动识别平台并提取歌单 ID
     * @return Pair<OnlinePlatform, PlaylistId> 或者 null
     */
    fun parseLinkOrText(text: String): Pair<OnlinePlatform, String>? {
        val trimmed = text.trim()
        if (trimmed.contains("qq.com") || trimmed.contains("y.qq.com")) {
            qqSource.extractPlaylistId(trimmed)?.let {
                return Pair(OnlinePlatform.QQ, it)
            }
        }
        if (trimmed.contains("163.com") || trimmed.contains("163cn.tv")) {
            neteaseSource.extractPlaylistId(trimmed)?.let {
                return Pair(OnlinePlatform.NETEASE, it)
            }
        }
        // 若没有识别到域名，尝试用当前平台的正则提取数字 ID
        val current = _currentPlatform.value
        getSource(current).extractPlaylistId(trimmed)?.let {
            return Pair(current, it)
        }
        return null
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        detailCache.evictAll()
        tagsCache.clear()
        leaderboardCache.clear()
    }
}
