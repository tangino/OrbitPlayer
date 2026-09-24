package com.orbit.music.data.online.source

import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem

/**
 * 统一在线音源抽象接口（参考落雪音乐 source 适配层）
 */
interface IOnlineMusicSource {
    val platform: OnlinePlatform

    /**
     * 获取歌单分类标签
     */
    suspend fun getTags(): List<OnlinePlaylistTag>

    /**
     * 根据分类标签分页获取热门歌单
     * @param tagId 标签 ID（传入全部/热门标签对应的 ID）
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     */
    suspend fun getPlaylists(tagId: String = "全部", page: Int = 1, pageSize: Int = 30): List<OnlinePlaylist>

    /**
     * 获取官方排行榜列表
     */
    suspend fun getLeaderboards(): List<OnlineLeaderboard>

    /**
     * 获取歌单完整详情及歌曲列表
     * @param playlistId 歌单 ID
     */
    suspend fun getPlaylistDetail(playlistId: String): Pair<OnlinePlaylist, List<OnlineSongItem>>

    /**
     * 搜索歌单
     * @param keyword 搜索关键词
     * @param page 页码
     */
    suspend fun searchPlaylists(keyword: String, page: Int = 1, pageSize: Int = 20): List<OnlinePlaylist>

    /**
     * 从分享链接/字符串中提取解析歌单 ID
     */
    fun extractPlaylistId(urlOrText: String): String?
}
