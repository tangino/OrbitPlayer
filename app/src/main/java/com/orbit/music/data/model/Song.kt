package com.orbit.music.data.model

import android.net.Uri

enum class SongAttitude {
    NONE,
    FAVORITE,
    DISLIKED
}

/**
 * 音乐曲目数据模型
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val size: Long,
    val albumArtUri: String? = null,
    val folderPath: String = "",
    val year: Int = 0,
    val mimeType: String = "",
    val isFavorite: Boolean = false,
    val isDisliked: Boolean = false,
    val playCount: Int = 0,
    val sourcePlatform: com.orbit.music.data.online.model.OnlinePlatform? = null,
    val sourceTag: String? = null,
    val originalPlatform: com.orbit.music.data.online.model.OnlinePlatform? = null
) {
    val attitude: SongAttitude
        get() = when {
            isFavorite -> SongAttitude.FAVORITE
            isDisliked -> SongAttitude.DISLIKED
            else -> SongAttitude.NONE
        }
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    val isOnlineSong: Boolean
        get() = id < 0 || path.startsWith("online://") || path.startsWith("http://") || path.startsWith("https://")

    val resolvedPlatform: com.orbit.music.data.online.model.OnlinePlatform?
        get() {
            if (sourcePlatform != null) return sourcePlatform
            if (originalPlatform != null) return originalPlatform
            if (path.startsWith("online://")) {
                val platformId = path.removePrefix("online://").substringBefore("/")
                return com.orbit.music.data.online.model.OnlinePlatform.values().firstOrNull { it.id == platformId }
            }
            return null
        }

    val resolvedSourceTag: String?
        get() = sourceTag ?: resolvedPlatform?.displayName
}

/**
 * 文件夹树节点
 */
data class FolderItem(
    val folderPath: String,
    val folderName: String,
    val songCount: Int
)

/**
 * 专辑与艺术家汇总
 */
data class AlbumItem(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val songCount: Int
)

data class ArtistItem(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

/**
 * 歌单类型枚举（智能识别自建歌单构成为纯本地、纯网络还是混合歌单）
 */
enum class PlaylistType(val displayName: String) {
    LOCAL("本地"),
    ONLINE("网络"),
    HYBRID("混合")
}

/**
 * 播放列表模型
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val groupName: String = "默认",
    val coverArtUri: String? = null,
    val type: PlaylistType = PlaylistType.LOCAL,
    val onlineSongCount: Int = 0
) {
    val localSongCount: Int
        get() = (songCount - onlineSongCount).coerceAtLeast(0)

    val isPureOnline: Boolean
        get() = type == PlaylistType.ONLINE

    val isPureLocal: Boolean
        get() = type == PlaylistType.LOCAL

    val isHybrid: Boolean
        get() = type == PlaylistType.HYBRID
}


/**
 * 歌曲播放来源上下文（用于一键精准定位回播放时的原始页面）
 */
sealed interface PlaybackOrigin {
    // ── 本地播放来源 ──
    object AllSongs : PlaybackOrigin
    data class Folder(val folderPath: String) : PlaybackOrigin
    data class Album(val albumItem: AlbumItem) : PlaybackOrigin
    data class Artist(val artistItem: ArtistItem) : PlaybackOrigin
    data class PlaylistOrigin(val playlist: Playlist) : PlaybackOrigin

    // ── 网络播放来源（与本地严格隔离） ──
    data class OnlinePlaylistOrigin(val onlinePlaylist: com.orbit.music.data.online.model.OnlinePlaylist) : PlaybackOrigin
    data class OnlineArtistOrigin(val onlineArtist: com.orbit.music.data.online.model.OnlineArtist, val tabIndex: Int = 0) : PlaybackOrigin
    data class OnlineAlbumOrigin(val onlineAlbum: com.orbit.music.data.online.model.OnlineAlbum) : PlaybackOrigin
    data class OnlineSearchOrigin(val query: String = "") : PlaybackOrigin
    data class OnlineSquareOrigin(val platform: com.orbit.music.data.online.model.OnlinePlatform, val tabIndex: Int = 0) : PlaybackOrigin
    data class OnlineGeneral(val platform: com.orbit.music.data.online.model.OnlinePlatform? = null) : PlaybackOrigin
}

