package com.orbit.music.data.online.model

/**
 * 在线音源类型
 */
enum class OnlinePlatform(val id: String, val displayName: String) {
    NETEASE("netease", "网易云音乐"),
    QQ("qq", "QQ 音乐"),
    KUGOU("kugou", "酷狗音乐"),
    KUWO("kuwo", "酷我音乐"),
    MIGU("migu", "咪咕音乐")
}

/**
 * 歌单分类标签
 */
data class OnlinePlaylistTag(
    val id: String,
    val name: String,
    val category: String = "默认"
)

/**
 * 在线歌单概览信息
 */
data class OnlinePlaylist(
    val id: String,
    val platform: OnlinePlatform,
    val title: String,
    val coverUrl: String,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val creatorName: String? = null,
    val creatorAvatarUrl: String? = null,
    val description: String? = null
)

/**
 * 在线歌单内的歌曲项
 */
data class OnlineSongItem(
    val id: String,
    val platform: OnlinePlatform,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0,
    val coverUrl: String? = null,
    val isVip: Boolean = false
)

/**
 * 官方排行榜信息
 */
data class OnlineLeaderboard(
    val id: String,
    val platform: OnlinePlatform,
    val title: String,
    val coverUrl: String,
    val updateFrequency: String? = null,
    val topSongsPreview: List<String> = emptyList()
)
