package com.antigravity.equalizer.data.model

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
    val playCount: Int = 0
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
 * 播放列表模型
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long
)
